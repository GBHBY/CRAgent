package cn.gyb.llm.cr.agent.dto.github;

import com.alibaba.fastjson2.annotation.JSONField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GitHub Pull Request Webhook 回调请求体 DTO
 *
 * @author guoyb
 * @date 2026-05-16
 */
@Data
@NoArgsConstructor
@Schema(description = "GitHub Pull Request Webhook 事件")
public class GitHubPullRequestEvent {

    @Schema(description = "操作类型，如 opened/synchronize/reopened/closed")
    private String action;

    @Schema(description = "Pull Request 编号")
    private Integer number;

    @JSONField(name = "pull_request")
    @Schema(description = "Pull Request 详细信息")
    private PullRequest pullRequest;

    @Schema(description = "所属仓库信息")
    private Repository repository;

    @Schema(description = "触发事件的用户")
    private GithubUser sender;

    // -------------------------------------------------------------------------
    // PullRequest
    // -------------------------------------------------------------------------

    @Data
    @NoArgsConstructor
    @Schema(description = "Pull Request 详情")
    public static class PullRequest {

        @Schema(description = "PR API URL")
        private String url;

        @Schema(description = "PR 全局唯一 ID")
        private Long id;

        @JSONField(name = "node_id")
        @Schema(description = "PR Node ID")
        private String nodeId;

        @JSONField(name = "html_url")
        @Schema(description = "PR 页面 URL")
        private String htmlUrl;

        @JSONField(name = "diff_url")
        @Schema(description = "PR diff URL")
        private String diffUrl;

        @JSONField(name = "patch_url")
        @Schema(description = "PR patch URL")
        private String patchUrl;

        @JSONField(name = "issue_url")
        @Schema(description = "PR 对应 issue API URL")
        private String issueUrl;

        @Schema(description = "PR 在仓库内的编号")
        private Integer number;

        @Schema(description = "PR 状态，如 open/closed")
        private String state;

        @Schema(description = "是否已锁定")
        private Boolean locked;

        @Schema(description = "PR 标题")
        private String title;

        @Schema(description = "PR 创建者")
        private GithubUser user;

        @Schema(description = "PR 描述正文")
        private String body;

        @JSONField(name = "created_at")
        @Schema(description = "创建时间")
        private String createdAt;

        @JSONField(name = "updated_at")
        @Schema(description = "更新时间")
        private String updatedAt;

        @JSONField(name = "closed_at")
        @Schema(description = "关闭时间")
        private String closedAt;

        @JSONField(name = "merged_at")
        @Schema(description = "合并时间")
        private String mergedAt;

        @JSONField(name = "merge_commit_sha")
        @Schema(description = "合并提交 SHA")
        private String mergeCommitSha;

        @Schema(description = "指定的审查人（单个）")
        private GithubUser assignee;

        @Schema(description = "指定的审查人列表")
        private List<GithubUser> assignees;

        @JSONField(name = "requested_reviewers")
        @Schema(description = "请求的 reviewer 列表")
        private List<GithubUser> requestedReviewers;

        @JSONField(name = "requested_teams")
        @Schema(description = "请求的团队列表")
        private List<Object> requestedTeams;

        @Schema(description = "标签列表")
        private List<Object> labels;

        @Schema(description = "里程碑")
        private Object milestone;

        @Schema(description = "是否为草稿")
        private Boolean draft;

        @JSONField(name = "commits_url")
        @Schema(description = "提交列表 API URL")
        private String commitsUrl;

        @JSONField(name = "review_comments_url")
        @Schema(description = "Review 评论列表 API URL")
        private String reviewCommentsUrl;

        @JSONField(name = "review_comment_url")
        @Schema(description = "单条 Review 评论 API URL 模板")
        private String reviewCommentUrl;

        @JSONField(name = "comments_url")
        @Schema(description = "评论列表 API URL")
        private String commentsUrl;

        @JSONField(name = "statuses_url")
        @Schema(description = "状态列表 API URL")
        private String statusesUrl;

        @Schema(description = "源分支信息")
        private BranchRef head;

        @Schema(description = "目标分支信息")
        private BranchRef base;

        @JSONField(name = "_links")
        @Schema(description = "相关资源链接")
        private Links links;

        @JSONField(name = "author_association")
        @Schema(description = "PR 作者与仓库的关联身份，如 OWNER/CONTRIBUTOR")
        private String authorAssociation;

        @JSONField(name = "auto_merge")
        @Schema(description = "自动合并配置")
        private Object autoMerge;

        @JSONField(name = "active_lock_reason")
        @Schema(description = "锁定原因")
        private String activeLockReason;

        @Schema(description = "是否已合并")
        private Boolean merged;

        @Schema(description = "是否可合并")
        private Boolean mergeable;

        @Schema(description = "是否可 rebase")
        private Boolean rebaseable;

        @JSONField(name = "mergeable_state")
        @Schema(description = "可合并状态，如 clean/dirty/unknown")
        private String mergeableState;

        @JSONField(name = "merged_by")
        @Schema(description = "执行合并的用户")
        private GithubUser mergedBy;

        @Schema(description = "评论数量")
        private Integer comments;

        @JSONField(name = "review_comments")
        @Schema(description = "Review 评论数量")
        private Integer reviewComments;

        @JSONField(name = "maintainer_can_modify")
        @Schema(description = "维护者是否可以修改此 PR")
        private Boolean maintainerCanModify;

        @Schema(description = "提交数量")
        private Integer commits;

        @Schema(description = "新增行数")
        private Integer additions;

        @Schema(description = "删除行数")
        private Integer deletions;

        @JSONField(name = "changed_files")
        @Schema(description = "变更文件数量")
        private Integer changedFiles;
    }

    // -------------------------------------------------------------------------
    // BranchRef
    // -------------------------------------------------------------------------

    @Data
    @NoArgsConstructor
    @Schema(description = "分支引用信息（head / base）")
    public static class BranchRef {

        @Schema(description = "标签，格式为 owner:branch")
        private String label;

        @Schema(description = "分支名称")
        private String ref;

        @Schema(description = "提交 SHA")
        private String sha;

        @Schema(description = "分支所属用户")
        private GithubUser user;

        @Schema(description = "所属仓库信息")
        private Repository repo;
    }

    // -------------------------------------------------------------------------
    // Repository
    // -------------------------------------------------------------------------

    @Data
    @NoArgsConstructor
    @Schema(description = "GitHub 仓库信息")
    public static class Repository {

        @Schema(description = "仓库 ID")
        private Long id;

        @JSONField(name = "node_id")
        @Schema(description = "仓库 Node ID")
        private String nodeId;

        @Schema(description = "仓库名称")
        private String name;

        @JSONField(name = "full_name")
        @Schema(description = "仓库全名，格式为 owner/repo")
        private String fullName;

        @JSONField(name = "private")
        @Schema(description = "是否为私有仓库")
        private Boolean isPrivate;

        @Schema(description = "仓库所有者")
        private GithubUser owner;

        @JSONField(name = "html_url")
        @Schema(description = "仓库页面 URL")
        private String htmlUrl;

        @Schema(description = "仓库描述")
        private String description;

        @Schema(description = "是否为 fork 仓库")
        private Boolean fork;

        @Schema(description = "仓库 API URL")
        private String url;

        @JSONField(name = "forks_url")
        @Schema(description = "fork 列表 API URL")
        private String forksUrl;

        @JSONField(name = "keys_url")
        @Schema(description = "部署密钥 API URL 模板")
        private String keysUrl;

        @JSONField(name = "collaborators_url")
        @Schema(description = "协作者 API URL 模板")
        private String collaboratorsUrl;

        @JSONField(name = "teams_url")
        @Schema(description = "团队 API URL")
        private String teamsUrl;

        @JSONField(name = "hooks_url")
        @Schema(description = "Webhook API URL")
        private String hooksUrl;

        @JSONField(name = "issue_events_url")
        @Schema(description = "Issue 事件 API URL 模板")
        private String issueEventsUrl;

        @JSONField(name = "events_url")
        @Schema(description = "事件 API URL")
        private String eventsUrl;

        @JSONField(name = "assignees_url")
        @Schema(description = "指派人 API URL 模板")
        private String assigneesUrl;

        @JSONField(name = "branches_url")
        @Schema(description = "分支 API URL 模板")
        private String branchesUrl;

        @JSONField(name = "tags_url")
        @Schema(description = "标签 API URL")
        private String tagsUrl;

        @JSONField(name = "blobs_url")
        @Schema(description = "Blob API URL 模板")
        private String blobsUrl;

        @JSONField(name = "git_tags_url")
        @Schema(description = "Git 标签 API URL 模板")
        private String gitTagsUrl;

        @JSONField(name = "git_refs_url")
        @Schema(description = "Git 引用 API URL 模板")
        private String gitRefsUrl;

        @JSONField(name = "trees_url")
        @Schema(description = "目录树 API URL 模板")
        private String treesUrl;

        @JSONField(name = "statuses_url")
        @Schema(description = "状态 API URL 模板")
        private String statusesUrl;

        @JSONField(name = "languages_url")
        @Schema(description = "语言统计 API URL")
        private String languagesUrl;

        @JSONField(name = "stargazers_url")
        @Schema(description = "Star 用户列表 API URL")
        private String stargazersUrl;

        @JSONField(name = "contributors_url")
        @Schema(description = "贡献者列表 API URL")
        private String contributorsUrl;

        @JSONField(name = "subscribers_url")
        @Schema(description = "订阅者列表 API URL")
        private String subscribersUrl;

        @JSONField(name = "subscription_url")
        @Schema(description = "订阅 API URL")
        private String subscriptionUrl;

        @JSONField(name = "commits_url")
        @Schema(description = "提交列表 API URL 模板")
        private String commitsUrl;

        @JSONField(name = "git_commits_url")
        @Schema(description = "Git 提交 API URL 模板")
        private String gitCommitsUrl;

        @JSONField(name = "comments_url")
        @Schema(description = "评论 API URL 模板")
        private String commentsUrl;

        @JSONField(name = "issue_comment_url")
        @Schema(description = "Issue 评论 API URL 模板")
        private String issueCommentUrl;

        @JSONField(name = "contents_url")
        @Schema(description = "文件内容 API URL 模板")
        private String contentsUrl;

        @JSONField(name = "compare_url")
        @Schema(description = "比较 API URL 模板")
        private String compareUrl;

        @JSONField(name = "merges_url")
        @Schema(description = "合并 API URL")
        private String mergesUrl;

        @JSONField(name = "archive_url")
        @Schema(description = "归档下载 API URL 模板")
        private String archiveUrl;

        @JSONField(name = "downloads_url")
        @Schema(description = "下载 API URL")
        private String downloadsUrl;

        @JSONField(name = "issues_url")
        @Schema(description = "Issue API URL 模板")
        private String issuesUrl;

        @JSONField(name = "pulls_url")
        @Schema(description = "Pull Request API URL 模板")
        private String pullsUrl;

        @JSONField(name = "milestones_url")
        @Schema(description = "里程碑 API URL 模板")
        private String milestonesUrl;

        @JSONField(name = "notifications_url")
        @Schema(description = "通知 API URL 模板")
        private String notificationsUrl;

        @JSONField(name = "labels_url")
        @Schema(description = "标签 API URL 模板")
        private String labelsUrl;

        @JSONField(name = "releases_url")
        @Schema(description = "发布版本 API URL 模板")
        private String releasesUrl;

        @JSONField(name = "deployments_url")
        @Schema(description = "部署 API URL")
        private String deploymentsUrl;

        @JSONField(name = "created_at")
        @Schema(description = "仓库创建时间")
        private String createdAt;

        @JSONField(name = "updated_at")
        @Schema(description = "仓库更新时间")
        private String updatedAt;

        @JSONField(name = "pushed_at")
        @Schema(description = "最近推送时间")
        private String pushedAt;

        @JSONField(name = "git_url")
        @Schema(description = "Git 协议 clone URL")
        private String gitUrl;

        @JSONField(name = "ssh_url")
        @Schema(description = "SSH 协议 clone URL")
        private String sshUrl;

        @JSONField(name = "clone_url")
        @Schema(description = "HTTPS 协议 clone URL")
        private String cloneUrl;

        @JSONField(name = "svn_url")
        @Schema(description = "SVN 协议 URL")
        private String svnUrl;

        @Schema(description = "仓库主页")
        private String homepage;

        @Schema(description = "仓库大小（KB）")
        private Integer size;

        @JSONField(name = "stargazers_count")
        @Schema(description = "Star 数量")
        private Integer stargazersCount;

        @JSONField(name = "watchers_count")
        @Schema(description = "Watch 数量")
        private Integer watchersCount;

        @Schema(description = "主要编程语言")
        private String language;

        @JSONField(name = "has_issues")
        @Schema(description = "是否开启 Issues")
        private Boolean hasIssues;

        @JSONField(name = "has_projects")
        @Schema(description = "是否开启 Projects")
        private Boolean hasProjects;

        @JSONField(name = "has_downloads")
        @Schema(description = "是否开启 Downloads")
        private Boolean hasDownloads;

        @JSONField(name = "has_wiki")
        @Schema(description = "是否开启 Wiki")
        private Boolean hasWiki;

        @JSONField(name = "has_pages")
        @Schema(description = "是否开启 GitHub Pages")
        private Boolean hasPages;

        @JSONField(name = "has_discussions")
        @Schema(description = "是否开启 Discussions")
        private Boolean hasDiscussions;

        @JSONField(name = "forks_count")
        @Schema(description = "fork 数量")
        private Integer forksCount;

        @JSONField(name = "mirror_url")
        @Schema(description = "镜像仓库 URL")
        private String mirrorUrl;

        @Schema(description = "是否已归档")
        private Boolean archived;

        @Schema(description = "是否已禁用")
        private Boolean disabled;

        @JSONField(name = "open_issues_count")
        @Schema(description = "未关闭 Issue 数量")
        private Integer openIssuesCount;

        @Schema(description = "许可证信息")
        private Object license;

        @JSONField(name = "allow_forking")
        @Schema(description = "是否允许 fork")
        private Boolean allowForking;

        @JSONField(name = "is_template")
        @Schema(description = "是否为模板仓库")
        private Boolean isTemplate;

        @JSONField(name = "web_commit_signoff_required")
        @Schema(description = "是否要求 Web 提交签名")
        private Boolean webCommitSignoffRequired;

        @JSONField(name = "has_pull_requests")
        @Schema(description = "是否开启 Pull Requests")
        private Boolean hasPullRequests;

        @JSONField(name = "pull_request_creation_policy")
        @Schema(description = "PR 创建策略")
        private String pullRequestCreationPolicy;

        @Schema(description = "仓库 Topics 标签列表")
        private List<String> topics;

        @Schema(description = "仓库可见性，如 public/private")
        private String visibility;

        @Schema(description = "fork 数量（同 forks_count）")
        private Integer forks;

        @JSONField(name = "open_issues")
        @Schema(description = "未关闭 Issue 数量（同 open_issues_count）")
        private Integer openIssues;

        @Schema(description = "Watch 数量（同 watchers_count）")
        private Integer watchers;

        @JSONField(name = "default_branch")
        @Schema(description = "默认分支名称")
        private String defaultBranch;

        // 以下字段仅出现在 head.repo / base.repo 中
        @JSONField(name = "allow_squash_merge")
        @Schema(description = "是否允许 Squash Merge")
        private Boolean allowSquashMerge;

        @JSONField(name = "allow_merge_commit")
        @Schema(description = "是否允许 Merge Commit")
        private Boolean allowMergeCommit;

        @JSONField(name = "allow_rebase_merge")
        @Schema(description = "是否允许 Rebase Merge")
        private Boolean allowRebaseMerge;

        @JSONField(name = "allow_auto_merge")
        @Schema(description = "是否允许自动合并")
        private Boolean allowAutoMerge;

        @JSONField(name = "delete_branch_on_merge")
        @Schema(description = "合并后是否自动删除分支")
        private Boolean deleteBranchOnMerge;

        @JSONField(name = "allow_update_branch")
        @Schema(description = "是否允许更新分支")
        private Boolean allowUpdateBranch;

        @JSONField(name = "use_squash_pr_title_as_default")
        @Schema(description = "是否以 PR 标题作为 Squash 提交默认标题")
        private Boolean useSquashPrTitleAsDefault;

        @JSONField(name = "squash_merge_commit_message")
        @Schema(description = "Squash 合并提交信息类型")
        private String squashMergeCommitMessage;

        @JSONField(name = "squash_merge_commit_title")
        @Schema(description = "Squash 合并提交标题类型")
        private String squashMergeCommitTitle;

        @JSONField(name = "merge_commit_message")
        @Schema(description = "普通合并提交信息类型")
        private String mergeCommitMessage;

        @JSONField(name = "merge_commit_title")
        @Schema(description = "普通合并提交标题类型")
        private String mergeCommitTitle;
    }

    // -------------------------------------------------------------------------
    // GithubUser
    // -------------------------------------------------------------------------

    @Data
    @NoArgsConstructor
    @Schema(description = "GitHub 用户信息")
    public static class GithubUser {

        @Schema(description = "用户名（登录名）")
        private String login;

        @Schema(description = "用户 ID")
        private Long id;

        @JSONField(name = "node_id")
        @Schema(description = "用户 Node ID")
        private String nodeId;

        @JSONField(name = "avatar_url")
        @Schema(description = "头像 URL")
        private String avatarUrl;

        @JSONField(name = "gravatar_id")
        @Schema(description = "Gravatar ID")
        private String gravatarId;

        @Schema(description = "用户 API URL")
        private String url;

        @JSONField(name = "html_url")
        @Schema(description = "个人主页 URL")
        private String htmlUrl;

        @JSONField(name = "followers_url")
        @Schema(description = "粉丝列表 API URL")
        private String followersUrl;

        @JSONField(name = "following_url")
        @Schema(description = "关注列表 API URL 模板")
        private String followingUrl;

        @JSONField(name = "gists_url")
        @Schema(description = "Gist API URL 模板")
        private String gistsUrl;

        @JSONField(name = "starred_url")
        @Schema(description = "Star 列表 API URL 模板")
        private String starredUrl;

        @JSONField(name = "subscriptions_url")
        @Schema(description = "订阅列表 API URL")
        private String subscriptionsUrl;

        @JSONField(name = "organizations_url")
        @Schema(description = "组织列表 API URL")
        private String organizationsUrl;

        @JSONField(name = "repos_url")
        @Schema(description = "仓库列表 API URL")
        private String reposUrl;

        @JSONField(name = "events_url")
        @Schema(description = "事件列表 API URL 模板")
        private String eventsUrl;

        @JSONField(name = "received_events_url")
        @Schema(description = "接收事件列表 API URL")
        private String receivedEventsUrl;

        @Schema(description = "账号类型，如 User/Organization")
        private String type;

        @JSONField(name = "user_view_type")
        @Schema(description = "用户视图类型，如 public")
        private String userViewType;

        @JSONField(name = "site_admin")
        @Schema(description = "是否为 GitHub 管理员")
        private Boolean siteAdmin;
    }

    // -------------------------------------------------------------------------
    // Links（_links）
    // -------------------------------------------------------------------------

    @Data
    @NoArgsConstructor
    @Schema(description = "PR 相关资源超链接集合")
    public static class Links {

        @Schema(description = "PR 自身 API 链接")
        private LinkItem self;

        @Schema(description = "PR 页面链接")
        private LinkItem html;

        @Schema(description = "对应 Issue 链接")
        private LinkItem issue;

        @Schema(description = "评论列表链接")
        private LinkItem comments;

        @JSONField(name = "review_comments")
        @Schema(description = "Review 评论列表链接")
        private LinkItem reviewComments;

        @JSONField(name = "review_comment")
        @Schema(description = "单条 Review 评论链接模板")
        private LinkItem reviewComment;

        @Schema(description = "提交列表链接")
        private LinkItem commits;

        @Schema(description = "状态列表链接")
        private LinkItem statuses;
    }

    @Data
    @NoArgsConstructor
    @Schema(description = "超链接条目")
    public static class LinkItem {

        @Schema(description = "链接地址")
        private String href;
    }
}
