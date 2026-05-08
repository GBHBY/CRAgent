package cn.gyb.llm.cr.agent.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码审查问题记录
 * <p>
 * 表示代码审查过程中发现的单个问题，包含文件位置、严重程度、问题描述及修复建议等信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewIssue {

    /** 涉及的文件路径 */
    private String file;

    /** 问题所在行号 */
    private Integer line;

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
}
