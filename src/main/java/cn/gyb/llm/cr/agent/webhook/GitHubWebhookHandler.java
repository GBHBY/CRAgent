package cn.gyb.llm.cr.agent.webhook;

import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * GitHub Webhook 处理器
 * <p>
 * 解析 GitHub 平台的 Pull Request Webhook 事件，
 * 将其转换为统一的 MergeRequestEvent 模型。
 */
@Slf4j
@Component
public class GitHubWebhookHandler implements WebhookHandler {

    /** 支持的 PR 操作类型 */
    private static final Set<String> SUPPORTED_ACTIONS = Set.of("opened", "synchronize", "reopened");

    @Override
    public String platformType() {
        return "github";
    }

    @Override
    public boolean validateToken(String secretToken, String headerToken) {
        if (secretToken == null || secretToken.isBlank()) {
            return true;
        }
        // GitHub 使用 HMAC-SHA256 签名验证，这里简化为直接比对
        // 实际生产环境应使用 X-Hub-Signature-256 进行 HMAC 验证
        return secretToken.equals(headerToken);
    }

    @Override
    public MergeRequestEvent parseEvent(String payload) {
        JSONObject json = JSONObject.parseObject(payload);

        String action = json.getString("action");
        JSONObject pullRequest = json.getJSONObject("pull_request");

        // 必须包含 pull_request 字段
        if (pullRequest == null) {
            log.debug("忽略非 pull_request 事件");
            return null;
        }

        // 检查操作类型
        if (!SUPPORTED_ACTIONS.contains(action)) {
            log.debug("忽略操作为 {} 的 Pull Request", action);
            return null;
        }

        String state = pullRequest.getString("state");

        // 只处理 open 状态的 PR
        if (!"open".equals(state)) {
            log.debug("忽略状态为 {} 的 Pull Request", state);
            return null;
        }

        // 映射为统一的 action 格式
        String normalizedAction = normalizeAction(action);

        // 提取分支信息
        JSONObject head = pullRequest.getJSONObject("head");
        JSONObject base = pullRequest.getJSONObject("base");
        String sourceBranch = head != null ? head.getString("ref") : null;
        String targetBranch = base != null ? base.getString("ref") : null;

        // 提取仓库信息
        JSONObject repository = json.getJSONObject("repository");
        Long repoId = repository != null ? repository.getLong("id") : null;
        String fullName = repository != null ? repository.getString("full_name") : null;
        String htmlUrl = repository != null ? repository.getString("html_url") : null;
        String repoName = repository != null ? repository.getString("name") : null;

        // 提取用户信息
        JSONObject sender = json.getJSONObject("sender");
        Long userId = sender != null ? sender.getLong("id") : null;
        String username = sender != null ? sender.getString("login") : null;

        // 构建统一的 MergeRequestEvent
        MergeRequestEvent.MergeRequestAttributes attrs = MergeRequestEvent.MergeRequestAttributes.builder()
                .id(pullRequest.getLong("id"))
                .iid(pullRequest.getInteger("number"))
                .title(pullRequest.getString("title"))
                .description(pullRequest.getString("body"))
                .sourceBranch(sourceBranch)
                .targetBranch(targetBranch)
                .state("opened") // 统一为 "opened" 格式
                .action(normalizedAction)
                .url(pullRequest.getString("html_url"))
                .sourceProjectId(repoId)
                .targetProjectId(repoId)
                .build();

        MergeRequestEvent.Project project = MergeRequestEvent.Project.builder()
                .id(repoId)
                .name(repoName)
                .pathWithNamespace(fullName)
                .webUrl(htmlUrl)
                .build();

        MergeRequestEvent.User user = MergeRequestEvent.User.builder()
                .id(userId)
                .name(username)
                .username(username)
                .build();

        MergeRequestEvent event = MergeRequestEvent.builder()
                .objectKind("merge_request")
                .eventType("merge_request")
                .objectAttributes(attrs)
                .project(project)
                .user(user)
                .build();

        log.info("解析 GitHub Pull Request 事件: repoId={}, number={}, action={}, title={}",
                repoId, attrs.getIid(), normalizedAction, attrs.getTitle());

        return event;
    }

    /**
     * 将 GitHub 的 action 映射为统一的内部 action 格式
     */
    private String normalizeAction(String githubAction) {
        return switch (githubAction) {
            case "opened" -> "open";
            case "synchronize" -> "update";
            case "reopened" -> "reopen";
            default -> githubAction;
        };
    }
}
