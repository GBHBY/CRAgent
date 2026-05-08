package cn.gyb.llm.cr.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置类，构建并注册 Spring AI ChatClient Bean。
 * <p>
 * 基于 Spring AI 自动注入的 ChatModel 创建 ChatClient 实例，
 * 供各 Agent 和服务组件使用。
 */
@Configuration
public class ChatClientConfig {

    /**
     * 创建 ChatClient Bean。
     *
     * @param chatModel Spring AI 自动注入的聊天模型
     * @return 构建好的 ChatClient 实例
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
