package cn.gyb.llm.cr.agent.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审查问题记录实体
 * <p>
 * 对应数据库表 cr_review_issue，记录代码审查过程中发现的每个问题的详细信息，
 * 包括文件位置、严重程度、规则编号、问题描述、修复建议和AI生成的修复代码。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("cr_review_issue")
public class ReviewIssueRecord {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的审查任务ID */
    private Long reviewTaskId;

    /** 问题所在的文件路径 */
    private String filePath;

    /** 问题所在的行号 */
    private Integer lineNumber;

    /** 严重程度 (BLOCKER/CRITICAL/MAJOR/MINOR/INFO) */
    private String severity;

    /** 违反的规则编号 */
    private String ruleId;

    /** 问题描述 */
    private String message;

    /** 修复建议 */
    private String suggestion;

    /** AI 生成的修复代码 */
    private String fixCode;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
