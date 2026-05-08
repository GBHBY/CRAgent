package cn.gyb.llm.cr.agent.service;

import java.util.List;
import java.util.Map;

/**
 * GitLab API service for interacting with GitLab REST API v4.
 */
public interface GitLabApiService {

    /**
     * Fetch the diff/changes of a merge request.
     *
     * @param projectId        the project ID
     * @param mergeRequestIid  the merge request IID
     * @return JSON string of the changes array
     */
    String fetchMergeRequestDiff(Long projectId, Long mergeRequestIid);

    /**
     * Approve a merge request.
     *
     * @param projectId        the project ID
     * @param mergeRequestIid  the merge request IID
     */
    void approveMergeRequest(Long projectId, Long mergeRequestIid);

    /**
     * Create a discussion on a merge request.
     *
     * @param projectId        the project ID
     * @param mergeRequestIid  the merge request IID
     * @param body             the discussion body
     */
    void createMergeRequestDiscussion(Long projectId, Long mergeRequestIid, String body);

    /**
     * Create a note (comment) on a merge request.
     *
     * @param projectId        the project ID
     * @param mergeRequestIid  the merge request IID
     * @param body             the note body
     */
    void createMergeRequestNote(Long projectId, Long mergeRequestIid, String body);

    /**
     * Create a new branch in the repository.
     *
     * @param projectId  the project ID
     * @param branch     the new branch name
     * @param ref        the ref (commit SHA or branch name) to create from
     * @return JSON string of the created branch
     */
    String createBranch(Long projectId, String branch, String ref);

    /**
     * Create a commit with multiple file actions.
     *
     * @param projectId     the project ID
     * @param branch        the target branch
     * @param commitMessage the commit message
     * @param actions       list of actions, each action is a Map with keys: action, filePath, content
     */
    void createCommit(Long projectId, String branch, String commitMessage, List<Map<String, String>> actions);

    /**
     * Create a new merge request.
     *
     * @param projectId     the project ID
     * @param sourceBranch  the source branch
     * @param targetBranch  the target branch
     * @param title         the merge request title
     * @param description   the merge request description
     * @return map containing the created merge request details
     */
    Map<String, Object> createMergeRequest(Long projectId, String sourceBranch, String targetBranch,
                                            String title, String description);

    /**
     * List all files in a project repository for a given branch.
     *
     * @param projectId  the project ID
     * @param branch     the branch name
     * @return list of file paths
     */
    List<String> listProjectFiles(Long projectId, String branch);

    /**
     * Fetch the raw content of a file in the repository.
     *
     * @param projectId  the project ID
     * @param filePath   the file path
     * @param branch     the branch name
     * @return the file content as a string
     */
    String fetchFileContent(Long projectId, String filePath, String branch);

    /**
     * Get merge request details.
     *
     * @param projectId        the project ID
     * @param mergeRequestIid  the merge request IID
     * @return map containing the merge request details
     */
    Map<String, Object> getMergeRequest(Long projectId, Long mergeRequestIid);
}
