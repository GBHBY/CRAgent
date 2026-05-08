package cn.gyb.llm.cr.agent.agent;

import cn.gyb.llm.cr.agent.common.ReviewIssue;
import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.common.ReviewVerdict;
import cn.gyb.llm.cr.agent.prompts.FullScanPrompts;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全量扫描 Agent，逐文件扫描项目代码。
 * <p>
 * 基于 ReAct 模式，遵循与 CodeReviewAgent 相同的模式，但使用 FullScanPrompts，
 * 设计用于扫描所有项目文件而非单个 MR diff。
 * <p>
 * 扫描流程：先列出项目文件，然后逐个读取并审查每个文件，最终输出全量扫描报告。
 */
@Slf4j
public class FullScanAgent {

    /** 默认最大推理轮次 */
    private static final int DEFAULT_MAX_ROUNDS = 15;

    /** 用于从 AI 响应中提取 JSON 代码块的正则表达式 */
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\s*\\n?(.*?)\\n?\\s*```", Pattern.DOTALL);

    /** 聊天模型，用于与 LLM 交互 */
    private final ChatModel chatModel;

    /** Agent 可调用的工具回调列表 */
    private final List<ToolCallback> tools;

    /** ChatClient 实例，用于发送聊天请求 */
    private ChatClient chatClient;

    /** 最大推理轮次，超过后强制输出最终结果 */
    private int maxRounds = DEFAULT_MAX_ROUNDS;

    /**
     * 构造全量扫描 Agent。
     *
     * @param chatModel 聊天模型实例
     * @param tools     Agent 可调用的工具回调列表
     */
    public FullScanAgent(ChatModel chatModel, List<ToolCallback> tools) {
        this.chatModel = chatModel;
        this.tools = tools;
        initChatClient();
    }

    /**
     * 初始化 ChatClient，配置工具调用选项并注册工具回调。
     * 禁用内部工具自动执行，由 Agent 手动控制工具调用流程。
     */
    private void initChatClient() {
        ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .internalToolExecutionEnabled(false)
                .build();
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(toolOptions)
                .defaultToolCallbacks(tools)
                .build();
    }

    /**
     * 对给定项目和分支执行全量扫描。
     *
     * @param projectId GitLab 项目 ID
     * @param branch    要扫描的分支
     * @param rules     活跃的编码规则
     * @return 扫描结果
     */
    public ReviewResult scan(Long projectId, String branch, String rules) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(FullScanPrompts.getSystemPrompt(rules)));
        messages.add(new UserMessage(String.format(
                "请对项目进行全面代码扫描。\n项目 ID: %d\n分支: %s\n请先列出项目文件，然后逐个读取并审查每个文件。",
                projectId, branch)));

        int round = 0;
        while (true) {
            round++;
            log.info("全量扫描代理轮次 {}/{}, 项目 {}，分支: {}", round, maxRounds, projectId, branch);

            if (round > maxRounds) {
                messages.add(new UserMessage(
                        "你已达到最大推理轮次限制。请基于当前已扫描的文件信息直接给出最终扫描结果。禁止再调用任何工具。"));
            }

            ChatClientResponse response = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .chatClientResponse();

            String aiText = response.chatResponse().getResult().getOutput().getText();
            log.debug("AI 响应文本长度: {}", aiText != null ? aiText.length() : 0);

            // 没有工具调用 = 最终答案
            if (!response.chatResponse().hasToolCalls()) {
                log.info("未检测到工具调用，解析最终扫描结果");
                return parseReviewResult(aiText);
            }

            // 执行工具调用
            List<AssistantMessage.ToolCall> toolCalls = response.chatResponse().getResult().getOutput().getToolCalls();
            log.info("收到 {} 个工具调用", toolCalls.size());

            messages.add(AssistantMessage.builder().content(aiText).toolCalls(toolCalls).build());

            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                log.info("执行工具: {}，参数: {}", toolCall.name(), toolCall.arguments());
                ToolCallback callback = findTool(toolCall.name());
                if (callback == null) {
                    log.warn("工具未找到: {}", toolCall.name());
                    addErrorToolResponse(messages, toolCall, "工具未找到: " + toolCall.name());
                    continue;
                }
                try {
                    Object result = callback.call(toolCall.arguments());
                    String resultStr = Objects.toString(result, "");
                    log.debug("工具 {} 返回结果长度: {}", toolCall.name(), resultStr.length());
                    messages.add(ToolResponseMessage.builder()
                            .responses(List.of(new ToolResponseMessage.ToolResponse(
                                    toolCall.id(), toolCall.name(), resultStr)))
                            .build());
                } catch (Exception e) {
                    log.error("工具执行失败: {}", toolCall.name(), e);
                    addErrorToolResponse(messages, toolCall, "工具执行失败：" + e.getMessage());
                }
            }
        }
    }

    /**
     * 解析 AI 返回的扫描结果文本，提取 verdict、summary 和 issues 字段。
     * 支持从 ```json ``` 代码块或原始 JSON 对象中提取。
     *
     * @param text AI 返回的文本内容
     * @return 解析后的审查结果
     */
    private ReviewResult parseReviewResult(String text) {
        if (text == null || text.isBlank()) {
            return buildFallbackResult("AI 返回了空内容");
        }

        try {
            // 从 ```json ... ``` 代码块中提取 JSON
            Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
            String jsonStr;
            if (matcher.find()) {
                jsonStr = matcher.group(1).trim();
            } else {
                // 回退: 尝试查找原始 JSON 对象
                int start = text.indexOf('{');
                int end = text.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    jsonStr = text.substring(start, end + 1);
                } else {
                    return buildFallbackResult(text);
                }
            }

            JSONObject obj = JSON.parseObject(jsonStr);

            ReviewResult result = new ReviewResult();
            result.setVerdict(ReviewVerdict.valueOf(obj.getString("verdict")));
            result.setSummary(obj.getString("summary"));
            result.setIssues(obj.getList("issues", ReviewIssue.class));

            log.info("解析扫描结果: verdict={}, 问题数量={}",
                    result.getVerdict(), result.getIssues() != null ? result.getIssues().size() : 0);
            return result;
        } catch (Exception e) {
            log.warn("解析扫描结果 JSON 失败，使用回退方案: {}", e.getMessage());
            return buildFallbackResult(text);
        }
    }

    /**
     * 构建回退扫描结果，当无法解析 AI 响应时使用。
     *
     * @param rawText 原始 AI 响应文本
     * @return 回退审查结果，verdict 为 ISSUES_FOUND
     */
    private ReviewResult buildFallbackResult(String rawText) {
        ReviewResult fallback = new ReviewResult();
        fallback.setVerdict(ReviewVerdict.ISSUES_FOUND);
        fallback.setSummary("无法解析扫描结果，原始内容：" + rawText);
        return fallback;
    }

    /**
     * 根据工具名称在已注册的工具列表中查找对应的工具回调。
     *
     * @param name 工具名称
     * @return 匹配的工具回调，未找到则返回 null
     */
    private ToolCallback findTool(String name) {
        return tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * 将工具执行错误信息添加到消息列表中，构建错误类型的 ToolResponseMessage。
     *
     * @param messages 消息列表
     * @param toolCall 原始工具调用请求
     * @param errMsg   错误信息
     */
    private void addErrorToolResponse(List<Message> messages, AssistantMessage.ToolCall toolCall, String errMsg) {
        messages.add(ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        toolCall.id(), toolCall.name(), "{\"error\":\"" + errMsg + "\"}")))
                .build());
    }
}
