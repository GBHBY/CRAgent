package cn.gyb.llm.cr.agent.service;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

/**
 * GitHub API 服务
 * <p>
 * 封装与 GitHub REST API 的交互，提供 Pull Request diff 获取、评论发布等功能。
 *
 * @author guoyb
 * @date 2026-05-16
 */
@Slf4j
@Service
public class GitHubApiService {

    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final String DIFF_ACCEPT = "application/vnd.github.v3.diff";

    /** GitHub API 基础地址 */
    @Value("${github.api.url:https://api.github.com}")
    private String githubApiUrl;

    /** GitHub Personal Access Token 或 GitHub App Token */
    @Value("${github.api.token}")
    private String token;

    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();
        log.info("GitHubApiService 初始化完成，基础 URL: {}", githubApiUrl);
    }

    /**
     * 构建通用 JSON 请求头（用于写操作，如发评论）。
     */
    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("X-GitHub-Api-Version", GITHUB_API_VERSION);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/vnd.github+json");
        return headers;
    }

    /**
     * 构建 diff 专用请求头，Accept 设置为 vnd.github.v3.diff 以获取原始 diff 文本。
     */
    private HttpHeaders buildDiffHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("X-GitHub-Api-Version", GITHUB_API_VERSION);
        headers.set("Accept", DIFF_ACCEPT);
        return headers;
    }

    /**
     * 获取 Pull Request 的 unified diff 内容。
     * <p>
     * 接口：GET /repos/{owner}/{repo}/pulls/{pull_number}
     * 响应体为原始 unified diff 文本，直接返回给调用方。
     *
     * @param owner      仓库所有者
     * @param repo       仓库名称
     * @param pullNumber Pull Request 编号
     * @return 原始 unified diff 文本
     */
    public String fetchPullRequestDiff(String owner, String repo, Integer pullNumber) {
        String url = String.format("%s/repos/%s/%s/pulls/%d", githubApiUrl, owner, repo, pullNumber);
        log.info("获取 GitHub PR diff: owner={}, repo={}, pullNumber={}", owner, repo, pullNumber);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildDiffHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            String diff = response.getBody();
            log.info("获取 PR diff 成功: owner={}, repo={}, pullNumber={}, 字符数={}",
                    owner, repo, pullNumber, diff != null ? diff.length() : 0);
            return diff;
        } catch (Exception e) {
            log.error("获取 GitHub PR diff 失败: owner={}, repo={}, pullNumber={}", owner, repo, pullNumber, e);
            throw new RuntimeException(
                    String.format("获取 PR diff 失败，%s/%s #%d: %s", owner, repo, pullNumber, e.getMessage()), e);
        }
    }

    /**
     * 在 Pull Request 上发布 Issue 评论。
     * <p>
     * 接口：POST /repos/{owner}/{repo}/issues/{issue_number}/comments
     * Pull Request 编号与 Issue 编号相同，可复用此接口。
     *
     * @param owner      仓库所有者
     * @param repo       仓库名称
     * @param pullNumber Pull Request 编号
     * @param body       评论内容（支持 Markdown）
     */
    public void createPullRequestComment(String owner, String repo, Integer pullNumber, String body) {
        String url = String.format("%s/repos/%s/%s/issues/%d/comments", githubApiUrl, owner, repo, pullNumber);
        log.info("在 GitHub PR 发布评论: owner={}, repo={}, pullNumber={}", owner, repo, pullNumber);

        try {
            String requestBody = JSONObject.toJSONString(Collections.singletonMap("body", body));
            HttpEntity<String> entity = new HttpEntity<>(requestBody, buildJsonHeaders());
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("发布 PR 评论成功: owner={}, repo={}, pullNumber={}", owner, repo, pullNumber);
        } catch (Exception e) {
            log.error("发布 GitHub PR 评论失败: owner={}, repo={}, pullNumber={}", owner, repo, pullNumber, e);
            throw new RuntimeException(
                    String.format("发布 PR 评论失败，%s/%s #%d: %s", owner, repo, pullNumber, e.getMessage()), e);
        }
    }
}
