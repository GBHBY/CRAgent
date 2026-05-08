package cn.gyb.llm.cr.agent.service;

import cn.gyb.llm.cr.agent.common.ReviewResult;

/**
 * Notion 文档服务接口
 * <p>
 * 提供在 Notion 中创建代码审查文档的功能，
 * 将审查结果以结构化的方式记录到 Notion 页面中，便于团队成员查阅和追踪。
 */
public interface NotionDocumentService {

    /**
     * 创建代码审查文档
     *
     * @param projectId    GitLab 项目ID
     * @param projectName  项目名称
     * @param mrTitle      合并请求标题
     * @param mrUrl        合并请求URL
     * @param result       审查结果
     * @param sourceBranch 源分支名称
     * @param targetBranch 目标分支名称
     * @return 创建的 Notion 文档URL，如果创建失败则返回空字符串
     */
    String createReviewDocument(Long projectId, String projectName, String mrTitle, String mrUrl,
                                ReviewResult result, String sourceBranch, String targetBranch);
}
