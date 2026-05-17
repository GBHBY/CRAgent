package cn.gyb.llm.cr.agent.webhook;

import cn.gyb.llm.cr.agent.dto.github.GitHubPullRequestEvent;
import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;
import cn.gyb.llm.cr.agent.service.GitHubApiService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

/**
 * GitHub Webhook 处理器
 * <p>
 * 解析 GitHub 平台的 Pull Request Webhook 事件，
 * 拉取对应 PR 的 unified diff 并保存到本地 diff 目录，
 * 最终将本地文件路径写入统一的 MergeRequestEvent 模型。
 */
@Slf4j
@Component
public class GitHubWebhookHandler implements WebhookHandler {

    private static final Set<String> SUPPORTED_ACTIONS = Set.of("opened", "synchronize", "reopened");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private GitHubApiService gitHubApiService;

    /** diff 文件本地存储目录，默认为项目根目录下的 diff 文件夹 */
    @Value("${github.diff.local-dir:./diff}")
    private String diffLocalDir;

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
        GitHubPullRequestEvent event = JSON.parseObject(payload, GitHubPullRequestEvent.class);

        GitHubPullRequestEvent.PullRequest pr = event.getPullRequest();

        if (pr == null) {
            log.debug("忽略非 pull_request 事件");
            return null;
        }

        String action = event.getAction();

        if (!SUPPORTED_ACTIONS.contains(action)) {
            log.debug("忽略操作为 {} 的 Pull Request", action);
            return null;
        }

        if (!"open".equals(pr.getState())) {
            log.debug("忽略状态为 {} 的 Pull Request", pr.getState());
            return null;
        }

        String normalizedAction = normalizeAction(action);

        String sourceBranch = Optional.ofNullable(pr.getHead()).map(GitHubPullRequestEvent.BranchRef::getRef).orElse(null);
        String targetBranch = Optional.ofNullable(pr.getBase()).map(GitHubPullRequestEvent.BranchRef::getRef).orElse(null);

        GitHubPullRequestEvent.Repository repo = event.getRepository();
        Long repoId = Optional.ofNullable(repo).map(GitHubPullRequestEvent.Repository::getId).orElse(null);
        String fullName = Optional.ofNullable(repo).map(GitHubPullRequestEvent.Repository::getFullName).orElse(null);
        String htmlUrl = Optional.ofNullable(repo).map(GitHubPullRequestEvent.Repository::getHtmlUrl).orElse(null);
        String repoName = Optional.ofNullable(repo).map(GitHubPullRequestEvent.Repository::getName).orElse(null);

        GitHubPullRequestEvent.GithubUser sender = event.getSender();
        Long userId = Optional.ofNullable(sender).map(GitHubPullRequestEvent.GithubUser::getId).orElse(null);
        String username = Optional.ofNullable(sender).map(GitHubPullRequestEvent.GithubUser::getLogin).orElse(null);

        // 拉取 diff 并保存到本地文件
        String diffFilePath = fetchAndSaveDiff(fullName, repoName, pr.getNumber());

        MergeRequestEvent.MergeRequestAttributes attrs = MergeRequestEvent.MergeRequestAttributes.builder()
                .id(pr.getId())
                .iid(pr.getNumber())
                .title(pr.getTitle())
                .description(pr.getBody())
                .sourceBranch(sourceBranch)
                .targetBranch(targetBranch)
                .state("opened")
                .action(normalizedAction)
                .url(pr.getHtmlUrl())
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

        MergeRequestEvent mergeRequestEvent = MergeRequestEvent.builder()
                .objectKind("merge_request")
                .eventType("merge_request")
                .objectAttributes(attrs)
                .project(project)
                .user(user)
                .diffFilePath(diffFilePath)
                .build();

        log.info("解析 GitHub Pull Request 事件: repoId={}, number={}, action={}, title={}, diffFilePath={}",
                repoId, attrs.getIid(), normalizedAction, attrs.getTitle(), diffFilePath);

        return mergeRequestEvent;
    }

    /**
     * 拉取 PR diff 并保存到本地文件。
     * <p>
     * 文件命名规则：{repo}_{yyyyMMdd}_{pullNumber}.diff
     * 存储路径：{diffLocalDir}/{fileName}
     *
     * @param fullName   仓库全名（owner/repo 格式），用于解析 owner 和 repo
     * @param repoName   仓库名，用于文件命名
     * @param pullNumber Pull Request 编号
     * @return 本地文件路径字符串；若拉取失败则返回 null
     */
    private String fetchAndSaveDiff(String fullName, String repoName, Integer pullNumber) {
        if (fullName == null || !fullName.contains("/") || pullNumber == null) {
            log.warn("无法拉取 diff：fullName={}, pullNumber={}", fullName, pullNumber);
            return null;
        }

        String[] parts = fullName.split("/", 2);
        String owner = parts[0];
        String repo = parts[1];

        try {
            String diffContent = gitHubApiService.fetchPullRequestDiff(owner, repo, pullNumber);

            // 确保目录存在
            Path dirPath = Paths.get(diffLocalDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                log.info("创建 diff 目录: {}", dirPath.toAbsolutePath());
            }

            // {repo}_{yyyyMMdd}_{pullNumber}.diff
            String date = LocalDate.now().format(DATE_FMT);
            String fileName = String.format("%s_%s_%d.diff", repoName, date, pullNumber);
            Path filePath = dirPath.resolve(fileName);

            Files.writeString(filePath, diffContent, StandardCharsets.UTF_8);
            log.info("diff 文件已保存: {}", filePath.toAbsolutePath());

            return filePath.toString();
        } catch (IOException e) {
            log.error("保存 diff 文件失败: owner={}, repo={}, pullNumber={}", owner, repo, pullNumber, e);
            return null;
        } catch (Exception e) {
            log.error("拉取 GitHub PR diff 失败: owner={}, repo={}, pullNumber={}", owner, repo, pullNumber, e);
            return null;
        }
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
