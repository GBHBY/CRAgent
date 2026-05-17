package cn.gyb.llm.cr.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 技能注册表
 * <p>
 * 管理所有已加载的审查规则技能，提供技能的注册、查询、刷新和移除功能。
 * 内部使用 ConcurrentHashMap 缓存完整技能信息（含元数据与规则内容），支持并发访问。
 * Agent 通过两阶段调用使用：先查元数据列表选择合适的 Skill，再按需拉取完整内容，
 * 全程无需访问数据库。
 */
@Slf4j
@Component
public class SkillRegistry {

    /**
     * 内存中缓存的完整技能记录
     *
     * @param version     内容版本号（与数据库版本对比，用于判断是否需要刷新）
     * @param skillName   技能显示名称
     * @param description 技能覆盖范围描述
     * @param content     完整规则文本内容
     */
    private record CachedSkill(int version, String skillName, String description, String content) {
    }

    /** 技能缓存，key 为技能编码，value 为完整的缓存技能对象 */
    private final ConcurrentHashMap<String, CachedSkill> cache = new ConcurrentHashMap<>();

    /**
     * 注册一个技能到注册表中，同时缓存元数据与完整内容
     *
     * @param skillCode   技能编码
     * @param version     内容版本号
     * @param skillName   技能显示名称
     * @param description 技能覆盖范围描述
     * @param content     完整规则文本内容
     */
    public void register(String skillCode, int version, String skillName, String description, String content) {
        cache.put(skillCode, new CachedSkill(version, skillName, description, content));
        log.info("注册技能: {}, version={}", skillCode, version);
    }

    /**
     * 获取所有已注册技能的元数据列表（不含完整内容），供 Agent 第一阶段选择使用
     *
     * @return 所有已缓存技能的元数据列表，注册表为空时返回空列表
     */
    public List<SkillMeta> listSkillMeta() {
        if (cache.isEmpty()) {
            return Collections.emptyList();
        }
        return cache.entrySet().stream()
                .map(e -> new SkillMeta(e.getKey(), e.getValue().skillName(), e.getValue().description()))
                .collect(Collectors.toList());
    }

    /**
     * 根据技能编码获取对应的完整规则内容，供 Agent 第二阶段按需加载
     *
     * @param skillCode 技能编码
     * @return 技能完整规则内容，技能不存在时返回空 Optional
     */
    public Optional<String> getContent(String skillCode) {
        return Optional.ofNullable(cache.get(skillCode)).map(CachedSkill::content);
    }

    /**
     * 获取所有已注册技能的合并规则文本（兼容旧调用路径，不推荐新代码使用）
     *
     * @return 所有技能内容拼接后的规则文本，如果注册表为空则返回空字符串
     */
    public String getRulesText() {
        if (cache.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : cache.entrySet()) {
            sb.append("=== ").append(entry.getKey()).append(" ===\n");
            sb.append(entry.getValue().content()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 检查指定技能是否需要刷新
     *
     * @param skillCode      技能编码
     * @param currentVersion 当前数据库中的版本号
     * @return 缓存中不存在或版本号不一致时返回 true
     */
    public boolean needsRefresh(String skillCode, int currentVersion) {
        CachedSkill cached = cache.get(skillCode);
        return cached == null || cached.version() != currentVersion;
    }

    /**
     * 从注册表中移除指定技能
     *
     * @param skillCode 要移除的技能编码
     */
    public void remove(String skillCode) {
        cache.remove(skillCode);
        log.info("从注册表中移除技能: {}", skillCode);
    }

    /**
     * 清空注册表中的所有技能
     */
    public void clear() {
        cache.clear();
        log.info("已清空注册表中的所有技能");
    }
}
