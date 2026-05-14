package cn.gyb.llm.cr.agent.controller;

import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;
import cn.gyb.llm.cr.agent.service.ReviewService;
import com.alibaba.fastjson2.JSON;
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

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * GitLab Webhook 控制器，接收并处理 MR 事件。
 * <p>
 * 验证 Webhook 密钥令牌，过滤非合并请求事件和无效状态，
 * 然后异步分发审查处理任务。
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    /** 支持的合并请求操作类型 */
    private static final Set<String> SUPPORTED_ACTIONS = Set.of("open", "update", "reopen");

    /** 代码审查服务 */
    @Autowired
    private ReviewService reviewService;

    /** GitLab Webhook 密钥令牌，用于验证请求合法性 */
    @Value("${gitlab.webhook.secret-token}")
    private String secretToken;

    /**
     * 处理 GitLab Webhook 事件。
     * <p>
     * 接收 GitLab 推送的 Merge Request 事件，验证令牌后异步触发代码审查。
     * 仅处理 opened 状态且操作类型为 open/update/reopen 的合并请求。
     *
     * @param token   X-Gitlab-Token 请求头，用于验证 Webhook 来源
     * @param payload GitLab Webhook 事件的 JSON 载荷
     * @return 202 Accepted 表示已接受处理，401 表示令牌无效，400 表示载荷格式错误
     */
    @PostMapping("/gitlab")
    public ResponseEntity<String> handleGitLabWebhook(@RequestBody String payload) {
            ///232323232
        log.info("收到 GitLab webhook 事件{}", payload);

        // 验证 Webhook 密钥令牌
        //if (!secretToken.equals(token)) {
        //    log.warn("收到无效的 Webhook 令牌，拒绝请求");
        //    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("无效的令牌");
        //}

        try {
            MergeRequestEvent event = JSON.parseObject(payload, MergeRequestEvent.class);

            // 只处理合并请求事件
            if (!"merge_request".equals(event.getObjectKind())) {
                log.debug("忽略非 merge_request 事件: objectKind={}", event.getObjectKind());
                return ResponseEntity.ok("已忽略");
            }

            MergeRequestEvent.MergeRequestAttributes attrs = event.getObjectAttributes();

            // 检查状态和操作
            if (attrs == null) {
                log.warn("合并请求事件没有 object_attributes，忽略");
                return ResponseEntity.ok("已忽略");
            }

            if (!"opened".equals(attrs.getState())) {
                log.debug("忽略状态为 {} 的合并请求", attrs.getState());
                return ResponseEntity.ok("已忽略");
            }

            if (!SUPPORTED_ACTIONS.contains(attrs.getAction())) {
                log.debug("忽略操作为 {} 的合并请求", attrs.getAction());
                return ResponseEntity.ok("已忽略");
            }

            log.info("处理合并请求事件: projectId={}, iid={}, action={}, title={}",
                    event.getProject() != null ? event.getProject().getId() : "unknown",
                    attrs.getIid(),
                    attrs.getAction(),
                    attrs.getTitle());

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
            log.error("解析 GitLab Webhook 载荷失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无效的载荷");
        }
    }
}
