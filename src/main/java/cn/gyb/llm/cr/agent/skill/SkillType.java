package cn.gyb.llm.cr.agent.skill;

/**
 * 技能类型枚举
 * <p>
 * 定义审查规则技能的内容来源类型，决定内容的加载和解析方式。
 */
public enum SkillType {

    /** PDF 文档类型，从 PDF 文件中解析审查规则 */
    PDF,

    /** 纯文本类型，直接使用文本内容作为审查规则 */
    TEXT,

    /** 自定义类型，通过自定义逻辑加载审查规则 */
    CUSTOM
}
