package cn.gyb.llm.cr.agent.tool;

import cn.gyb.llm.cr.agent.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * 编码规范查询工具，Agent 可通过此工具查询当前活跃的编码规范。
 * <p>
 * 从技能注册中心获取所有已启用编码规则的文本内容，供代码审查时参考。
 */
@Slf4j
@Service
public class SkillQueryTool {

    /** 技能服务，用于查询编码规范 */
    private final SkillService skillService;

    /**
     * 构造编码规范查询工具。
     *
     * @param skillService 技能服务实例
     */
    public SkillQueryTool(SkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * 查询当前活跃的编码规范和规则，返回完整的规则文本。
     *
     * @param category 规则类别过滤（可选，不传则返回全部规则）
     * @return 活跃编码规范的完整文本，无激活规则时返回提示信息
     */
    @Tool(description = "查询当前活跃的编码规范和规则，返回完整的规则文本")
    public String queryRules(@ToolParam(description = "规则类别过滤（可选，不传则返回全部规则）") String category) {
        log.info("查询规则，category={}", category);
        String rulesText = skillService.getAllActiveRulesAsText();
        if (rulesText == null || rulesText.isBlank()) {
            return "当前没有激活的编码规范。";
        }
        return rulesText;
    }
}
