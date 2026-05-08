package cn.gyb.llm.cr.agent.service.impl;

import cn.gyb.llm.cr.agent.service.GitLabApiService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 RestTemplate 的 GitLab API 服务实现。
 * <p>
 * 与 GitLab REST API v4 端点交互，提供合并请求操作、文件操作、分支操作等功能。
 */
@Slf4j
@Service
public class GitLabApiServiceImpl implements GitLabApiService {

    /** GitLab API 基础地址 */
    @Value("${gitlab.api.url}")
    private String gitLabApiUrl;

    /** GitLab API 私有访问令牌 */
    @Value("${gitlab.api.private-token}")
    private String privateToken;

    /** HTTP 请求客户端 */
    private RestTemplate restTemplate;

    /**
     * 初始化 RestTemplate 实例。
     */
    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();
        log.info("GitLabApiService 初始化完成，基础 URL: {}", gitLabApiUrl);
    }

    /**
     * 构建包含私有令牌和 JSON 内容类型的 HTTP 请求头。
     *
     * @return HTTP 请求头
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", privateToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * 构建完整的 GitLab API 请求 URL。
     *
     * @param path API 路径模板
     * @param args 路径模板参数
     * @return 完整的请求 URL
     */
    private String buildUrl(String path, Object... args) {
        return gitLabApiUrl + String.format(path, args);
    }

    /**
     * 获取合并请求的 diff 变更内容。
     *
     * @param projectId        项目 ID
     * @param mergeRequestIid  合并请求 IID
     * @return 变更列表的 JSON 数组字符串
     */
    @Override
    public String fetchMergeRequestDiff(Long projectId, Long mergeRequestIid) {
        String url = buildUrl("/api/v4/projects/%d/merge_requests/%d/changes", projectId, mergeRequestIid);
        log.info("获取合并请求 diff: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JSONObject jsonResponse = JSON.parseObject(response.getBody());
            JSONArray changes = jsonResponse.getJSONArray("changes");
            log.info("获取到 {} 个变更，合并请求: {}", changes.size(), mergeRequestIid);
            return changes.toJSONString();
        } catch (Exception e) {
            log.error("获取合并请求 diff 失败: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid, e);
            throw new RuntimeException(
                    String.format("获取合并请求 diff 失败，项目 %d, MR %d: %s",
                            projectId, mergeRequestIid, e.getMessage()), e);
        }
    }

    /**
     * 审批通过指定的合并请求。
     *
     * @param projectId        项目 ID
     * @param mergeRequestIid  合并请求 IID
     */
    @Override
    public void approveMergeRequest(Long projectId, Long mergeRequestIid) {
        String url = buildUrl("/api/v4/projects/%d/merge_requests/%d/approve", projectId, mergeRequestIid);
        log.info("审批合并请求: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("审批合并请求成功: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);
        } catch (Exception e) {
            log.error("审批合并请求失败: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid, e);
            throw new RuntimeException(
                    String.format("审批合并请求失败，项目 %d, MR %d: %s",
                            projectId, mergeRequestIid, e.getMessage()), e);
        }
    }

    /**
     * 在合并请求中创建讨论（Discussion）。
     *
     * @param projectId        项目 ID
     * @param mergeRequestIid  合并请求 IID
     * @param body             讨论内容
     */
    @Override
    public void createMergeRequestDiscussion(Long projectId, Long mergeRequestIid, String body) {
        String url = buildUrl("/api/v4/projects/%d/merge_requests/%d/discussions", projectId, mergeRequestIid);
        log.info("创建合并请求讨论: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);

        try {
            Map<String, String> requestBody = Collections.singletonMap("body", body);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, buildHeaders());
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("创建讨论成功: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);
        } catch (Exception e) {
            log.error("创建合并请求讨论失败: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid, e);
            throw new RuntimeException(
                    String.format("创建讨论失败，项目 %d, MR %d: %s",
                            projectId, mergeRequestIid, e.getMessage()), e);
        }
    }

    /**
     * 在合并请求中添加备注（Note）。
     *
     * @param projectId        项目 ID
     * @param mergeRequestIid  合并请求 IID
     * @param body             备注内容
     */
    @Override
    public void createMergeRequestNote(Long projectId, Long mergeRequestIid, String body) {
        String url = buildUrl("/api/v4/projects/%d/merge_requests/%d/notes", projectId, mergeRequestIid);
        log.info("创建合并请求备注: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);

        try {
            Map<String, String> requestBody = Collections.singletonMap("body", body);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, buildHeaders());
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("创建备注成功: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);
        } catch (Exception e) {
            log.error("创建合并请求备注失败: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid, e);
            throw new RuntimeException(
                    String.format("创建备注失败，项目 %d, MR %d: %s",
                            projectId, mergeRequestIid, e.getMessage()), e);
        }
    }

    /**
     * 在项目中创建新分支。
     *
     * @param projectId 项目 ID
     * @param branch    新分支名称
     * @param ref       基于的源引用（分支名或提交 SHA）
     * @return 创建结果的 JSON 字符串
     */
    @Override
    public String createBranch(Long projectId, String branch, String ref) {
        String url = buildUrl("/api/v4/projects/%d/repository/branches", projectId);
        log.info("创建分支: projectId={}, branch={}, ref={}", projectId, branch, ref);

        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("branch", branch);
            requestBody.put("ref", ref);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, buildHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("创建分支成功: projectId={}, branch={}", projectId, branch);
            return response.getBody();
        } catch (Exception e) {
            log.error("创建分支失败: projectId={}, branch={}, ref={}", projectId, branch, ref, e);
            throw new RuntimeException(
                    String.format("创建分支 '%s' 失败，项目 %d: %s", branch, projectId, e.getMessage()), e);
        }
    }

    /**
     * 在指定分支上创建提交，支持批量文件操作。
     *
     * @param projectId    项目 ID
     * @param branch       目标分支
     * @param commitMessage 提交信息
     * @param actions      文件操作列表（create、update、delete 等）
     */
    @Override
    public void createCommit(Long projectId, String branch, String commitMessage, List<Map<String, String>> actions) {
        String url = buildUrl("/api/v4/projects/%d/repository/commits", projectId);
        log.info("创建提交: projectId={}, branch={}, 操作数={}", projectId, branch, actions.size());

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("branch", branch);
            requestBody.put("commit_message", commitMessage);
            requestBody.put("actions", actions);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, buildHeaders());
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("创建提交成功: projectId={}, branch={}", projectId, branch);
        } catch (Exception e) {
            log.error("创建提交失败: projectId={}, branch={}", projectId, branch, e);
            throw new RuntimeException(
                    String.format("创建提交失败，项目 %d，分支 '%s': %s",
                            projectId, branch, e.getMessage()), e);
        }
    }

    /**
     * 创建合并请求。
     *
     * @param projectId    项目 ID
     * @param sourceBranch 源分支
     * @param targetBranch 目标分支
     * @param title        合并请求标题
     * @param description  合并请求描述
     * @return 创建结果的 Map（包含 iid、web_url 等字段）
     */
    @Override
    public Map<String, Object> createMergeRequest(Long projectId, String sourceBranch, String targetBranch,
                                                   String title, String description) {
        String url = buildUrl("/api/v4/projects/%d/merge_requests", projectId);
        log.info("创建合并请求: projectId={}, sourceBranch={}, targetBranch={}", projectId, sourceBranch, targetBranch);

        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("source_branch", sourceBranch);
            requestBody.put("target_branch", targetBranch);
            requestBody.put("title", title);
            requestBody.put("description", description);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, buildHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
            log.info("创建合并请求成功: projectId={}, sourceBranch={}, targetBranch={}", projectId, sourceBranch, targetBranch);
            return result;
        } catch (Exception e) {
            log.error("创建合并请求失败: projectId={}, sourceBranch={}, targetBranch={}", projectId, sourceBranch, targetBranch, e);
            throw new RuntimeException(
                    String.format("创建合并请求失败，项目 %d: %s", projectId, e.getMessage()), e);
        }
    }

    /**
     * 递归列出项目和分支下的所有文件路径。
     *
     * @param projectId 项目 ID
     * @param branch    分支名称
     * @return 文件路径列表
     */
    @Override
    public List<String> listProjectFiles(Long projectId, String branch) {
        String url = buildUrl("/api/v4/projects/%d/repository/tree?ref=%s&recursive=true&per_page=10000",
                projectId, URLEncoder.encode(branch, StandardCharsets.UTF_8));
        log.info("列出项目文件: projectId={}, branch={}", projectId, branch);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JSONArray tree = JSON.parseArray(response.getBody());
            List<String> filePaths = new ArrayList<>(tree.size());
            for (int i = 0; i < tree.size(); i++) {
                JSONObject item = tree.getJSONObject(i);
                if ("blob".equals(item.getString("type"))) {
                    filePaths.add(item.getString("path"));
                }
            }
            log.info("列出 {} 个文件，项目 {}，分支: {}", filePaths.size(), projectId, branch);
            return filePaths;
        } catch (Exception e) {
            log.error("列出项目文件失败: projectId={}, branch={}", projectId, branch, e);
            throw new RuntimeException(
                    String.format("列出项目文件失败，项目 %d，分支 '%s': %s",
                            projectId, branch, e.getMessage()), e);
        }
    }

    /**
     * 获取项目中指定文件的原始内容。
     *
     * @param projectId 项目 ID
     * @param filePath  文件路径
     * @param branch    分支名称
     * @return 文件原始内容字符串
     */
    @Override
    public String fetchFileContent(Long projectId, String filePath, String branch) {
        String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8);
        String url = buildUrl("/api/v4/projects/%d/repository/files/%s/raw?ref=%s",
                projectId, encodedPath, URLEncoder.encode(branch, StandardCharsets.UTF_8));
        log.info("获取文件内容: projectId={}, filePath={}, branch={}", projectId, filePath, branch);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            log.debug("获取文件内容成功: projectId={}, filePath={}", projectId, filePath);
            return response.getBody();
        } catch (Exception e) {
            log.error("获取文件内容失败: projectId={}, filePath={}, branch={}", projectId, filePath, branch, e);
            throw new RuntimeException(
                    String.format("获取文件内容失败，文件 '%s'，项目 %d，分支 '%s': %s",
                            filePath, projectId, branch, e.getMessage()), e);
        }
    }

    /**
     * 获取合并请求详情。
     *
     * @param projectId        项目 ID
     * @param mergeRequestIid  合并请求 IID
     * @return 合并请求详情的 Map
     */
    @Override
    public Map<String, Object> getMergeRequest(Long projectId, Long mergeRequestIid) {
        String url = buildUrl("/api/v4/projects/%d/merge_requests/%d", projectId, mergeRequestIid);
        log.info("获取合并请求: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
            log.info("获取合并请求成功: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);
            return result;
        } catch (Exception e) {
            log.error("获取合并请求失败: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid, e);
            throw new RuntimeException(
                    String.format("获取合并请求失败，项目 %d, MR %d: %s",
                            projectId, mergeRequestIid, e.getMessage()), e);
        }
    }
}
