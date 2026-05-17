package cn.gyb.llm.cr.agent.agent;

import cn.gyb.llm.cr.agent.common.ReviewIssue;
import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.common.ReviewVerdict;
import cn.gyb.llm.cr.agent.prompts.CodeReviewPrompts;
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
 * 代码审查 ReAct Agent，基于 SimpleReactAgent 模式，通过 observe-think-act 循环审查代码。
 * <p>
 * 遵循 dodo-agent 的 SimpleReactAgent 模式:
 * <ul>
 *   <li>构建包含 SystemMessage + UserMessage 的消息列表</li>
 *   <li>使用轮次计数器和最大轮次检查进行循环</li>
 *   <li>调用 chatClient.prompt().messages(messages).call().chatClientResponse()</li>
 *   <li>检查 hasToolCalls() - 如果没有工具调用，解析最终答案</li>
 *   <li>如果有工具调用，通过 callback.call(args) 执行，构建 ToolResponseMessage</li>
 *   <li>继续循环</li>
 * </ul>
 */
@Slf4j
public class CodeReviewAgent {

    /** 默认最大推理轮次 */
    private static final int DEFAULT_MAX_ROUNDS = 5;

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
     * 构造代码审查 Agent。
     *
     * @param chatModel 聊天模型实例
     * @param tools     Agent 可调用的工具回调列表
     */
    public CodeReviewAgent(ChatModel chatModel, List<ToolCallback> tools) {
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
     * 对给定的 diff 内容执行代码审查。
     * <p>
     * Agent 会在推理过程中自主调用 listAvailableSkills / getSkillContent 工具，
     * 根据 diff 涉及的语言动态选择并加载对应的编码规范，无需调用方预先注入规则。
     *
     * @param diff MR diff 内容
     * @return 审查结果
     */
    public ReviewResult review(String diff) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(CodeReviewPrompts.getSystemPrompt()));
        messages.add(new UserMessage("<diff>\n" + diff + "\n</diff>"));

        int round = 0;
        while (true) {
            round++;
            log.info("代码审查代理轮次 {}/{}", round, maxRounds);

            if (round > maxRounds) {
                messages.add(new UserMessage(
                        "你已达到最大推理轮次限制。请基于当前信息直接给出最终审查结果。禁止再调用任何工具。"));
            }

            ChatClientResponse response = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .chatClientResponse();

            String aiText = response.chatResponse().getResult().getOutput().getText();
            log.info("AI 响应: {}", aiText);

            // 没有工具调用 = 最终答案
            if (!response.chatResponse().hasToolCalls()) {
                log.info("未检测到工具调用，解析最终审查结果");
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
                    log.info("工具 {} 返回结果长度: {}", toolCall.name(), resultStr.length());
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
     * 解析 AI 返回的审查结果文本，提取 verdict、summary 和 issues 字段。
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

            log.info("解析审查结果: verdict={}, 问题数量={}",
                    result.getVerdict(), result.getIssues() != null ? result.getIssues().size() : 0);
            return result;
        } catch (Exception e) {
            log.warn("解析审查结果 JSON 失败，使用回退方案: {}", e.getMessage());
            return buildFallbackResult(text);
        }
    }

    /**
     * 构建回退审查结果，当无法解析 AI 响应时使用。
     *
     * @param rawText 原始 AI 响应文本
     * @return 回退审查结果，verdict 为 ISSUES_FOUND
     */
    private ReviewResult buildFallbackResult(String rawText) {
        ReviewResult fallback = new ReviewResult();
        fallback.setVerdict(ReviewVerdict.ISSUES_FOUND);
        fallback.setSummary("无法解析审查结果，原始内容：" + rawText);
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
