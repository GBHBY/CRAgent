package cn.gyb.llm.cr.agent.tool;

import cn.gyb.llm.cr.agent.service.GitLabApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * GitLab Diff 获取工具，Agent 可通过此工具获取合并请求的代码变更内容。
 * <p>
 * 调用 GitLab REST API 获取指定 MR 的 diff 信息，返回变更文件的详细列表（JSON 数组格式）。
 */
@Slf4j
@Service
public class GitLabDiffTool {

    /** GitLab API 服务，用于与 GitLab 交互 */
    private final GitLabApiService gitLabApiService;

    /**
     * 构造 GitLab Diff 工具。
     *
     * @param gitLabApiService GitLab API 服务实例
     */
    public GitLabDiffTool(GitLabApiService gitLabApiService) {
        this.gitLabApiService = gitLabApiService;
    }

    /**
     * 获取指定 MR 的 diff 内容，返回变更文件的详细信息列表（JSON 数组格式）。
     *
     * @param projectId        GitLab 项目 ID
     * @param mergeRequestIid  Merge Request 的 IID（项目内编号）
     * @return 变更文件的 JSON 数组字符串，失败时返回包含 error 字段的 JSON
     */
    @Tool(description = "获取指定 MR 的 diff 内容，返回变更文件的详细信息列表（JSON 数组格式）")
    public String fetchDiff(
            @ToolParam(description = "GitLab 项目 ID") Long projectId,
            @ToolParam(description = "Merge Request 的 IID（项目内编号）") Long mergeRequestIid) {
        log.info("获取 diff: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);
        try {
            return gitLabApiService.fetchMergeRequestDiff(projectId, mergeRequestIid);
        } catch (Exception e) {
            log.error("获取 diff 失败: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid, e);
            return "{\"error\": \"获取 diff 失败: " + e.getMessage() + "\"}";
        }
    }
}
