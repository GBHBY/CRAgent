package cn.gyb.llm.cr.agent.controller;

import cn.gyb.llm.cr.agent.common.AgentResponse;
import cn.gyb.llm.cr.agent.entity.db.Skill;
import cn.gyb.llm.cr.agent.mapper.SkillMapper;
import cn.gyb.llm.cr.agent.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 编码规范管理控制器，提供 Skill 的 CRUD 和刷新接口。
 * <p>
 * 支持技能的查询、新增、修改、删除以及内容刷新操作，
 * 刷新操作会从配置的来源重新加载技能内容。
 */
@Slf4j
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    /** 技能服务 */
    @Autowired
    private SkillService skillService;

    /** 技能数据库 Mapper */
    @Autowired
    private SkillMapper skillMapper;

    /**
     * 查询所有技能列表。
     *
     * @return 所有技能的 JSON 数组
     */
    @GetMapping
    public ResponseEntity<String> listAllSkills() {
        List<Skill> skills = skillService.listAllSkills();
        return ResponseEntity.ok(AgentResponse.json(skills));
    }

    /**
     * 根据 ID 查询单个技能详情。
     *
     * @param id 技能 ID
     * @return 技能详情的 JSON 对象
     */
    @GetMapping("/{id}")
    public ResponseEntity<String> getById(@PathVariable Long id) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) {
            return ResponseEntity.ok(AgentResponse.error("技能未找到: " + id));
        }
        return ResponseEntity.ok(AgentResponse.json(skill));
    }

    /**
     * 新增技能。
     *
     * @param skill 技能对象（请求体 JSON）
     * @return 创建后的技能对象
     */
    @PostMapping
    public ResponseEntity<String> addSkill(@RequestBody Skill skill) {
        Skill created = skillService.addSkill(skill);
        return ResponseEntity.ok(AgentResponse.json(created));
    }

    /**
     * 更新技能信息。
     *
     * @param id    技能 ID
     * @param skill 包含更新字段的技能对象（请求体 JSON）
     * @return 更新后的技能对象
     */
    @PutMapping("/{id}")
    public ResponseEntity<String> updateSkill(@PathVariable Long id, @RequestBody Skill skill) {
        Skill updated = skillService.updateSkill(id, skill);
        return ResponseEntity.ok(AgentResponse.json(updated));
    }

    /**
     * 删除技能。
     *
     * @param id 技能 ID
     * @return 删除成功的提示信息
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteSkill(@PathVariable Long id) {
        skillService.deleteSkill(id);
        return ResponseEntity.ok(AgentResponse.text("技能已删除: " + id));
    }

    /**
     * 刷新单个技能的内容，重新从来源加载。
     *
     * @param id 技能 ID
     * @return 刷新成功的提示信息
     */
    @PostMapping("/{id}/refresh")
    public ResponseEntity<String> refreshSkillContent(@PathVariable Long id) {
        skillService.refreshSkillContent(id);
        return ResponseEntity.ok(AgentResponse.text("技能已刷新: " + id));
    }

    /**
     * 刷新所有已启用技能的内容。
     *
     * @return 批量刷新成功的提示信息
     */
    @PostMapping("/refresh-all")
    public ResponseEntity<String> refreshAllSkills() {
        skillService.refreshAllSkills();
        return ResponseEntity.ok(AgentResponse.text("所有技能已刷新"));
    }
}
