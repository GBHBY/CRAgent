package cn.gyb.llm.cr.agent.service;

import cn.gyb.llm.cr.agent.common.ReviewResult;

/**
 * 钉钉通知服务接口
 * <p>
 * 通过钉钉机器人 Webhook 发送代码审查结果通知，
 * 支持审查通过、发现问题、审查失败和全量扫描等不同场景的消息推送。
 */
public interface DingTalkNotifyService {

    /**
     * 发送审查通过的通知
     *
     * @param mrTitle      合并请求标题
     * @param mrUrl        合并请求URL
     * @param author       合并请求作者
     * @param sourceBranch 源分支名称
     * @param targetBranch 目标分支名称
     * @param summary      审查摘要
     */
    void sendReviewPassedNotification(String mrTitle, String mrUrl, String author,
                                      String sourceBranch, String targetBranch, String summary);

    /**
     * 发送审查发现问题的通知
     *
     * @param mrTitle       合并请求标题
     * @param mrUrl         合并请求URL
     * @param author        合并请求作者
     * @param sourceBranch  源分支名称
     * @param targetBranch  目标分支名称
     * @param result        审查结果（包含问题列表）
     * @param fixMrUrl      自动修复的合并请求URL（可能为空）
     * @param notionPageUrl Notion 审查文档URL（可能为空）
     */
    void sendIssuesFoundNotification(String mrTitle, String mrUrl, String author,
                                     String sourceBranch, String targetBranch,
                                     ReviewResult result, String fixMrUrl, String notionPageUrl);

    /**
     * 发送审查处理失败的通知
     *
     * @param mrTitle 合并请求标题
     * @param mrUrl   合并请求URL
     * @param error   错误信息
     */
    void sendReviewFailedNotification(String mrTitle, String mrUrl, String error);

    /**
     * 发送项目全量扫描结果的通知
     *
     * @param projectName  项目名称
     * @param result       扫描结果
     * @param notionPageUrl Notion 扫描文档URL（可能为空）
     */
    void sendFullScanNotification(String projectName, ReviewResult result, String notionPageUrl);
}
