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
 * 审查任务实体
 * <p>
 * 对应数据库表 cr_review_task，记录每次代码审查任务的详细信息，
 * 包括关联的合并请求、分支信息、审查状态和结果摘要等。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("cr_review_task")
public class ReviewTask {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务唯一标识 */
    private String taskId;

    /** GitLab 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 合并请求 IID */
    private Integer mergeRequestIid;

    /** 源分支名称 */
    private String sourceBranch;

    /** 目标分支名称 */
    private String targetBranch;

    /** 触发类型 (WEBHOOK/MANUAL/FULL_SCAN) */
    private String triggerType;

    /** 任务状态 */
    private String status;

    /** 发现的问题总数 */
    private Integer totalIssues;

    /** 审查结果摘要 */
    private String summary;

    /** Notion 文档页面URL */
    private String notionPageUrl;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
