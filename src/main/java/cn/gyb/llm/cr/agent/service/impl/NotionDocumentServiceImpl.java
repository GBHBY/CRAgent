package cn.gyb.llm.cr.agent.service.impl;

import cn.gyb.llm.cr.agent.common.ReviewIssue;
import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.config.McpClientConfig;
import cn.gyb.llm.cr.agent.service.NotionDocumentService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Notion 文档服务实现，通过 Notion MCP 工具回调创建审查文档。
 * <p>
 * 将代码审查结果格式化为 Notion 页面，包含审查概要、问题列表和 MR 信息等章节，
 * 写入指定的 Notion 数据库。
 */
@Slf4j
@Service
public class NotionDocumentServiceImpl implements NotionDocumentService {

    /** Notion MCP 客户端配置，提供工具回调 */
    @Autowired
    private McpClientConfig mcpClientConfig;

    /** Notion 数据库 ID，指定审查文档写入的目标数据库 */
    @Value("${notion.database-id}")
    private String notionDatabaseId;

    /**
     * 创建 Notion 审查文档，将审查结果写入指定数据库。
     *
     * @param projectId    GitLab 项目 ID
     * @param projectName  项目名称
     * @param mrTitle      合并请求标题
     * @param mrUrl        合并请求 URL
     * @param result       审查结果
     * @param sourceBranch 源分支名称
     * @param targetBranch 目标分支名称
     * @return 创建的 Notion 页面 URL，失败时返回空字符串
     */
    @Override
    public String createReviewDocument(Long projectId, String projectName, String mrTitle, String mrUrl,
                                       ReviewResult result, String sourceBranch, String targetBranch) {
        log.info("创建 Notion 审查文档 - projectId={}, projectName={}, mrTitle={}, sourceBranch={}, targetBranch={}",
                projectId, projectName, mrTitle, sourceBranch, targetBranch);

        ToolCallback[] notionToolCallbacks = mcpClientConfig.getNotionToolCallbacks();
        if (notionToolCallbacks == null || notionToolCallbacks.length == 0) {
            log.warn("Notion MCP 工具回调未初始化，跳过文档创建");
            return "";
        }

        // 查找创建页面的工具回调
        ToolCallback createPageTool = null;
        for (ToolCallback callback : notionToolCallbacks) {
            String toolName = callback.getToolDefinition().name().toLowerCase();
            if (toolName.contains("create") && toolName.contains("page")) {
                createPageTool = callback;
                break;
            }
        }

        if (createPageTool == null) {
            log.warn("未找到 Notion MCP 回调中的 'create page' 工具，可用工具: {}",
                    List.of(notionToolCallbacks).stream()
                            .map(t -> t.getToolDefinition().name())
                            .collect(Collectors.joining(", ")));
            return "";
        }

        try {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String title = String.format("[CR] %s - %s - %s", projectName, mrTitle, date);

            JSONObject arguments = buildCreatePageArguments(title, result, mrUrl, sourceBranch, targetBranch);
            log.debug("调用 Notion 创建页面工具，参数: {}", arguments.toJSONString());

            String response = createPageTool.call(arguments.toJSONString());
            log.debug("Notion 创建页面响应: {}", response);

            String pageUrl = parsePageUrlFromResponse(response);
            if (StringUtils.isNotBlank(pageUrl)) {
                log.info("Notion 审查文档创建成功: {}", pageUrl);
            } else {
                log.warn("Notion 页面已创建，但无法从响应中提取 URL");
            }
            return pageUrl;
        } catch (Exception e) {
            log.error("创建 Notion 审查文档失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 构建创建 Notion 页面的请求参数，包含标题、属性和页面内容。
     *
     * @param title        页面标题
     * @param result       审查结果
     * @param mrUrl        合并请求 URL
     * @param sourceBranch 源分支
     * @param targetBranch 目标分支
     * @return Notion API 请求参数的 JSON 对象
     */
    private JSONObject buildCreatePageArguments(String title, ReviewResult result, String mrUrl,
                                                String sourceBranch, String targetBranch) {
        JSONObject args = new JSONObject();

        // 父级: database_id
        JSONObject parent = new JSONObject();
        parent.put("database_id", notionDatabaseId);
        args.put("parent", parent);

        // 属性
        JSONObject properties = new JSONObject();
        JSONObject titleProp = new JSONObject();
        JSONArray titleArr = new JSONArray();
        JSONObject titleText = new JSONObject();
        titleText.put("content", title);
        JSONObject richText = new JSONObject();
        richText.put("text", titleText);
        titleArr.add(richText);
        titleProp.put("title", titleArr);
        properties.put("Name", titleProp);
        args.put("properties", properties);

        // 子内容（页面正文）
        JSONArray children = new JSONArray();

        // 标题 2: 审查概要
        children.add(buildHeadingBlock("审查概要"));

        // 段落: 摘要
        String summaryText = result.getSummary() != null ? result.getSummary() : "无摘要信息";
        children.add(buildParagraphBlock(summaryText));

        // 标题 2: 问题列表（数量）
        int issueCount = result.getIssues() != null ? result.getIssues().size() : 0;
        children.add(buildHeadingBlock("问题列表 (" + issueCount + ")"));

        // 每个问题的编号列表项
        if (result.getIssues() != null) {
            int index = 1;
            for (ReviewIssue issue : result.getIssues()) {
                String issueLine = String.format("%d. [%s] %s (Line %d): %s",
                        index++,
                        issue.getSeverity() != null ? issue.getSeverity() : "UNKNOWN",
                        issue.getFile() != null ? issue.getFile() : "unknown",
                        issue.getLine() != null ? issue.getLine() : 0,
                        issue.getMessage() != null ? issue.getMessage() : "");
                children.add(buildParagraphBlock(issueLine));
            }
        }

        // 标题 2: MR 信息
        children.add(buildHeadingBlock("MR 信息"));

        // 段落: 分支信息和 MR 链接
        String mrInfo = String.format("Branch: %s -> %s\nMR Link: %s",
                sourceBranch != null ? sourceBranch : "unknown",
                targetBranch != null ? targetBranch : "unknown",
                mrUrl != null ? mrUrl : "");
        children.add(buildParagraphBlock(mrInfo));

        args.put("children", children);
        return args;
    }

    /**
     * 构建二级标题（heading_2）Notion 块。
     *
     * @param text 标题文本
     * @return heading_2 类型的 Notion 块 JSON 对象
     */
    private JSONObject buildHeadingBlock(String text) {
        JSONObject block = new JSONObject();
        block.put("object", "block");
        block.put("type", "heading_2");

        JSONObject heading = new JSONObject();
        JSONArray richTextArr = new JSONArray();
        JSONObject richTextObj = new JSONObject();
        JSONObject textObj = new JSONObject();
        textObj.put("content", text);
        richTextObj.put("text", textObj);
        richTextArr.add(richTextObj);
        heading.put("rich_text", richTextArr);
        block.put("heading_2", heading);

        return block;
    }

    /**
     * 构建段落（paragraph）Notion 块。
     *
     * @param text 段落文本
     * @return paragraph 类型的 Notion 块 JSON 对象
     */
    private JSONObject buildParagraphBlock(String text) {
        JSONObject block = new JSONObject();
        block.put("object", "block");
        block.put("type", "paragraph");

        JSONObject paragraph = new JSONObject();
        JSONArray richTextArr = new JSONArray();
        JSONObject richTextObj = new JSONObject();
        JSONObject textObj = new JSONObject();
        textObj.put("content", text);
        richTextObj.put("text", textObj);
        richTextArr.add(richTextObj);
        paragraph.put("rich_text", richTextArr);
        block.put("paragraph", paragraph);

        return block;
    }

    /**
     * 从 Notion MCP 创建页面的响应中解析页面 URL。
     * 支持多种响应结构：直接 url 字段、嵌套 content 数组、text 字段等。
     *
     * @param response MCP 工具调用的原始响应字符串
     * @return 解析出的页面 URL，解析失败时返回空字符串
     */
    private String parsePageUrlFromResponse(String response) {
        try {
            JSONObject json = JSON.parseObject(response);
            // 尝试常见的 Notion MCP create-page 响应结构
            if (json.containsKey("url")) {
                return json.getString("url");
            }
            // 嵌套内容结构
            if (json.containsKey("content")) {
                JSONArray contentItems = json.getJSONArray("content");
                for (int i = 0; i < contentItems.size(); i++) {
                    JSONObject item = contentItems.getJSONObject(i);
                    if (item.containsKey("url")) {
                        return item.getString("url");
                    }
                    if (item.containsKey("text")) {
                        String text = item.getString("text");
                        if (text != null && text.contains("notion.so")) {
                            return text;
                        }
                    }
                }
            }
            // 尝试从文本内容中提取 URL
            if (json.containsKey("text")) {
                String text = json.getString("text");
                if (text != null && text.contains("notion.so")) {
                    return text;
                }
            }
            log.debug("无法从响应结构中提取页面 URL: {}", json.keySet());
        } catch (Exception e) {
            log.debug("解析 Notion 响应获取页面 URL 失败: {}", e.getMessage());
        }
        return "";
    }
}
