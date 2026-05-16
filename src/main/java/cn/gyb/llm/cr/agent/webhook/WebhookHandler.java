package cn.gyb.llm.cr.agent.webhook;

import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;

/**
 * Webhook 处理器策略接口
 * <p>
 * 不同代码托管平台（GitLab、GitHub等）实现此接口，
 * 将平台特有的 Webhook 请求体解析为统一的 MergeRequestEvent 模型。
 */
public interface WebhookHandler {

    /**
     * 当前处理器支持的平台类型
     *
     * @return 平台类型标识，如 "gitlab"、"github"
     */
    String platformType();

    /**
     * 验证 Webhook 请求的合法性
     *
     * @param secretToken 配置的密钥令牌
     * @param headerToken 请求头中携带的令牌
     * @return true 表示验证通过
     */
    boolean validateToken(String secretToken, String headerToken);

    /**
     * 将原始 payload 解析为统一的 MergeRequestEvent
     * <p>
     * 如果事件不需要处理（如非合并请求事件、不支持的操作类型等），返回 null。
     *
     * @param payload 原始 JSON 载荷
     * @return 解析后的合并请求事件，不需要处理时返回 null
     */
    MergeRequestEvent parseEvent(String payload);
}
