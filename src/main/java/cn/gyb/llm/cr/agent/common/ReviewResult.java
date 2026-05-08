package cn.gyb.llm.cr.agent.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 代码审查结果
 * <p>
 * 封装一次代码审查的完整结果，包括审查结论、总结摘要和发现的问题列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResult {

    /** 审查结论 (PASSED/ISSUES_FOUND) */
    private ReviewVerdict verdict;

    /** 审查总结 */
    private String summary;

    /** 发现的问题列表 */
    private List<ReviewIssue> issues;
}
