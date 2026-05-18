package cn.gyb.llm.cr.agent.comment;

import cn.gyb.llm.cr.agent.common.ReviewIssue;
import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.common.ReviewVerdict;
import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub 审查评论回写策略实现
 * <p>
 * 使用 GitHub Pull Request Review API 将 AI 代码审查结论回写到 PR。
 * 接口：POST /repos/{owner}/{repo}/pulls/{pull_number}/reviews
 * <p>
 * 审查通过时 event=APPROVE，发现问题时 event=REQUEST_CHANGES，
 * 同时可以附带行级别的 inline comments。
 */
@Slf4j
@Component
public class GitHubReviewCommentHandler implements ReviewCommentHandler {

    private static final String GITHUB_API_VERSION = "2022-11-28";

    @Value("${github.api.url:https://api.github.com}")
    private String githubApiUrl;

    @Value("${github.api.token:}")
    private String token;

    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String platformType() {
        return "github";
    }

    @Override
    public void postReviewComment(MergeRequestEvent event, ReviewResult result) {
        String pathWithNamespace = event.getProject() != null ? event.getProject().getPathWithNamespace() : null;
        Integer pullNumber = event.getObjectAttributes() != null ? event.getObjectAttributes().getIid() : null;

        if (pathWithNamespace == null || !pathWithNamespace.contains("/") || pullNumber == null) {
            log.warn("GitHub 评论回写失败：缺少仓库信息或 PR 编号, pathWithNamespace={}, pullNumber={}",
                    pathWithNamespace, pullNumber);
            return;
        }

        String[] parts = pathWithNamespace.split("/", 2);
        String owner = parts[0];
        String repo = parts[1];

        try {
            // 构建 Review 请求体
            Map<String, Object> requestBody = buildReviewRequest(result);

            String url = String.format("%s/repos/%s/%s/pulls/%d/reviews", githubApiUrl, owner, repo, pullNumber);
            HttpEntity<String> entity = new HttpEntity<>(JSONObject.toJSONString(requestBody), buildHeaders());

            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("已在 GitHub PR 发布 Review: owner={}, repo={}, pullNumber={}, event={}",
                    owner, repo, pullNumber, requestBody.get("event"));
        } catch (Exception e) {
            log.warn("发布 GitHub PR Review 失败: owner={}, repo={}, pullNumber={}, error={}",
                    owner, repo, pullNumber, e.getMessage());
        }
    }

    /**
     * 构建 GitHub PR Review API 的请求体
     * <p>
     * 请求体结构：
     * {
     *   "body": "总体评论",
     *   "event": "APPROVE" | "REQUEST_CHANGES" | "COMMENT",
     *   "comments": [{"path": "file.java", "position": 1, "body": "行内评论"}]
     * }
     */
    private Map<String, Object> buildReviewRequest(ReviewResult result) {
        Map<String, Object> requestBody = new HashMap<>();

        // 设置 Review body（总体评论）
        String body = formatReviewBody(result);
        requestBody.put("body", body);

//        // 设置 event 类型
//        if (result.getVerdict() == ReviewVerdict.PASSED) {
//            requestBody.put("event", "APPROVE");
//        } else {
            requestBody.put("event", "COMMENT");
//        }

        // 构建行内评论（comments）
        List<Map<String, Object>> comments = buildInlineComments(result);
        if (!comments.isEmpty()) {
            requestBody.put("comments", comments);
        }

        return requestBody;
    }

    /**
     * 格式化 Review 的总体评论
     */
    private String formatReviewBody(ReviewResult result) {
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

    /**
     * 将有行号和文件路径的问题构建为 GitHub Review 的 inline comments。
     * <p>
     * 每个 comment 结构：{"path": "file.java", "position": lineNumber, "body": "评论内容"}
     * 注意：position 是 diff 中的行号位置，这里使用 issue 的 line 作为近似值。
     */
    private List<Map<String, Object>> buildInlineComments(ReviewResult result) {
        List<Map<String, Object>> comments = new ArrayList<>();

        if (result.getIssues() == null) {
            return comments;
        }

        for (ReviewIssue issue : result.getIssues()) {
            // 只有同时有文件路径和行号的问题才能生成行内评论
            if (issue.getFile() != null && issue.getLine() != null && issue.getLine() > 0) {
                Map<String, Object> comment = new HashMap<>();
                comment.put("path", issue.getFile());
                comment.put("position", issue.getLine());

                StringBuilder body = new StringBuilder();
                body.append("**[").append(issue.getSeverity()).append("]** ").append(issue.getMessage());
                if (issue.getSuggestion() != null) {
                    body.append("\n\n💡 **建议:** ").append(issue.getSuggestion());
                }
                comment.put("body", body.toString());

                comments.add(comment);
            }
        }

        return comments;
    }

    /**
     * 构建 GitHub API 请求头
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("X-GitHub-Api-Version", GITHUB_API_VERSION);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/vnd.github+json");
        return headers;
    }
}
