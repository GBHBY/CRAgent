package cn.gyb.llm.cr.agent.comment;

import cn.gyb.llm.cr.agent.common.ReviewIssue;
import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.common.ReviewVerdict;
import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;
import cn.gyb.llm.cr.agent.service.GitLabApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * GitLab 审查评论回写策略实现
 * <p>
 * 将 AI 代码审查结论以 Note（备注）的形式回写到 GitLab Merge Request。
 * 若审查通过，则额外调用 approve 接口审批 MR。
 */
@Slf4j
@Component
public class GitLabReviewCommentHandler implements ReviewCommentHandler {

    @Autowired
    private GitLabApiService gitLabApiService;

    @Override
    public String platformType() {
        return "gitlab";
    }

    @Override
    public void postReviewComment(MergeRequestEvent event, ReviewResult result) {
        Long projectId = event.getProject() != null ? event.getProject().getId() : null;
        Integer mrIid = event.getObjectAttributes() != null ? event.getObjectAttributes().getIid() : null;

        if (projectId == null || mrIid == null) {
            log.warn("GitLab 评论回写失败：缺少 projectId 或 mrIid");
            return;
        }

        Long mergeRequestIid = mrIid.longValue();

        // 审查通过 → approve + 发评论
        if (result.getVerdict() == ReviewVerdict.PASSED) {
            try {
                gitLabApiService.approveMergeRequest(projectId, mergeRequestIid);
                log.info("已审批 GitLab MR: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);
            } catch (Exception e) {
                log.warn("审批 GitLab MR 失败: {}", e.getMessage());
            }
        }

        // 无论通过与否均发布评论
        String comment = formatComment(result);
        try {
            gitLabApiService.createMergeRequestNote(projectId, mergeRequestIid, comment);
            log.info("已在 GitLab MR 发布审查评论: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);
        } catch (Exception e) {
            log.warn("发布 GitLab MR 评论失败: {}", e.getMessage());
        }
    }

    /**
     * 将审查结果格式化为 Markdown 评论
     */
    private String formatComment(ReviewResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("## \uD83E\uDD16 AI 代码审查结果\n\n");

        if (result.getVerdict() == ReviewVerdict.PASSED) {
            sb.append("**结论:** ✅ 通过\n\n");
        } else {
            sb.append("**结论:** ❌ 发现问题\n\n");
        }

        if (result.getSummary() != null) {
            sb.append("**摘要:** ").append(result.getSummary()).append("\n\n");
        }

        if (result.getIssues() != null && !result.getIssues().isEmpty()) {
            sb.append("### 问题列表 (").append(result.getIssues().size()).append(")\n\n");

            for (int i = 0; i < result.getIssues().size(); i++) {
                ReviewIssue issue = result.getIssues().get(i);
                sb.append("**").append(i + 1).append(". [").append(issue.getSeverity()).append("] ")
                        .append(issue.getFile() != null ? issue.getFile() : "未知文件");

                if (issue.getLine() != null) {
                    sb.append(":L").append(issue.getLine());
                }
                sb.append("**\n");
                sb.append("- **问题:** ").append(issue.getMessage()).append("\n");

                if (issue.getSuggestion() != null) {
                    sb.append("- **建议:** ").append(issue.getSuggestion()).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("---\n*此审查由 AI 代码审查代理自动生成。*");
        return sb.toString();
    }
}
