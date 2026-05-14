package cn.gyb.llm.cr.agent.service.impl;

import cn.gyb.llm.cr.agent.agent.CodeReviewAgent;
import cn.gyb.llm.cr.agent.agent.FullScanAgent;
import cn.gyb.llm.cr.agent.common.ReviewIssue;
import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.common.ReviewVerdict;
import cn.gyb.llm.cr.agent.entity.db.ReviewIssueRecord;
import cn.gyb.llm.cr.agent.entity.db.ReviewTask;
import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;
import cn.gyb.llm.cr.agent.mapper.ReviewIssueRecordMapper;
import cn.gyb.llm.cr.agent.mapper.ReviewTaskMapper;
import cn.gyb.llm.cr.agent.service.DiffParserService;
import cn.gyb.llm.cr.agent.service.DingTalkNotifyService;
import cn.gyb.llm.cr.agent.service.GitLabApiService;
import cn.gyb.llm.cr.agent.service.NotionDocumentService;
import cn.gyb.llm.cr.agent.service.ReviewService;
import cn.gyb.llm.cr.agent.service.SkillService;
import cn.gyb.llm.cr.agent.tool.GitLabDiffTool;
import cn.gyb.llm.cr.agent.tool.NotionWriteTool;
import cn.gyb.llm.cr.agent.tool.ProjectFilesTool;
import cn.gyb.llm.cr.agent.tool.SkillQueryTool;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
 */
@Slf4j
@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private SkillService skillService;

    @Autowired
    private GitLabApiService gitLabApiService;

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

    @Value("${gitlab.review.target-branches:main,master}")
    private String targetBranches;

    @Override
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

        // 步骤1: 按目标分支过滤
        List<String> allowedBranches = Arrays.asList(targetBranches.split(","));
        if (!allowedBranches.contains(targetBranch)) {
            log.info("跳过 MR 事件: 目标分支 '{}' not in allowed list {}", targetBranch, allowedBranches);
            return;
        }

        // 步骤2: 按操作类型过滤
        if (!"open".equals(action) && !"update".equals(action) && !"reopen".equals(action)) {
            log.info("跳过 MR 事件: action '{}' 不在允许列表 [open, update, reopen] 中", action);
            return;
        }

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
            // 步骤4: 获取 MR diff
            String diffJson = gitLabApiService.fetchMergeRequestDiff(projectId, mrIid.longValue());
            log.info("获取 MR diff 成功: projectId={}, MR={}, 字符数={}", projectId, mrIid, diffJson.length());

            // 步骤5: 获取规则
            String rules = skillService.getAllActiveRulesAsText();

            // 步骤6: 构建工具回调
            List<ToolCallback> toolCallbacks = buildReviewToolCallbacks(projectId, mrIid.longValue());

            // 步骤7: 创建并运行 CodeReviewAgent
            CodeReviewAgent agent = new CodeReviewAgent(chatModel, toolCallbacks);
            ReviewResult result = agent.review(diffJson, rules);

            log.info("审查完成: verdict={}, issues={}",
                    result.getVerdict(),
                    result.getIssues() != null ? result.getIssues().size() : 0);

            // 步骤9: 处理结果
            processReviewResult(task, result, projectId, mrIid.longValue(),
                    mrTitle, mrUrl, author, sourceBranch, targetBranch, projectName);

        } catch (Exception e) {
            log.error("审查失败: projectId={}, MR={}", projectId, mrIid, e.getMessage(), e);
            task.setStatus("FAILED");
            task.setCompletedAt(LocalDateTime.now());
            task.setSummary("审查失败: " + e.getMessage());
            reviewTaskMapper.updateById(task);

            dingTalkNotifyService.sendReviewFailedNotification(mrTitle, mrUrl, e.getMessage());
        }
    }

    @Override
    public ReviewResult reviewMergeRequest(Long projectId, Long mergeRequestIid) {
        log.info("手动审查触发: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);

        // 获取 MR 信息
        Map<String, Object> mrInfo = gitLabApiService.getMergeRequest(projectId, mergeRequestIid);
        String mrTitle = String.valueOf(mrInfo.getOrDefault("title", "未知"));
        String mrUrl = String.valueOf(mrInfo.getOrDefault("web_url", ""));
        String sourceBranch = String.valueOf(mrInfo.getOrDefault("source_branch", ""));
        String targetBranch = String.valueOf(mrInfo.getOrDefault("target_branch", ""));
        String author = mrInfo.get("author") instanceof Map<?, ?> m
                ? String.valueOf(m.get("name") != null ? m.get("name") : "未知")
                : "未知";
        String projectName = String.valueOf(projectId);

        // 创建审查任务记录
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
            // 获取 MR diff
            String diffJson = gitLabApiService.fetchMergeRequestDiff(projectId, mergeRequestIid);

            // 获取规则
            String rules = skillService.getAllActiveRulesAsText();

            // 构建工具回调并运行代理
            List<ToolCallback> toolCallbacks = buildReviewToolCallbacks(projectId, mergeRequestIid);
            CodeReviewAgent agent = new CodeReviewAgent(chatModel, toolCallbacks);
            ReviewResult result = agent.review(diffJson, rules);

            // 处理结果
            processReviewResult(task, result, projectId, mergeRequestIid,
                    mrTitle, mrUrl, author, sourceBranch, targetBranch, projectName);

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

    @Override
    public ReviewResult triggerFullScan(Long projectId) {
        log.info("全量扫描触发: projectId={}", projectId);

        // 创建审查任务记录
        ReviewTask task = ReviewTask.builder()
                .taskId(UUID.randomUUID().toString())
                .projectId(projectId)
                .triggerType("FULL_SCAN")
                .status("RUNNING")
                .createdAt(LocalDateTime.now())
                .build();
        reviewTaskMapper.insert(task);

        try {
            // 获取项目信息 - 使用 "main" 作为默认分支
            String branch = "main";
            String projectName = String.valueOf(projectId);

            // 获取规则
            String rules = skillService.getAllActiveRulesAsText();

            // 构建工具回调（包含 ProjectFilesTool）
            List<ToolCallback> toolCallbacks = new ArrayList<>();
            toolCallbacks.addAll(Arrays.asList(ToolCallbacks.from(skillQueryTool)));
            toolCallbacks.addAll(Arrays.asList(ToolCallbacks.from(projectFilesTool)));
            toolCallbacks.addAll(Arrays.asList(ToolCallbacks.from(notionWriteTool)));

            // 创建并运行 FullScanAgent
            FullScanAgent agent = new FullScanAgent(chatModel, toolCallbacks);
            ReviewResult result = agent.scan(projectId, branch, rules);

            log.info("全量扫描完成: verdict={}, issues={}",
                    result.getVerdict(),
                    result.getIssues() != null ? result.getIssues().size() : 0);

            // 更新任务
            task.setProjectName(projectName);
            task.setStatus("COMPLETED");
            task.setTotalIssues(result.getIssues() != null ? result.getIssues().size() : 0);
            task.setSummary(result.getSummary());
            task.setCompletedAt(LocalDateTime.now());

            // 写入 Notion 文档
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

            // 保存问题
            if (result.getIssues() != null) {
                saveIssues(task.getId(), result.getIssues());
            }

            // 发送通知
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
     * 处理审查结果: 更新任务、保存问题、审批/评论/通知。
     */
    private void processReviewResult(ReviewTask task, ReviewResult result,
                                     Long projectId, Long mergeRequestIid,
                                     String mrTitle, String mrUrl, String author,
                                     String sourceBranch, String targetBranch,
                                     String projectName) {
        int issueCount = result.getIssues() != null ? result.getIssues().size() : 0;

        task.setTotalIssues(issueCount);
        task.setSummary(result.getSummary());
        task.setCompletedAt(LocalDateTime.now());

        if (result.getVerdict() == ReviewVerdict.PASSED) {
            // 通过: 审批 MR 并通知
            task.setStatus("COMPLETED");
            reviewTaskMapper.updateById(task);

            try {
                gitLabApiService.approveMergeRequest(projectId, mergeRequestIid);
                log.info("已审批 MR: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);
            } catch (Exception e) {
                log.warn("审批 MR 失败: projectId={}, mergeRequestIid={}",
                        projectId, mergeRequestIid, e.getMessage());
            }

            dingTalkNotifyService.sendReviewPassedNotification(
                    mrTitle, mrUrl, author, sourceBranch, targetBranch, result.getSummary());

        } else if (result.getVerdict() == ReviewVerdict.ISSUES_FOUND) {
            task.setStatus("COMPLETED");
            reviewTaskMapper.updateById(task);

            // a. 保存问题到 cr_review_issue 表
            if (result.getIssues() != null) {
                saveIssues(task.getId(), result.getIssues());
            }

            // b. 在 MR 上发布审查评论
            String comment = formatReviewComment(result);
            try {
                gitLabApiService.createMergeRequestNote(projectId, mergeRequestIid, comment);
                log.info("已在 MR 上发布审查评论: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);
            } catch (Exception e) {
                log.warn("发布审查评论失败: {}", e.getMessage());
            }

            // c. 尝试创建修复 MR
            String fixMrUrl = null;
            try {
                fixMrUrl = createFixMr(projectId, mergeRequestIid, sourceBranch, targetBranch, mrTitle, result);
            } catch (Exception e) {
                log.warn("创建修复 MR 失败: {}", e.getMessage());
            }

            // d. 写入 Notion 文档
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

            // e. 发送钉钉通知
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
        callbacks.addAll(Arrays.asList(ToolCallbacks.from(gitLabDiffTool)));
        callbacks.addAll(Arrays.asList(ToolCallbacks.from(notionWriteTool)));
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

        // 收集有修复代码的问题
        List<ReviewIssue> fixableIssues = result.getIssues().stream()
                .filter(issue -> issue.getFixCode() != null && !issue.getFixCode().isBlank())
                .toList();

        if (fixableIssues.isEmpty()) {
            log.info("没有可修复的问题，跳过修复 MR 创建");
            return null;
        }

        // 创建修复分支
        String fixBranch = "fix/cr-auto-fix-" + mergeRequestIid + "-" + System.currentTimeMillis();
        gitLabApiService.createBranch(projectId, fixBranch, sourceBranch);

        // 构建提交操作
        List<Map<String, String>> actions = new ArrayList<>();
        for (ReviewIssue issue : fixableIssues) {
            Map<String, String> action = Map.of(
                    "action", "update",
                    "filePath", issue.getFile() != null ? issue.getFile() : "",
                    "content", issue.getFixCode()
            );
            actions.add(action);
        }

        // 创建包含修复的提交
        String commitMessage = "fix: 自动修复代码审查问题 MR !" + mergeRequestIid;
        gitLabApiService.createCommit(projectId, fixBranch, commitMessage, actions);

        // 创建修复 MR
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
