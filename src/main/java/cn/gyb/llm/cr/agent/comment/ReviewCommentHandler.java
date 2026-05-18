package cn.gyb.llm.cr.agent.comment;

import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.entity.record.MergeRequestEvent;

/**
 * 审查评论回写策略接口
 * <p>
 * 不同代码托管平台（GitLab、GitHub等）实现此接口，
 * 将 AI 代码审查结论回写至对应平台的 MR/PR 评论区。
 */
public interface ReviewCommentHandler {

    /**
     * 当前处理器支持的平台类型
     *
     * @return 平台类型标识，如 "gitlab"、"github"
     */
    String platformType();

    /**
     * 将审查结果作为评论回写到对应平台的 MR/PR
     *
     * @param event  合并请求事件（包含项目ID、MR/PR编号等上下文信息）
     * @param result AI 代码审查结果
     */
    void postReviewComment(MergeRequestEvent event, ReviewResult result);
}
