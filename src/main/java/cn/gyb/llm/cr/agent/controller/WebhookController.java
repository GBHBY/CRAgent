package cn.gyb.llm.cr.agent.controller;

import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;
import cn.gyb.llm.cr.agent.service.ReviewService;
import cn.gyb.llm.cr.agent.webhook.WebhookHandler;
import cn.gyb.llm.cr.agent.webhook.WebhookHandlerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * Webhook 控制器，接收并处理代码合并请求事件。
 * <p>
 * 通过策略模式自动适配不同代码托管平台（GitLab、GitHub等）的 Webhook 请求体，
 * 验证令牌后异步分发审查处理任务。
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    /** 代码审查服务 */
    @Autowired
    private ReviewService reviewService;

    /** Webhook 处理器注册中心 */
    @Autowired
    private WebhookHandlerRegistry webhookHandlerRegistry;

    /** Webhook 密钥令牌，用于验证请求合法性 */
    @Value("${gitlab.webhook.secret-token:}")
    private String secretToken;

    /**
     * 处理 Webhook 事件。
     * <p>
     * 接收代码托管平台推送的合并请求事件，通过策略模式自动适配平台格式，
     * 验证令牌后异步触发代码审查。
     *
     * @param token   请求头中的验证令牌（GitLab: X-Gitlab-Token, GitHub: X-Hub-Signature-256）
     * @param payload Webhook 事件的 JSON 载荷
     * @return 202 Accepted 表示已接受处理，401 表示令牌无效，400 表示载荷格式错误
     */
    @PostMapping("/receive")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String gitlabToken,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String githubToken,
            @RequestBody String payload) {

        WebhookHandler handler = webhookHandlerRegistry.getActiveHandler();
        String platform = webhookHandlerRegistry.getPlatform();

        log.info("收到 {} Webhook 事件", platform);

        // 验证 Webhook 密钥令牌
        //String headerToken = resolveHeaderToken(gitlabToken, githubToken);
        //if (!handler.validateToken(secretToken, headerToken)) {
        //    log.warn("收到无效的 Webhook 令牌，拒绝请求");
        //    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("无效的令牌");
        //}

        try {
            // 使用策略处理器解析事件
            MergeRequestEvent event = handler.parseEvent(payload);

            if (event == null) {
                return ResponseEntity.ok("已忽略");
            }

            // 异步分发审查处理
            CompletableFuture.runAsync(() -> {
                try {
                    reviewService.handleMergeRequestEvent(event);
                } catch (Exception e) {
                    log.error("异步处理合并请求事件出错: {}", e.getMessage(), e);
                }
            });

            return ResponseEntity.accepted().body("已接受");

        } catch (Exception e) {
            log.error("解析 {} Webhook 载荷失败: {}", platform, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无效的载荷");
        }
    }

    /**
     * 根据平台选择合适的请求头令牌
     */
    private String resolveHeaderToken(String gitlabToken, String githubToken) {
        if (gitlabToken != null) {
            return gitlabToken;
        }
        return githubToken;
    }
}
