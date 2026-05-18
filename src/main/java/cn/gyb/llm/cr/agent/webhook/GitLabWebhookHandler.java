package cn.gyb.llm.cr.agent.webhook;

import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * GitLab Webhook 处理器
 * <p>
 * 解析 GitLab 平台的 Merge Request Webhook 事件，
 * 将其转换为统一的 MergeRequestEvent 模型。
 */
@Slf4j
@Component
public class GitLabWebhookHandler implements WebhookHandler {

    /** 支持的合并请求操作类型 */
    private static final Set<String> SUPPORTED_ACTIONS = Set.of("open", "update", "reopen");

    @Override
    public String platformType() {
        return "gitlab";
    }

    @Override
    public boolean validateToken(String secretToken, String headerToken) {
        if (secretToken == null || secretToken.isBlank()) {
            return true;
        }
        return secretToken.equals(headerToken);
    }

    @Override
    public MergeRequestEvent parseEvent(String payload) {
        MergeRequestEvent event = JSON.parseObject(payload, MergeRequestEvent.class);

        // 只处理合并请求事件
        if (!"merge_request".equals(event.getObjectKind())) {
            log.debug("忽略非 merge_request 事件: objectKind={}", event.getObjectKind());
            return null;
        }

        MergeRequestEvent.MergeRequestAttributes attrs = event.getObjectAttributes();

        // 检查属性是否存在
        if (attrs == null) {
            log.warn("合并请求事件没有 object_attributes，忽略");
            return null;
        }

        // 检查状态
        if (!"opened".equals(attrs.getState())) {
            log.debug("忽略状态为 {} 的合并请求", attrs.getState());
            return null;
        }

        // 检查操作类型
        if (!SUPPORTED_ACTIONS.contains(attrs.getAction())) {
            log.debug("忽略操作为 {} 的合并请求", attrs.getAction());
            return null;
        }

        // 设置平台标识
        event.setPlatform(platformType());

        log.info("解析 GitLab 合并请求事件: projectId={}, iid={}, action={}, title={}",
                event.getProject() != null ? event.getProject().getId() : "unknown",
                attrs.getIid(),
                attrs.getAction(),
                attrs.getTitle());

        return event;
    }
}
