package cn.gyb.llm.cr.agent.skill;

/**
 * 技能元数据，仅包含标识和描述信息，不含完整规则内容。
 * <p>
 * 用于 Agent 第一阶段的技能选择：AI 先读取所有 Skill 的元数据，
 * 根据描述判断哪些 Skill 与当前 diff 相关，再按需拉取完整内容。
 *
 * @author guoyb
 * @date 2026-05-17
 */
public record SkillMeta(

        /** 技能唯一编码，用于后续按需拉取完整内容 */
        String skillCode,

        /** 技能显示名称 */
        String skillName,

        /** 技能覆盖范围描述，包含适用语言、框架和触发场景等信息 */
        String description
) {
}
