package cn.gyb.llm.cr.agent.service;

import cn.gyb.llm.cr.agent.entity.db.Skill;
import cn.gyb.llm.cr.agent.skill.SkillMeta;

import java.util.List;
import java.util.Optional;

/**
 * 技能管理服务接口
 * <p>
 * 提供审查规则技能的增删改查和内容刷新等功能。
 * 技能是代码审查规则的定义来源，支持从不同类型的文件中加载审查规则。
 */
public interface SkillService {

    /**
     * 获取所有技能列表
     *
     * @return 技能列表
     */
    List<Skill> listAllSkills();

    /**
     * 根据技能编码获取技能信息
     *
     * @param skillCode 技能编码
     * @return 技能信息，如果不存在则返回 null
     */
    Skill getSkillByCode(String skillCode);

    /**
     * 添加新技能
     *
     * @param skill 技能信息
     * @return 添加后的技能信息（含生成的ID）
     */
    Skill addSkill(Skill skill);

    /**
     * 更新技能信息
     *
     * @param id    技能主键ID
     * @param skill 更新后的技能信息
     * @return 更新后的技能信息
     */
    Skill updateSkill(Long id, Skill skill);

    /**
     * 删除指定技能
     *
     * @param id 技能主键ID
     */
    void deleteSkill(Long id);

    /**
     * 刷新指定技能的内容
     * <p>
     * 从配置的来源路径重新加载和解析技能内容，并更新版本号。
     *
     * @param id 技能主键ID
     */
    void refreshSkillContent(Long id);

    /**
     * 刷新所有技能的内容
     * <p>
     * 遍历所有已启用的技能，重新加载和解析其内容。
     */
    void refreshAllSkills();

    /**
     * 获取所有已启用技能的合并规则文本（兼容旧调用路径，不推荐新代码使用）
     * <p>
     * 将所有已启用且已加载的技能内容拼接为一段完整的审查规则文本。
     *
     * @return 合并后的规则文本
     */
    String getAllActiveRulesAsText();

    /**
     * 从内存缓存中获取所有技能的元数据列表，供 Agent 第一阶段语义选择使用。
     * <p>
     * 仅返回 skillCode、skillName、description，不含完整规则内容，全程无数据库访问。
     *
     * @return 技能元数据列表，无已注册技能时返回空列表
     */
    List<SkillMeta> listSkillMeta();

    /**
     * 根据技能编码从内存缓存中获取完整规则内容，供 Agent 第二阶段按需加载。
     * <p>
     * 全程无数据库访问，技能不存在时返回空 Optional。
     *
     * @param skillCode 技能编码
     * @return 技能完整规则内容
     */
    Optional<String> getSkillContentFromCache(String skillCode);
}
