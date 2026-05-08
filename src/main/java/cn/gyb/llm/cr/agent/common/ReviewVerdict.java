package cn.gyb.llm.cr.agent.common;

/**
 * 审查结论枚举
 * <p>
 * 定义代码审查的最终判定结果。
 */
public enum ReviewVerdict {

    /** 审查通过，未发现问题 */
    PASSED,

    /** 审查发现存在问题 */
    ISSUES_FOUND
}
