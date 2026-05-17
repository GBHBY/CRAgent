# GitLab / GitHub Code Review Agent

基于 Spring AI + ReAct Agent 的自动化代码审查系统，支持 GitLab / GitHub Webhook 触发，通过 AI 对 MR/PR diff 进行多维度审查。

---

## 功能状态

### ✅ 已完成

- **双平台 Webhook**：支持 GitLab MR 和 GitHub PR 事件触发
- **动态 Skill 选择**：AI 读取所有规范描述，根据 diff 涉及的语言和框架自主匹配并加载对应审查规范，全程内存缓存，无额外 DB 访问
- **多维度代码审查**：规范合规性、安全风险、代码质量、逻辑正确性、性能问题
- **问题分级输出**：BLOCKER / CRITICAL / MAJOR / MINOR / INFO 五级
- **全量项目扫描**：对整个项目逐文件扫描
- **Skill 管理 API**：支持增删改查、手动刷新，定时任务自动同步内存缓存
- **审查任务持久化**：任务记录与问题明细写入 MySQL

### 🚧 进行中

- **GitLab 回写评论**：审查完成后自动在 MR 发布审查意见
- **GitHub 回写评论**：审查完成后自动在 PR 发布审查意见
- **Notion 集成**：通过 MCP 协议将审查报告写入 Notion 数据库
- **钉钉通知**：审查完成后推送通知（通过 / 发现问题 / 失败）

---

## 快速开始

### 1. 数据库初始化

```sql
CREATE DATABASE gitlab_cr DEFAULT CHARSET utf8mb4;
```

执行 `sql/schema.sql` 创建三张表：

| 表名 | 说明 |
|------|------|
| `cr_skill` | 审查规范 Skill |
| `cr_review_task` | 审查任务记录 |
| `cr_review_issue` | 问题明细记录 |

### 2. 配置文件

修改 `src/main/resources/application.yml`：

```yaml
spring:
  ai:
    openai:
      api-key: <DashScope API Key>
      base-url: https://dashscope.aliyuncs.com/compatible-mode/
      chat:
        options:
          model: qwen-plus

  datasource:
    url: jdbc:mysql://127.0.0.1:3306/gitlab_cr?...
    username: <用户名>
    password: <密码>

# 平台选择：gitlab 或 github
webhook:
  platform: gitlab

gitlab:
  api:
    url: https://gitlab.example.com
    private-token: <GitLab Access Token>
  webhook:
    secret-token: <Webhook Secret>
  review:
    target-branches: main,master

github:
  api:
    token: <GitHub Token>
  webhook:
    secret-token: <Webhook Secret>
  review:
    target-branches: main,master

dingtalk:
  webhook:
    url: https://oapi.dingtalk.com/robot/send?access_token=xxx
    secret: SECxxx

notion:
  mcp:
    url: https://mcp.notion.com/mcp
    api-key: <Notion API Key>
  database-id: <Notion Database ID>
```

### 3. 启动

```bash
mvn spring-boot:run
```

### 4. 配置 Webhook

- **GitLab**：项目 Settings → Webhooks，填入 `http://<host>:8080/webhook/gitlab`，勾选 Merge Request events
- **GitHub**：仓库 Settings → Webhooks，填入 `http://<host>:8080/webhook/github`，勾选 Pull Request events

---

## Skill 管理

Skill 是审查规范的核心，每个 Skill 对应一套针对特定语言或框架的审查规则。`description` 字段是关键——AI 依据此字段描述自主判断是否适用于当前 diff。

### 写入示例

```sql
INSERT INTO cr_skill (skill_code, skill_name, skill_type, description, source_path, enabled)
VALUES (
  'java-cr',
  'Java 代码审查规范',
  'TEXT',
  '专业 Java Code Review 技能，符合阿里巴巴 Java 开发手册规范。当 diff 中包含 .java 文件、Spring Boot、MyBatis 相关代码时使用。',
  './skills/java-cr.md',
  1
);
```

### REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/skills` | 查询所有 Skill |
| POST | `/api/skills` | 新增 Skill |
| PUT | `/api/skills/{id}` | 更新 Skill |
| DELETE | `/api/skills/{id}` | 删除 Skill |
| POST | `/api/skills/{id}/refresh` | 手动刷新单个 Skill 内容 |

---

## 手动触发审查

```bash
# 手动触发指定 MR 审查（GitLab）
POST /api/review/{projectId}/mr/{mergeRequestIid}

# 触发全量项目扫描
POST /api/review/{projectId}/full-scan
```

---

## 问题严重程度说明

| 级别 | 含义 |
|------|------|
| BLOCKER | 必须修复，阻断合并（安全漏洞、数据丢失风险） |
| CRITICAL | 强烈建议修复（关键逻辑错误、重大安全风险） |
| MAJOR | 应该修复（违反核心规范、明显代码质量问题） |
| MINOR | 建议改进（代码风格、命名不够清晰） |
| INFO | 信息提示，供参考的最佳实践建议 |
