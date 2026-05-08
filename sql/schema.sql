-- GitLab CR Agent 数据库 Schema

CREATE DATABASE IF NOT EXISTS gitlab_cr DEFAULT CHARSET utf8mb4;
USE gitlab_cr;

-- 编码规范 Skill
CREATE TABLE cr_skill (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_code        VARCHAR(100) NOT NULL COMMENT 'Skill 唯一标识',
    skill_name        VARCHAR(200) NOT NULL COMMENT '显示名称',
    skill_type        VARCHAR(20) NOT NULL COMMENT 'PDF, TEXT, CUSTOM',
    description       TEXT COMMENT '覆盖范围描述',
    source_path       VARCHAR(500) COMMENT '文件路径或 URL',
    content           LONGTEXT COMMENT '解析后的文本内容',
    enabled           TINYINT DEFAULT 1 COMMENT '1=启用, 0=禁用',
    version           INT DEFAULT 1 COMMENT '内容版本号',
    last_refreshed_at DATETIME COMMENT '最后刷新时间',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_code (skill_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='编码规范 Skill';

-- 审查任务记录
CREATE TABLE cr_review_task (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id           VARCHAR(64) NOT NULL COMMENT 'UUID 任务标识',
    project_id        BIGINT COMMENT 'GitLab 项目 ID',
    project_name      VARCHAR(200) COMMENT '项目名称',
    merge_request_iid BIGINT COMMENT 'MR IID（全量扫描为 null）',
    source_branch     VARCHAR(200) COMMENT '源分支',
    target_branch     VARCHAR(200) COMMENT '目标分支',
    trigger_type      VARCHAR(20) NOT NULL COMMENT 'WEBHOOK, MANUAL, FULL_SCAN',
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, RUNNING, PASSED, ISSUES_FOUND, FAILED',
    total_issues      INT DEFAULT 0 COMMENT '问题总数',
    summary           TEXT COMMENT '审查摘要',
    notion_page_url   VARCHAR(500) COMMENT 'Notion 文档链接',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at      DATETIME COMMENT '完成时间',
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_project_mr (project_id, merge_request_iid),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码审查任务';

-- 审查问题记录
CREATE TABLE cr_review_issue (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_task_id  BIGINT NOT NULL COMMENT '关联 cr_review_task.id',
    file_path       VARCHAR(500) NOT NULL COMMENT '文件路径',
    line_number     INT COMMENT '行号',
    severity        VARCHAR(20) NOT NULL COMMENT 'BLOCKER, CRITICAL, MAJOR, MINOR, INFO',
    rule_id         VARCHAR(100) COMMENT '违反的规则 ID',
    message         TEXT NOT NULL COMMENT '问题描述',
    suggestion      TEXT COMMENT '修复建议',
    fix_code        TEXT COMMENT 'AI 生成的修复代码',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_review_task (review_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码审查问题';
