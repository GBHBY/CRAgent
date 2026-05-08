package cn.gyb.llm.cr.agent.skill;

/**
 * 技能内容加载器接口
 * <p>
 * 定义技能内容的加载策略，不同的实现类负责从不同来源（如PDF文件、文本文件等）
 * 加载和解析审查规则内容。
 */
public interface SkillLoader {

    /**
     * 判断当前加载器是否支持指定的技能类型
     *
     * @param type 技能类型
     * @return 如果支持则返回 true，否则返回 false
     */
    boolean supports(SkillType type);

    /**
     * 从指定路径加载技能内容
     *
     * @param sourcePath 内容来源路径（文件路径或URL）
     * @return 解析后的文本内容
     */
    String loadContent(String sourcePath);
}
