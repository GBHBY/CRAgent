package cn.gyb.llm.cr.agent.mapper;

import cn.gyb.llm.cr.agent.entity.db.ReviewIssueRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审查问题记录 Mapper 接口
 * <p>
 * 提供审查问题记录表 (cr_review_issue) 的数据库访问操作，
 * 继承 MyBatis-Plus 的 BaseMapper，支持基本的 CRUD 操作。
 */
@Mapper
public interface ReviewIssueRecordMapper extends BaseMapper<ReviewIssueRecord> {
}
