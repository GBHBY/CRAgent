package cn.gyb.llm.cr.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能注册表
 * <p>
 * 管理所有已加载的审查规则技能，提供技能的注册、查询、刷新和移除功能。
 * 内部使用 ConcurrentHashMap 缓存技能内容，支持并发访问。
 */
@Slf4j
@Component
public class SkillRegistry {

    /**
     * 缓存的技能记录
     *
     * @param version 内容版本号
     * @param content 技能文本内容
     */
    private record CachedSkill(int version, String content) {
    }

    /** 技能缓存，key 为技能编码，value 为缓存的技能内容 */
    private final ConcurrentHashMap<String, CachedSkill> cache = new ConcurrentHashMap<>();

    /**
     * 注册一个技能到注册表中
     *
     * @param skillCode 技能编码
     * @param version   内容版本号
     * @param content   技能文本内容
     */
    public void register(String skillCode, int version, String content) {
        cache.put(skillCode, new CachedSkill(version, content));
        log.info("注册技能: {}, version={}", skillCode, version);
    }

    /**
     * 获取所有已注册技能的合并规则文本
     *
     * @return 所有技能内容拼接后的规则文本，如果注册表为空则返回空字符串
     */
    public String getRulesText() {
        if (cache.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, CachedSkill> entry : cache.entrySet()) {
            sb.append("=== ").append(entry.getKey()).append(" ===\n");
            sb.append(entry.getValue().content()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 检查指定技能是否需要刷新
     *
     * @param skillCode     技能编码
     * @param currentVersion 当前数据库中的版本号
     * @return 如果缓存中不存在或版本号不一致则返回 true，表示需要刷新
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
