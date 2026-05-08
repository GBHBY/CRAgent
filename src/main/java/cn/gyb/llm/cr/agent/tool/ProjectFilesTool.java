package cn.gyb.llm.cr.agent.tool;

import cn.gyb.llm.cr.agent.service.GitLabApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目文件操作工具，Agent 可通过此工具列举和读取项目文件。
 * <p>
 * 支持列出指定分支下的所有文件路径，以及获取单个文件的原始内容。
 */
@Slf4j
@Service
public class ProjectFilesTool {

    /** GitLab API 服务，用于与 GitLab 交互 */
    private final GitLabApiService gitLabApiService;

    /**
     * 构造项目文件工具。
     *
     * @param gitLabApiService GitLab API 服务实例
     */
    public ProjectFilesTool(GitLabApiService gitLabApiService) {
        this.gitLabApiService = gitLabApiService;
    }

    /**
     * 列出指定项目和分支下的所有文件路径，返回文件路径列表。
     *
     * @param projectId GitLab 项目 ID
     * @param branch    分支名称
     * @return 文件路径列表的格式化文本，失败时返回包含 error 字段的 JSON
     */
    @Tool(description = "列出指定项目和分支下的所有文件路径，返回文件路径列表")
    public String listFiles(
            @ToolParam(description = "GitLab 项目 ID") Long projectId,
            @ToolParam(description = "分支名称") String branch) {
        log.info("列出文件: projectId={}, branch={}", projectId, branch);
        try {
            List<String> files = gitLabApiService.listProjectFiles(projectId, branch);
            if (files.isEmpty()) {
                return "项目中没有找到任何文件。";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("共找到 ").append(files.size()).append(" 个文件：\n");
            for (String file : files) {
                sb.append("- ").append(file).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("列出文件失败: projectId={}, branch={}", projectId, branch, e);
            return "{\"error\": \"列出文件失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取指定项目中某个文件的原始内容。
     *
     * @param projectId GitLab 项目 ID
     * @param filePath  文件路径
     * @param branch    分支名称
     * @return 文件的原始内容字符串，失败时返回包含 error 字段的 JSON
     */
    @Tool(description = "获取指定项目中某个文件的原始内容")
    public String getFileContent(
            @ToolParam(description = "GitLab 项目 ID") Long projectId,
            @ToolParam(description = "文件路径") String filePath,
            @ToolParam(description = "分支名称") String branch) {
        log.info("获取文件内容: projectId={}, filePath={}, branch={}", projectId, filePath, branch);
        try {
            return gitLabApiService.fetchFileContent(projectId, filePath, branch);
        } catch (Exception e) {
            log.error("获取文件内容失败: projectId={}, filePath={}, branch={}", projectId, filePath, branch, e);
            return "{\"error\": \"获取文件内容失败: " + e.getMessage() + "\"}";
        }
    }
}
