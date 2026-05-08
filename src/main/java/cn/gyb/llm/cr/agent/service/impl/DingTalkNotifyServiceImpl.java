package cn.gyb.llm.cr.agent.service.impl;

import cn.gyb.llm.cr.agent.common.ReviewIssue;
import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.service.DingTalkNotifyService;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于机器人 Webhook 和 HMAC-SHA256 签名的钉钉通知服务实现。
 * <p>
 * 支持发送代码审查通过、发现问题、审查失败和全量扫描报告等类型的 Markdown 消息通知。
 */
@Slf4j
@Service
public class DingTalkNotifyServiceImpl implements DingTalkNotifyService {

    /** 钉钉机器人 Webhook 地址 */
    @Value("${dingtalk.webhook.url}")
    private String webhookUrl;

    /** 钉钉机器人 Webhook 签名密钥 */
    @Value("${dingtalk.webhook.secret}")
    private String webhookSecret;

    /** HTTP 请求客户端 */
    private RestTemplate restTemplate;

    /**
     * 初始化 RestTemplate 实例。
     */
    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();
        log.info("DingTalkNotifyService 初始化完成");
    }

    /**
     * 发送代码审查通过通知。
     *
     * @param mrTitle      合并请求标题
     * @param mrUrl        合并请求 URL
     * @param author       作者
     * @param sourceBranch 源分支
     * @param targetBranch 目标分支
     * @param summary      审查摘要
     */
    @Override
    public void sendReviewPassedNotification(String mrTitle, String mrUrl, String author,
                                             String sourceBranch, String targetBranch, String summary) {
        String title = "代码审查通过";
        String text = buildPassedMarkdown(mrTitle, mrUrl, author, sourceBranch, targetBranch, summary);
        sendMarkdownMessage(title, text);
    }

    /**
     * 发送代码审查发现问题通知，包含问题统计和修复链接。
     *
     * @param mrTitle      合并请求标题
     * @param mrUrl        合并请求 URL
     * @param author       作者
     * @param sourceBranch 源分支
     * @param targetBranch 目标分支
     * @param result       审查结果
     * @param fixMrUrl     自动修复 MR 的 URL（可选）
     * @param notionPageUrl Notion 文档页面 URL（可选）
     */
    @Override
    public void sendIssuesFoundNotification(String mrTitle, String mrUrl, String author,
                                            String sourceBranch, String targetBranch,
                                            ReviewResult result, String fixMrUrl, String notionPageUrl) {
        String title = "代码审查发现问题";
        String text = buildIssuesFoundMarkdown(mrTitle, mrUrl, author, sourceBranch, targetBranch,
                result, fixMrUrl, notionPageUrl);
        sendMarkdownMessage(title, text);
    }

    /**
     * 发送代码审查失败通知。
     *
     * @param mrTitle 合并请求标题
     * @param mrUrl   合并请求 URL
     * @param error   错误信息
     */
    @Override
    public void sendReviewFailedNotification(String mrTitle, String mrUrl, String error) {
        String title = "代码审查失败";
        String text = buildFailedMarkdown(mrTitle, mrUrl, error);
        sendMarkdownMessage(title, text);
    }

    /**
     * 发送全量扫描报告通知。
     *
     * @param projectName  项目名称
     * @param result       扫描结果
     * @param notionPageUrl Notion 文档页面 URL（可选）
     */
    @Override
    public void sendFullScanNotification(String projectName, ReviewResult result, String notionPageUrl) {
        String title = "全量扫描报告 - " + projectName;
        String text = buildFullScanMarkdown(projectName, result, notionPageUrl);
        sendMarkdownMessage(title, text);
    }

    /**
     * 构建带有时间戳和 HMAC-SHA256 签名的 Webhook URL。
     *
     * @return 带签名参数的完整 Webhook URL
     */
    private String buildSignedUrl() {
        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + webhookSecret;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), "UTF-8");

            return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.error("构建签名钉钉 Webhook URL 失败: {}", e.getMessage(), e);
            return webhookUrl;
        }
    }

    /**
     * 发送 Markdown 类型的消息到钉钉 Webhook。
     *
     * @param title 消息标题
     * @param text  Markdown 格式的消息正文
     */
    private void sendMarkdownMessage(String title, String text) {
        try {
            String url = buildSignedUrl();

            JSONObject markdown = new JSONObject();
            markdown.put("title", title);
            markdown.put("text", text);

            JSONObject body = new JSONObject();
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            String response = restTemplate.postForObject(url, entity, String.class);

            log.info("钉钉通知已发送，title={}, response={}", title, response);
        } catch (Exception e) {
            log.error("发送钉钉通知失败: title={}, error={}", title, e.getMessage(), e);
        }
    }

    /**
     * 构建审查通过的 Markdown 消息内容。
     */
    private String buildPassedMarkdown(String mrTitle, String mrUrl, String author,
                                       String sourceBranch, String targetBranch, String summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("### \\u2705 代码审查通过\n\n");
        sb.append("**合并请求:** [").append(mrTitle).append("](").append(mrUrl).append(")\n\n");
        sb.append("**作者:** ").append(author).append("\n\n");
        sb.append("**分支:** ").append(sourceBranch).append(" -> ").append(targetBranch).append("\n\n");
        sb.append("---\n\n");
        sb.append("**摘要:** ").append(summary != null ? summary : "未发现问题。");
        return sb.toString();
    }

    /**
     * 构建审查发现问题的 Markdown 消息内容，包含问题统计和修复链接。
     */
    private String buildIssuesFoundMarkdown(String mrTitle, String mrUrl, String author,
                                            String sourceBranch, String targetBranch,
                                            ReviewResult result, String fixMrUrl, String notionPageUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("### \\u274c 代码审查发现问题\n\n");
        sb.append("**合并请求:** [").append(mrTitle).append("](").append(mrUrl).append(")\n\n");
        sb.append("**作者:** ").append(author).append("\n\n");
        sb.append("**分支:** ").append(sourceBranch).append(" -> ").append(targetBranch).append("\n\n");
        sb.append("---\n\n");

        // Count issues by severity
        if (result.getIssues() != null && !result.getIssues().isEmpty()) {
            Map<String, Long> severityCount = result.getIssues().stream()
                    .collect(Collectors.groupingBy(
                            issue -> issue.getSeverity() != null ? issue.getSeverity() : "UNKNOWN",
                            LinkedHashMap::new,
                            Collectors.counting()));

            sb.append("**问题统计（按严重程度）:**\n\n");
            for (Map.Entry<String, Long> entry : severityCount.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": **").append(entry.getValue()).append("**\n\n");
            }
            sb.append("- 总计: **").append(result.getIssues().size()).append("**\n\n");
        }

        sb.append("**摘要:** ").append(result.getSummary() != null ? result.getSummary() : "").append("\n\n");

        if (fixMrUrl != null && !fixMrUrl.isBlank()) {
            sb.append("**自动修复 MR:** [查看修复 MR](").append(fixMrUrl).append(")\n\n");
        }

        if (notionPageUrl != null && !notionPageUrl.isBlank()) {
            sb.append("**审查文档:** [在 Notion 中查看](").append(notionPageUrl).append(")\n\n");
        }

        return sb.toString();
    }

    /**
     * 构建审查失败的 Markdown 消息内容。
     */
    private String buildFailedMarkdown(String mrTitle, String mrUrl, String error) {
        StringBuilder sb = new StringBuilder();
        sb.append("### \\u26a0\\ufe0f 代码审查失败\n\n");
        sb.append("**合并请求:** [").append(mrTitle).append("](").append(mrUrl).append(")\n\n");
        sb.append("---\n\n");
        sb.append("**错误:** ").append(error != null ? error : "未知错误");
        return sb.toString();
    }

    /**
     * 构建全量扫描报告的 Markdown 消息内容。
     */
    private String buildFullScanMarkdown(String projectName, ReviewResult result, String notionPageUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("### \\U0001f50d 全量扫描报告 - ").append(projectName).append("\n\n");

        if (result.getVerdict() != null) {
            sb.append("**结论:** ").append(result.getVerdict()).append("\n\n");
        }

        if (result.getIssues() != null && !result.getIssues().isEmpty()) {
            Map<String, Long> severityCount = result.getIssues().stream()
                    .collect(Collectors.groupingBy(
                            issue -> issue.getSeverity() != null ? issue.getSeverity() : "UNKNOWN",
                            LinkedHashMap::new,
                            Collectors.counting()));

            sb.append("**问题统计（按严重程度）:**\n\n");
            for (Map.Entry<String, Long> entry : severityCount.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": **").append(entry.getValue()).append("**\n\n");
            }
            sb.append("- 总计: **").append(result.getIssues().size()).append("**\n\n");
        }

        sb.append("**摘要:** ").append(result.getSummary() != null ? result.getSummary() : "").append("\n\n");

        if (notionPageUrl != null && !notionPageUrl.isBlank()) {
            sb.append("**扫描文档:** [在 Notion 中查看](").append(notionPageUrl).append(")\n\n");
        }

        return sb.toString();
    }
}
