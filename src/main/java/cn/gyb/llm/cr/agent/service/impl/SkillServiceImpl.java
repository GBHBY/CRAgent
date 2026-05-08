package cn.gyb.llm.cr.agent.service.impl;

import cn.gyb.llm.cr.agent.entity.db.Skill;
import cn.gyb.llm.cr.agent.mapper.SkillMapper;
import cn.gyb.llm.cr.agent.service.SkillService;
import cn.gyb.llm.cr.agent.skill.SkillLoader;
import cn.gyb.llm.cr.agent.skill.SkillRegistry;
import cn.gyb.llm.cr.agent.skill.SkillType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 技能（编码规范）服务实现，提供技能的 CRUD 操作和内容刷新功能。
 * <p>
 * 管理技能的创建、更新、删除和刷新，通过 SkillLoader 从不同来源加载技能内容，
 * 并通过 SkillRegistry 注册到内存中供 Agent 查询使用。
 */
@Slf4j
@Service
public class SkillServiceImpl implements SkillService {

    /** 技能数据库 Mapper */
    @Autowired
    private SkillMapper skillMapper;

    /** 技能注册中心，管理内存中的活跃技能 */
    @Autowired
    private SkillRegistry skillRegistry;

    /** 技能加载器列表，支持从不同来源加载技能内容 */
    @Autowired
    private List<SkillLoader> skillLoaders;

    /**
     * 查询所有技能列表。
     *
     * @return 所有技能的列表
     */
    @Override
    public List<Skill> listAllSkills() {
        return skillMapper.selectList(null);
    }

    /**
     * 根据技能编码查询技能。
     *
     * @param skillCode 技能编码
     * @return 匹配的技能对象，未找到返回 null
     */
    @Override
    public Skill getSkillByCode(String skillCode) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Skill::getSkillCode, skillCode);
        return skillMapper.selectOne(wrapper);
    }

    /**
     * 添加新技能，从配置的来源加载内容并注册到技能注册中心。
     *
     * @param skill 技能对象（需包含 skillType 和 sourcePath）
     * @return 添加后的技能对象（含生成的 ID 和加载的内容）
     */
    @Override
    public Skill addSkill(Skill skill) {
        SkillLoader loader = findLoader(SkillType.valueOf(skill.getSkillType()));
        String content = loader.loadContent(skill.getSourcePath());

        skill.setContent(content);
        skill.setVersion(1);
        skill.setEnabled(1);
        skill.setLastRefreshedAt(LocalDateTime.now());
        skill.setCreatedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());

        skillMapper.insert(skill);

        if (skill.getEnabled() == 1) {
            skillRegistry.register(skill.getSkillCode(), skill.getVersion(), content);
        }

        log.info("添加技能: {}, type={}, version={}", skill.getSkillCode(), skill.getSkillType(), skill.getVersion());
        return skill;
    }

    /**
     * 更新技能信息，如果技能已启用则自动刷新内容。
     *
     * @param id    技能 ID
     * @param skill 包含更新字段的技能对象
     * @return 更新后的技能对象
     */
    @Override
    public Skill updateSkill(Long id, Skill skill) {
        Skill existing = skillMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("技能未找到: " + id);
        }

        existing.setSkillCode(skill.getSkillCode());
        existing.setSkillName(skill.getSkillName());
        existing.setSkillType(skill.getSkillType());
        existing.setDescription(skill.getDescription());
        existing.setSourcePath(skill.getSourcePath());
        existing.setEnabled(skill.getEnabled());
        existing.setUpdatedAt(LocalDateTime.now());

        skillMapper.updateById(existing);

        if (existing.getEnabled() == 1) {
            refreshSkillContent(id);
        } else {
            skillRegistry.remove(existing.getSkillCode());
        }

        log.info("更新技能: {}", existing.getSkillCode());
        return existing;
    }

    /**
     * 删除技能，同时从技能注册中心移除。
     *
     * @param id 技能 ID
     */
    @Override
    public void deleteSkill(Long id) {
        Skill existing = skillMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("技能未找到: " + id);
        }
        skillMapper.deleteById(id);
        skillRegistry.remove(existing.getSkillCode());
        log.info("删除技能: {}", existing.getSkillCode());
    }

    /**
     * 刷新单个技能的内容，重新从来源加载并更新版本号。
     *
     * @param id 技能 ID
     */
    @Override
    public void refreshSkillContent(Long id) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) {
            throw new RuntimeException("技能未找到: " + id);
        }

        SkillLoader loader = findLoader(SkillType.valueOf(skill.getSkillType()));
        String content = loader.loadContent(skill.getSourcePath());

        int newVersion = skill.getVersion() + 1;
        skill.setContent(content);
        skill.setVersion(newVersion);
        skill.setLastRefreshedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());

        skillMapper.updateById(skill);

        if (skill.getEnabled() == 1) {
            skillRegistry.register(skill.getSkillCode(), newVersion, content);
        }

        log.info("刷新技能: {}, 新版本={}", skill.getSkillCode(), newVersion);
    }

    /**
     * 刷新所有已启用技能的内容，仅在版本号变更时才实际刷新。
     */
    @Override
    public void refreshAllSkills() {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Skill::getEnabled, 1);
        List<Skill> enabledSkills = skillMapper.selectList(wrapper);

        for (Skill skill : enabledSkills) {
            try {
                if (skillRegistry.needsRefresh(skill.getSkillCode(), skill.getVersion())) {
                    refreshSkillContent(skill.getId());
                }
            } catch (Exception e) {
                log.error("刷新技能失败 {}: {}", skill.getSkillCode(), e.getMessage(), e);
            }
        }

        log.info("刷新所有技能完成，检查了 {} 个启用的技能", enabledSkills.size());
    }

    /**
     * 获取所有活跃编码规范的文本内容。
     *
     * @return 所有已注册编码规则的合并文本
     */
    @Override
    public String getAllActiveRulesAsText() {
        return skillRegistry.getRulesText();
    }

    /**
     * 根据技能类型查找对应的加载器。
     *
     * @param type 技能类型
     * @return 支持该类型的加载器
     * @throws RuntimeException 未找到匹配的加载器时抛出
     */
    private SkillLoader findLoader(SkillType type) {
        for (SkillLoader loader : skillLoaders) {
            if (loader.supports(type)) {
                return loader;
            }
        }
        throw new RuntimeException("未找到技能类型对应的加载器: " + type);
    }
}
