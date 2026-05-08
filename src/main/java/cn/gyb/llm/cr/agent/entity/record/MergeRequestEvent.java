package cn.gyb.llm.cr.agent.entity.record;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合并请求事件
 * <p>
 * 表示从 GitLab Webhook 接收到的合并请求事件对象，
 * 包含事件类型、合并请求属性、项目信息和操作用户信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeRequestEvent {

    /** 事件对象类型 (如 merge_request) */
    private String objectKind;

    /** 事件类型 (如 merge_request) */
    private String eventType;

    /** 合并请求属性信息 */
    private MergeRequestAttributes objectAttributes;

    /** 所属项目信息 */
    private Project project;

    /** 触发事件的用户信息 */
    private User user;

    /**
     * 合并请求属性
     * <p>
     * 包含合并请求的详细属性信息，如标题、分支、状态等。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MergeRequestAttributes {

        /** 合并请求全局ID */
        private Long id;

        /** 合并请求在项目内的IID */
        private Integer iid;

        /** 合并请求标题 */
        private String title;

        /** 合并请求描述 */
        private String description;

        /** 源分支名称 */
        private String sourceBranch;

        /** 目标分支名称 */
        private String targetBranch;

        /** 合并请求状态 (opened/closed/merged等) */
        private String state;

        /** 触发的动作类型 (open/update/merge/close等) */
        private String action;

        /** 合并请求页面URL */
        private String url;

        /** 源项目ID */
        private Long sourceProjectId;

        /** 目标项目ID */
        private Long targetProjectId;
    }

    /**
     * GitLab 项目信息
     * <p>
     * 包含合并请求所属项目的基本信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Project {

        /** 项目ID */
        private Long id;

        /** 项目名称 */
        private String name;

        /** 项目路径（含命名空间，如 group/project） */
        private String pathWithNamespace;

        /** 项目Web页面URL */
        private String webUrl;
    }

    /**
     * GitLab 用户信息
     * <p>
     * 包含触发合并请求事件的用户信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {

        /** 用户ID */
        private Long id;

        /** 用户显示名称 */
        private String name;

        /** 用户名 */
        private String username;
    }
}
