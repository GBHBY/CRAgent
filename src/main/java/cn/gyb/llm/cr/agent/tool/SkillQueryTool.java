package cn.gyb.llm.cr.agent.tool;

import cn.gyb.llm.cr.agent.skill.SkillMeta;
import cn.gyb.llm.cr.agent.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 编码规范查询工具，Agent 通过两阶段调用动态选择并加载合适的 Skill。
 * <p>
 * 第一阶段：调用 {@link #listAvailableSkills()} 获取所有 Skill 的元数据（编码、名称、描述），
 * AI 根据 diff 中涉及的语言和框架自主判断哪些 Skill 与当前审查相关。<br>
 * 第二阶段：调用 {@link #getSkillContent(String)} 按需拉取所选 Skill 的完整规则内容。
 * 全程从内存缓存读取，无数据库访问。
 *
 * @author guoyb
 * @date 2026-05-17
 */
@Slf4j
@Service
public class SkillQueryTool {

    private final SkillService skillService;

    public SkillQueryTool(SkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * 列出当前所有可用的审查规范 Skill 元数据。
     * <p>
     * 返回每个 Skill 的 skillCode、skillName 和 description，不含完整规则内容。
     * Agent 应先调用此方法，根据 diff 中出现的文件类型、框架等信息与 description 进行语义匹配，
     * 选出相关 Skill 后再调用 {@link #getSkillContent(String)} 获取完整规则。
     *
     * @return 以文本格式列出的所有可用 Skill 元数据，无可用 Skill 时返回提示信息
     */
    @Tool(description = "列出所有可用的审查规范 Skill 元数据（编码、名称、描述），用于根据 diff 的语言和框架选择合适的规范，调用此方法后再按需拉取完整内容")
    public String listAvailableSkills() {
        List<SkillMeta> metas = skillService.listSkillMeta();
        if (metas.isEmpty()) {
            log.warn("当前注册表中无可用 Skill");
            return "当前没有可用的审查规范 Skill。";
        }

        String result = metas.stream()
                .map(m -> String.format("skillCode: %s\nskillName: %s\ndescription: %s",
                        m.skillCode(), m.skillName(), m.description()))
                .collect(Collectors.joining("\n\n---\n\n"));

        log.info("列出可用 Skill，共 {} 个", metas.size());
        return result;
    }

    /**
     * 根据 skillCode 获取指定审查规范的完整规则内容。
     * <p>
     * 应在调用 {@link #listAvailableSkills()} 并确认目标 skillCode 后调用此方法。
     * 内容直接从内存缓存读取，无数据库访问。
     *
     * @param skillCode 目标技能的唯一编码（从 listAvailableSkills 的返回结果中获取）
     * @return 对应 Skill 的完整规则内容，skillCode 不存在时返回提示信息
     */
    @Tool(description = "根据 skillCode 获取指定审查规范的完整规则内容，在 listAvailableSkills 后按需调用")
    public String getSkillContent(@ToolParam(description = "技能唯一编码，从 listAvailableSkills 返回结果中获取") String skillCode) {
        log.info("获取 Skill 完整内容: skillCode={}", skillCode);
        return skillService.getSkillContentFromCache(skillCode)
                .orElseGet(() -> {
                    log.warn("Skill 不存在或未加载到缓存: skillCode={}", skillCode);
                    return String.format("未找到 skillCode 为 '%s' 的审查规范，请通过 listAvailableSkills 确认可用编码。", skillCode);
                });
    }
}
