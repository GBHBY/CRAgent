package cn.gyb.llm.cr.agent.service.impl;

import cn.gyb.llm.cr.agent.entity.record.GitLabDiff;
import cn.gyb.llm.cr.agent.service.DiffParserService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Diff 解析服务实现，基于 fastjson2 解析 GitLab changes API 的 JSON 数据。
 * <p>
 * 将 GitLab changes API 返回的 JSON 数组解析为结构化的 GitLabDiff 对象列表，
 * 提取文件路径、变更类型和 diff 内容等信息。
 */
@Slf4j
@Service
public class DiffParserServiceImpl implements DiffParserService {

    /**
     * 解析 diff JSON 字符串为 GitLabDiff 对象列表。
     *
     * @param diffJson GitLab changes API 返回的 JSON 数组字符串
     * @return 解析后的 GitLabDiff 对象列表，输入为空时返回空列表
     * @throws RuntimeException JSON 解析失败时抛出
     */
    @Override
    public List<GitLabDiff> parseDiff(String diffJson) {
        log.info("解析 diff JSON，长度={}", diffJson != null ? diffJson.length() : 0);

        if (diffJson == null || diffJson.isBlank()) {
            log.warn("diff JSON 为空，返回空列表");
            return List.of();
        }

        try {
            JSONArray changesArray = JSON.parseArray(diffJson);
            List<GitLabDiff> diffs = new ArrayList<>(changesArray.size());

            for (int i = 0; i < changesArray.size(); i++) {
                JSONObject changeObj = changesArray.getJSONObject(i);
                GitLabDiff diff = GitLabDiff.builder()
                        .oldPath(changeObj.getString("old_path"))
                        .newPath(changeObj.getString("new_path"))
                        .newFile(changeObj.getBooleanValue("new_file", false))
                        .deletedFile(changeObj.getBooleanValue("deleted_file", false))
                        .renamedFile(changeObj.getBooleanValue("renamed_file", false))
                        .diff(changeObj.getString("diff"))
                        .build();
                diffs.add(diff);
            }

            log.info("解析了 {} 个 diff 条目", diffs.size());
            return diffs;
        } catch (Exception e) {
            log.error("解析 diff JSON 失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析 diff JSON 失败: " + e.getMessage(), e);
        }
    }
}
