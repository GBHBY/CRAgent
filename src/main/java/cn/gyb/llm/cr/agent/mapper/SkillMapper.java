package cn.gyb.llm.cr.agent.mapper;

import cn.gyb.llm.cr.agent.entity.db.Skill;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 技能配置 Mapper 接口
 * <p>
 * 提供技能配置表 (cr_skill) 的数据库访问操作，
 * 继承 MyBatis-Plus 的 BaseMapper，支持基本的 CRUD 操作。
 */
@Mapper
public interface SkillMapper extends BaseMapper<Skill> {
}
