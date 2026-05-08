package cn.gyb.llm.cr.agent.mapper;

import cn.gyb.llm.cr.agent.entity.db.ReviewTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审查任务 Mapper 接口
 * <p>
 * 提供审查任务表 (cr_review_task) 的数据库访问操作，
 * 继承 MyBatis-Plus 的 BaseMapper，支持基本的 CRUD 操作。
 */
@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {
}
