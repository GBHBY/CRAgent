package cn.gyb.llm.cr.agent.tool;

import cn.gyb.llm.cr.agent.common.ReviewIssue;
import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.common.ReviewVerdict;
import cn.gyb.llm.cr.agent.service.NotionDocumentService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * Notion 文档写入工具，Agent 可通过此工具将审查结果写入 Notion。
 * <p>
 * 将代码审查结果（包括结论、摘要、问题列表）格式化后写入 Notion 数据库，
 * 返回创建的 Notion 页面 URL。
 */
@Slf4j
@Service
public class NotionWriteTool {

    /** Notion 文档服务，用于创建审查文档 */
    private final NotionDocumentService notionDocumentService;

    /**
     * 构造 Notion 文档写入工具。
     *
     * @param notionDocumentService Notion 文档服务实例
     */
    public NotionWriteTool(NotionDocumentService notionDocumentService) {
        this.notionDocumentService = notionDocumentService;
    }

    /**
     * 将代码审查结果写入 Notion 文档，返回创建的文档 URL。
     *
     * @param projectId    GitLab 项目 ID
     * @param projectName  项目名称
     * @param mrTitle      Merge Request 标题
     * @param mrUrl        Merge Request URL
     * @param sourceBranch 源分支名称
     * @param targetBranch 目标分支名称
     * @param reviewResult 审查结果 JSON 字符串，包含 verdict、summary、issues
     * @return 创建结果提示信息，包含 Notion 页面地址
     */
    @Tool(description = "将代码审查结果写入 Notion 文档，返回创建的文档 URL")
    public String writeReviewDocument(
            @ToolParam(description = "GitLab 项目 ID") Long projectId,
            @ToolParam(description = "项目名称") String projectName,
            @ToolParam(description = "Merge Request 标题") String mrTitle,
            @ToolParam(description = "Merge Request URL") String mrUrl,
            @ToolParam(description = "源分支名称") String sourceBranch,
            @ToolParam(description = "目标分支名称") String targetBranch,
            @ToolParam(description = "审查结果 JSON 字符串，包含 verdict、summary、issues") String reviewResult) {
        log.info("写入审查文档到 Notion: projectId={}, mrTitle={}", projectId, mrTitle);
        try {
            ReviewResult result = parseReviewResult(reviewResult);
            String pageUrl = notionDocumentService.createReviewDocument(
                    projectId, projectName, mrTitle, mrUrl, result, sourceBranch, targetBranch);
            if (pageUrl == null || pageUrl.isBlank()) {
                return "审查文档已提交，但未能获取到 Notion 页面 URL（功能可能尚未完全配置）。";
            }
            return "审查文档已成功写入 Notion，页面地址：" + pageUrl;
        } catch (Exception e) {
            log.error("写入 Notion 审查文档失败: projectId={}, mrTitle={}", projectId, mrTitle, e);
            return "{\"error\": \"写入 Notion 文档失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 解析审查结果 JSON 字符串为 ReviewResult 对象。
     * 解析失败时使用回退方案，将原始文本作为摘要。
     *
     * @param json 审查结果 JSON 字符串
     * @return 解析后的审查结果对象
     */
    private ReviewResult parseReviewResult(String json) {
        try {
            com.alibaba.fastjson2.JSONObject obj = JSON.parseObject(json);
            ReviewResult result = new ReviewResult();
            result.setVerdict(ReviewVerdict.valueOf(obj.getString("verdict")));
            result.setSummary(obj.getString("summary"));
            result.setIssues(obj.getList("issues", ReviewIssue.class));
            return result;
        } catch (Exception e) {
            log.warn("解析审查结果 JSON 失败，使用回退方案: {}", e.getMessage());
            ReviewResult fallback = new ReviewResult();
            fallback.setVerdict(ReviewVerdict.ISSUES_FOUND);
            fallback.setSummary(json);
            return fallback;
        }
    }
}
