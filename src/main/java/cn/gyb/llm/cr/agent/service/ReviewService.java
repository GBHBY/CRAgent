package cn.gyb.llm.cr.agent.service;

import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;

/**
 * 代码审查服务接口
 * <p>
 * 核心审查服务，负责协调完整的代码审查流程，
 * 包括 AI Agent 执行、问题持久化、GitLab 交互、Notion 文档创建和钉钉通知等。
 */
public interface ReviewService {

    /**
     * 处理 GitLab Webhook 触发的合并请求事件
     *
     * @param event 合并请求事件
     */
    void handleMergeRequestEvent(MergeRequestEvent event);

    /**
     * 手动触发指定合并请求的代码审查
     *
     * @param projectId       GitLab 项目ID
     * @param mergeRequestIid 合并请求 IID
     * @return 审查结果
     */
    ReviewResult reviewMergeRequest(Long projectId, Long mergeRequestIid);

    /**
     * 触发项目全量代码扫描
     *
     * @param projectId GitLab 项目ID
     * @return 扫描结果
     */
    ReviewResult triggerFullScan(Long projectId);
}
