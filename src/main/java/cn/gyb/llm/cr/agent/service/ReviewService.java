package cn.gyb.llm.cr.agent.service;

import cn.gyb.llm.cr.agent.agent.CodeReviewAgent;
import cn.gyb.llm.cr.agent.agent.FullScanAgent;
import cn.gyb.llm.cr.agent.comment.ReviewCommentHandler;
import cn.gyb.llm.cr.agent.comment.ReviewCommentHandlerRegistry;
import cn.gyb.llm.cr.agent.common.ReviewIssue;
import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.common.ReviewVerdict;
import cn.gyb.llm.cr.agent.entity.db.ReviewIssueRecord;
import cn.gyb.llm.cr.agent.entity.db.ReviewTask;
import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;
import cn.gyb.llm.cr.agent.mapper.ReviewIssueRecordMapper;
import cn.gyb.llm.cr.agent.mapper.ReviewTaskMapper;
import cn.gyb.llm.cr.agent.tool.GitLabDiffTool;
import cn.gyb.llm.cr.agent.tool.NotionWriteTool;
import cn.gyb.llm.cr.agent.tool.ProjectFilesTool;
import cn.gyb.llm.cr.agent.tool.SkillQueryTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 代码审查工作流的核心编排器。
 * 协调 AI 代理执行、问题持久化、GitLab 交互、
 * Notion 文档创建和钉钉通知。
 *
 * @author guoyb
 * @date 2026-05-17
 */
@Slf4j
@Service
public class ReviewService {

    @Autowired
    private ZhiPuAiChatModel chatModel;


    @Autowired
    private GitLabApiService gitLabApiService;

    @Autowired
    private GitHubApiService gitHubApiService;

    @Autowired
    private ReviewCommentHandlerRegistry reviewCommentHandlerRegistry;

    @Autowired
    private DiffParserService diffParserService;

    @Autowired
    private DingTalkNotifyService dingTalkNotifyService;

    @Autowired
    private NotionDocumentService notionDocumentService;

    @Autowired
    private ReviewTaskMapper reviewTaskMapper;

    @Autowired
    private ReviewIssueRecordMapper reviewIssueRecordMapper;

    @Autowired
    private SkillQueryTool skillQueryTool;

    @Autowired
    private GitLabDiffTool gitLabDiffTool;

    @Autowired
    private ProjectFilesTool projectFilesTool;

    @Autowired
    private NotionWriteTool notionWriteTool;

    /**
     * 当前激活的平台，决定走 GitLab 还是 GitHub 流程
     */
    @Value("${webhook.platform:gitlab}")
    private String platform;

    @Value("${gitlab.review.target-branches:main,master}")
    private String gitlabTargetBranches;

    @Value("${github.review.target-branches:main,master}")
    private String githubTargetBranches;

    /**
     * 处理 GitLab Webhook 触发的合并请求事件
     *
     * @param event 合并请求事件
     */
    public void handleMergeRequestEvent(MergeRequestEvent event) {
        MergeRequestEvent.MergeRequestAttributes attrs = event.getObjectAttributes();
        MergeRequestEvent.Project project = event.getProject();

        Long projectId = project != null ? project.getId() : null;
        Integer mrIid = attrs != null ? attrs.getIid() : null;
        String mrTitle = attrs != null ? attrs.getTitle() : "未知";
        String mrUrl = attrs != null ? attrs.getUrl() : "";
        String sourceBranch = attrs != null ? attrs.getSourceBranch() : "";
        String targetBranch = attrs != null ? attrs.getTargetBranch() : "";
        String author = event.getUser() != null ? event.getUser().getName() : "未知";
        String projectName = project != null ? project.getName() : "未知";
        String action = attrs != null ? attrs.getAction() : "";

        log.info("处理 MR 事件: projectId={}, mrIid={}, action={}, title={}",
                projectId, mrIid, action, mrTitle);

        // 步骤3: 创建审查任务记录
        ReviewTask task = ReviewTask.builder()
                .taskId(UUID.randomUUID().toString())
                .projectId(projectId)
                .projectName(projectName)
                .mergeRequestIid(mrIid)
                .sourceBranch(sourceBranch)
                .targetBranch(targetBranch)
                .triggerType("WEBHOOK")
                .status("RUNNING")
                .createdAt(LocalDateTime.now())
                .build();
        reviewTaskMapper.insert(task);
        log.info("创建审查任务: taskId={}, projectId={}, mrIid={}", task.getTaskId(), projectId, mrIid);

        try {
            // 步骤4: 统一从 diffFilePath 读取 diff 文件内容
            // Handler 层负责拉取 diff 并落盘，Service 层只负责读文件
            String diffFilePath = event.getDiffFilePath();
            if (diffFilePath == null || diffFilePath.isBlank()) {
                throw new RuntimeException("diff 文件路径为空，Handler 层可能未成功保存 diff 文件");
            }
            String diffContent;
            try {
                diffContent = Files.readString(Paths.get(diffFilePath), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("读取 diff 文件失败: " + diffFilePath, e);
            }
            log.info("读取 diff 文件成功: path={}, 字符数={}", diffFilePath, diffContent.length());

            // 步骤5: 构建工具回调
            List<ToolCallback> toolCallbacks = buildReviewToolCallbacks(projectId, mrIid.longValue());

            // 步骤6: 创建并运行 CodeReviewAgent（Skill 由 Agent 通过工具自主选择）
            CodeReviewAgent agent = new CodeReviewAgent(chatModel, toolCallbacks);
            ReviewResult result = agent.review(diffContent);

            log.info("审查完成: result={}", result);

            // 步骤7: 通过策略模式按平台回写评论
            String eventPlatform = event.getPlatform();
            if (eventPlatform != null) {
                ReviewCommentHandler commentHandler = reviewCommentHandlerRegistry.getHandler(eventPlatform);
                if (commentHandler != null) {
                    try {
                        commentHandler.postReviewComment(event, result);
                        log.info("评论回写完成: platform={}, projectId={}, mrIid={}", eventPlatform, projectId, mrIid);
                    } catch (Exception e) {
                        log.warn("评论回写失败: platform={}, error={}", eventPlatform, e.getMessage());
                    }
                }
            } else {
                log.warn("事件中未设置 platform 字段，跳过评论回写");
            }

        } catch (Exception e) {
            log.error("审查失败: projectId={}, MR={}", projectId, mrIid, e.getMessage(), e);
            task.setStatus("FAILED");
            task.setCompletedAt(LocalDateTime.now());
            task.setSummary("审查失败: " + e.getMessage());
            reviewTaskMapper.updateById(task);

            dingTalkNotifyService.sendReviewFailedNotification(mrTitle, mrUrl, e.getMessage());
        }
    }

    /**
     * 手动触发指定合并请求的代码审查
     *
     * @param projectId       GitLab 项目ID
     * @param mergeRequestIid 合并请求 IID
     * @return 审查结果
     */
    public ReviewResult reviewMergeRequest(Long projectId, Long mergeRequestIid) {
        log.info("手动审查触发: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);

        Map<String, Object> mrInfo = gitLabApiService.getMergeRequest(projectId, mergeRequestIid);
        String mrTitle = String.valueOf(mrInfo.getOrDefault("title", "未知"));
        String mrUrl = String.valueOf(mrInfo.getOrDefault("web_url", ""));
        String sourceBranch = String.valueOf(mrInfo.getOrDefault("source_branch", ""));
        String targetBranch = String.valueOf(mrInfo.getOrDefault("target_branch", ""));
        String projectName = String.valueOf(projectId);

        ReviewTask task = ReviewTask.builder()
                .taskId(UUID.randomUUID().toString())
                .projectId(projectId)
                .projectName(projectName)
                .mergeRequestIid(mergeRequestIid.intValue())
                .sourceBranch(sourceBranch)
                .targetBranch(targetBranch)
                .triggerType("MANUAL")
                .status("RUNNING")
                .createdAt(LocalDateTime.now())
                .build();
        reviewTaskMapper.insert(task);

        try {
            String diffJson = gitLabApiService.fetchMergeRequestDiff(projectId, mergeRequestIid);

            // 构建工具回调并运行代理（Skill 由 Agent 通过工具自主选择）
            List<ToolCallback> toolCallbacks = buildReviewToolCallbacks(projectId, mergeRequestIid);
            CodeReviewAgent agent = new CodeReviewAgent(chatModel, toolCallbacks);
            ReviewResult result = agent.review(diffJson);

            //processReviewResult(task, result, projectId, mergeRequestIid,
            //        mrTitle, mrUrl, author, sourceBranch, targetBranch, projectName,
            //        false, null, null);

            return result;
        } catch (Exception e) {
            log.error("手动审查失败: projectId={}, mergeRequestIid={}",
                    projectId, mergeRequestIid, e.getMessage(), e);
            task.setStatus("FAILED");
            task.setCompletedAt(LocalDateTime.now());
            task.setSummary("审查失败: " + e.getMessage());
            reviewTaskMapper.updateById(task);

            dingTalkNotifyService.sendReviewFailedNotification(mrTitle, mrUrl, e.getMessage());
            throw new RuntimeException("手动审查失败: " + e.getMessage(), e);
        }
    }

    /**
     * 触发项目全量代码扫描
     *
     * @param projectId GitLab 项目ID
     * @return 扫描结果
     */
    public ReviewResult triggerFullScan(Long projectId) {
        log.info("全量扫描触发: projectId={}", projectId);

        ReviewTask task = ReviewTask.builder()
                .taskId(UUID.randomUUID().toString())
                .projectId(projectId)
                .triggerType("FULL_SCAN")
                .status("RUNNING")
                .createdAt(LocalDateTime.now())
                .build();
        reviewTaskMapper.insert(task);

        try {
            String branch = "main";
            String projectName = String.valueOf(projectId);

            // 构建工具回调（包含 ProjectFilesTool，Skill 由 Agent 通过工具自主选择）
            List<ToolCallback> toolCallbacks = new ArrayList<>();
            toolCallbacks.addAll(Arrays.asList(ToolCallbacks.from(skillQueryTool)));
            toolCallbacks.addAll(Arrays.asList(ToolCallbacks.from(projectFilesTool)));
            toolCallbacks.addAll(Arrays.asList(ToolCallbacks.from(notionWriteTool)));

            FullScanAgent agent = new FullScanAgent(chatModel, toolCallbacks);
            ReviewResult result = agent.scan(projectId, branch);

            log.info("全量扫描完成: verdict={}, issues={}",
                    result.getVerdict(),
                    result.getIssues() != null ? result.getIssues().size() : 0);

            task.setProjectName(projectName);
            task.setStatus("COMPLETED");
            task.setTotalIssues(result.getIssues() != null ? result.getIssues().size() : 0);
            task.setSummary(result.getSummary());
            task.setCompletedAt(LocalDateTime.now());

            String notionPageUrl = "";
            try {
                notionPageUrl = notionDocumentService.createReviewDocument(
                        projectId, projectName,
                        "全量扫描报告", "",
                        result, branch, branch);
                task.setNotionPageUrl(notionPageUrl);
            } catch (Exception e) {
                log.warn("写入 Notion 文档失败（全量扫描）: {}", e.getMessage());
            }

            reviewTaskMapper.updateById(task);

            if (result.getIssues() != null) {
                saveIssues(task.getId(), result.getIssues());
            }

            dingTalkNotifyService.sendFullScanNotification(projectName, result, notionPageUrl);

            return result;
        } catch (Exception e) {
            log.error("全量扫描失败: projectId={}", projectId, e.getMessage(), e);
            task.setStatus("FAILED");
            task.setCompletedAt(LocalDateTime.now());
            task.setSummary("全量扫描失败: " + e.getMessage());
            reviewTaskMapper.updateById(task);
            throw new RuntimeException("全量扫描失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理审查结果：更新任务、保存问题、回写评论（按平台分发）、写入 Notion、钉钉通知。
     *
     * @param isGitHub          是否为 GitHub 平台
     * @param pathWithNamespace 仓库全名（owner/repo 格式），GitHub 评论回写时使用
     * @param pullNumber        PR 编号，GitHub 评论回写时使用
     */
    private void processReviewResult(ReviewTask task, ReviewResult result,
                                     Long projectId, Long mergeRequestIid,
                                     String mrTitle, String mrUrl, String author,
                                     String sourceBranch, String targetBranch,
                                     String projectName,
                                     boolean isGitHub, String pathWithNamespace,
                                     Integer pullNumber) {
        int issueCount = result.getIssues() != null ? result.getIssues().size() : 0;

        task.setTotalIssues(issueCount);
        task.setSummary(result.getSummary());
        task.setCompletedAt(LocalDateTime.now());

        if (result.getVerdict() == ReviewVerdict.PASSED) {
            task.setStatus("COMPLETED");
            reviewTaskMapper.updateById(task);

            if (!isGitHub) {
                try {
                    gitLabApiService.approveMergeRequest(projectId, mergeRequestIid);
                    log.info("已审批 GitLab MR: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);
                } catch (Exception e) {
                    log.warn("审批 GitLab MR 失败: {}", e.getMessage());
                }
            }

            dingTalkNotifyService.sendReviewPassedNotification(
                    mrTitle, mrUrl, author, sourceBranch, targetBranch, result.getSummary());

        } else if (result.getVerdict() == ReviewVerdict.ISSUES_FOUND) {
            task.setStatus("COMPLETED");
            reviewTaskMapper.updateById(task);

            if (result.getIssues() != null) {
                saveIssues(task.getId(), result.getIssues());
            }

            String comment = formatReviewComment(result);
            if (isGitHub) {
                try {
                    String[] parts = pathWithNamespace != null ? pathWithNamespace.split("/", 2) : new String[]{"", ""};
                    gitHubApiService.createPullRequestComment(parts[0], parts[1], pullNumber, comment);
                    log.info("已在 GitHub PR 发布审查评论: {} #{}", pathWithNamespace, pullNumber);
                } catch (Exception e) {
                    log.warn("发布 GitHub PR 评论失败: {}", e.getMessage());
                }
            } else {
                try {
                    gitLabApiService.createMergeRequestNote(projectId, mergeRequestIid, comment);
                    log.info("已在 GitLab MR 发布审查评论: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);
                } catch (Exception e) {
                    log.warn("发布 GitLab MR 评论失败: {}", e.getMessage());
                }
            }

            String fixMrUrl = null;
            if (!isGitHub) {
                try {
                    fixMrUrl = createFixMr(projectId, mergeRequestIid, sourceBranch, targetBranch, mrTitle, result);
                } catch (Exception e) {
                    log.warn("创建修复 MR 失败: {}", e.getMessage());
                }
            }

            String notionPageUrl = null;
            try {
                notionPageUrl = notionDocumentService.createReviewDocument(
                        projectId, projectName, mrTitle, mrUrl,
                        result, sourceBranch, targetBranch);
                task.setNotionPageUrl(notionPageUrl);
                reviewTaskMapper.updateById(task);
            } catch (Exception e) {
                log.warn("写入 Notion 文档失败: {}", e.getMessage());
            }

            dingTalkNotifyService.sendIssuesFoundNotification(
                    mrTitle, mrUrl, author, sourceBranch, targetBranch,
                    result, fixMrUrl, notionPageUrl);
        }
    }

    /**
     * 为代码审查代理构建工具回调。
     */
    private List<ToolCallback> buildReviewToolCallbacks(Long projectId, Long mrIid) {
        List<ToolCallback> callbacks = new ArrayList<>();
        callbacks.addAll(Arrays.asList(ToolCallbacks.from(skillQueryTool)));
        //callbacks.addAll(Arrays.asList(ToolCallbacks.from(gitLabDiffTool)));
        //callbacks.addAll(Arrays.asList(ToolCallbacks.from(notionWriteTool)));
        return callbacks;
    }

    /**
     * 将审查结果格式化为 GitLab MR 的 Markdown 评论。
     */
    private String formatReviewComment(ReviewResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("## AI 代码审查结果\n\n");

        if (result.getVerdict() != null) {
            if (result.getVerdict() == ReviewVerdict.PASSED) {
                sb.append("**结论:** 通过 :white_check_mark:\n\n");
            } else {
                sb.append("**结论:** 发现问题 :x:\n\n");
            }
        }

        if (result.getSummary() != null) {
            sb.append("**Summary:** ").append(result.getSummary()).append("\n\n");
        }

        if (result.getIssues() != null && !result.getIssues().isEmpty()) {
            sb.append("### Issues (").append(result.getIssues().size()).append(")\n\n");

            for (int i = 0; i < result.getIssues().size(); i++) {
                ReviewIssue issue = result.getIssues().get(i);
                sb.append("**").append(i + 1).append(". [").append(issue.getSeverity()).append("] ")
                        .append(issue.getFile());

                if (issue.getLine() != null) {
                    sb.append(":L").append(issue.getLine());
                }

                sb.append("**\n\n");
                sb.append("- **Message:** ").append(issue.getMessage()).append("\n");

                if (issue.getSuggestion() != null) {
                    sb.append("- **Suggestion:** ").append(issue.getSuggestion()).append("\n");
                }

                if (issue.getRuleId() != null) {
                    sb.append("- **Rule:** ").append(issue.getRuleId()).append("\n");
                }

                sb.append("\n");
            }
        }

        sb.append("---\n*此审查由 AI 代码审查代理生成。*");
        return sb.toString();
    }

    /**
     * 创建包含自动修复代码变更的修复合并请求。
     */
    private String createFixMr(Long projectId, Long mergeRequestIid,
                               String sourceBranch, String targetBranch,
                               String mrTitle, ReviewResult result) {
        if (result.getIssues() == null || result.getIssues().isEmpty()) {
            return null;
        }

        List<ReviewIssue> fixableIssues = result.getIssues().stream()
                .filter(issue -> issue.getFixCode() != null && !issue.getFixCode().isBlank())
                .toList();

        if (fixableIssues.isEmpty()) {
            log.info("没有可修复的问题，跳过修复 MR 创建");
            return null;
        }

        String fixBranch = "fix/cr-auto-fix-" + mergeRequestIid + "-" + System.currentTimeMillis();
        gitLabApiService.createBranch(projectId, fixBranch, sourceBranch);

        List<Map<String, String>> actions = new ArrayList<>();
        for (ReviewIssue issue : fixableIssues) {
            Map<String, String> action = Map.of(
                    "action", "update",
                    "filePath", issue.getFile() != null ? issue.getFile() : "",
                    "content", issue.getFixCode()
            );
            actions.add(action);
        }

        String commitMessage = "fix: 自动修复代码审查问题 MR !" + mergeRequestIid;
        gitLabApiService.createCommit(projectId, fixBranch, commitMessage, actions);

        String fixMrTitle = "fix: 自动修复 - " + mrTitle;
        String fixMrDescription = "此 MR 包含对 !" + mergeRequestIid + " 代码审查问题的自动生成修复。";
        Map<String, Object> fixMr = gitLabApiService.createMergeRequest(
                projectId, fixBranch, sourceBranch, fixMrTitle, fixMrDescription);

        String fixMrUrl = String.valueOf(fixMr.getOrDefault("web_url", ""));
        log.info("已创建修复 MR: {}", fixMrUrl);
        return fixMrUrl;
    }

    /**
     * 保存审查问题到数据库。
     */
    private void saveIssues(Long reviewTaskId, List<ReviewIssue> issues) {
        for (ReviewIssue issue : issues) {
            ReviewIssueRecord record = ReviewIssueRecord.builder()
                    .reviewTaskId(reviewTaskId)
                    .filePath(issue.getFile())
                    .lineNumber(issue.getLine())
                    .severity(issue.getSeverity())
                    .ruleId(issue.getRuleId())
                    .message(issue.getMessage())
                    .suggestion(issue.getSuggestion())
                    .fixCode(issue.getFixCode())
                    .createdAt(LocalDateTime.now())
                    .build();
            reviewIssueRecordMapper.insert(record);
        }
        log.info("已保存 {} 个问题，审查任务: {}", issues.size(), reviewTaskId);
    }
}
