package cn.gyb.llm.cr.agent.service;

import cn.gyb.llm.cr.agent.entity.record.GitLabDiff;

import java.util.List;

/**
 * 差异解析服务接口
 * <p>
 * 负责将 GitLab 合并请求的 diff JSON 数据解析为结构化的 GitLabDiff 对象列表，
 * 供代码审查流程使用。
 */
public interface DiffParserService {

    /**
     * 解析 GitLab changes API 返回的 diff JSON 字符串
     *
     * @param diffJson GitLab API 返回的 changes 数组 JSON 字符串
     * @return 解析后的 GitLabDiff 对象列表
     */
    List<GitLabDiff> parseDiff(String diffJson);
}
