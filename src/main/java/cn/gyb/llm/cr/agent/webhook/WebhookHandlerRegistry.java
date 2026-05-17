package cn.gyb.llm.cr.agent.webhook;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Webhook 处理器注册中心
 * <p>
 * 管理所有 WebhookHandler 实现，根据配置文件中的平台类型
 * 选择当前激活的处理器。
 */
@Slf4j
@Component
public class WebhookHandlerRegistry {

    /** 配置的平台类型 */
    @Value("${webhook.platform:gitlab}")
    private String platform;

    /** 所有注册的处理器，key 为平台类型 */
    private final Map<String, WebhookHandler> handlers;

    public WebhookHandlerRegistry(List<WebhookHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(WebhookHandler::platformType, Function.identity()));
    }

    @PostConstruct
    public void init() {
        log.info("Webhook 处理器注册中心初始化，当前平台: {}，已注册处理器: {}",
                platform, handlers.keySet());

        if (!handlers.containsKey(platform)) {
            throw new IllegalStateException(
                    String.format("不支持的 Webhook 平台类型: %s，可用类型: %s", platform, handlers.keySet()));
        }
    }

    /**
     * 获取当前激活的 Webhook 处理器
     *
     * @return 当前平台对应的处理器
     */
    public WebhookHandler getActiveHandler() {
        return handlers.get(platform);
    }

    /**
     * 获取当前配置的平台类型
     *
     * @return 平台类型标识
     */
    public String getPlatform() {
        return platform;
    }
}
