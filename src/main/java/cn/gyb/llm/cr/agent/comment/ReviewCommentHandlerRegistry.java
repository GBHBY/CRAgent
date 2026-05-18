package cn.gyb.llm.cr.agent.comment;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 审查评论回写处理器注册中心
 * <p>
 * 管理所有 ReviewCommentHandler 实现，根据平台类型选择对应的评论回写策略。
 * 与 WebhookHandlerRegistry 设计思路一致，均采用策略模式 + 自动注册。
 */
@Slf4j
@Component
public class ReviewCommentHandlerRegistry {

    /** 所有注册的评论回写处理器，key 为平台类型 */
    private final Map<String, ReviewCommentHandler> handlers;

    public ReviewCommentHandlerRegistry(List<ReviewCommentHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(ReviewCommentHandler::platformType, Function.identity()));
    }

    @PostConstruct
    public void init() {
        log.info("审查评论回写处理器注册中心初始化，已注册处理器: {}", handlers.keySet());
    }

    /**
     * 根据平台类型获取对应的评论回写处理器
     *
     * @param platform 平台类型标识，如 "gitlab"、"github"
     * @return 对应平台的处理器，若不存在则返回 null
     */
    public ReviewCommentHandler getHandler(String platform) {
        ReviewCommentHandler handler = handlers.get(platform);
        if (handler == null) {
            log.warn("未找到平台 {} 对应的评论回写处理器，可用处理器: {}", platform, handlers.keySet());
        }
        return handler;
    }
}
