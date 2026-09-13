# CodeAtlas · AI 软件工程研发助手

面向个人开发者与小型团队的 **AI 辅助研发平台**。

它不是又一个 AI Chat Demo，而是让 AI 真正"读懂"你的项目——把源码、文档、Git 历史统一管理成可检索的知识库，再基于**真实项目上下文**提供问答、代码审查与 Bug 分析能力。

> 核心原则：AI 的回答必须基于项目证据，而不是凭模型记忆自由生成。每一条回答都应附带文件、路径、Commit 等引用来源。

---

## 当前状态

**Phase 11：安全**（已完成 —— 单元测试 239 项；并对运行中的服务做了 39 项端到端安全验证，全部通过）

> 阶段编号以 [开发阶段计划](docs/development.md) 的**详细章节**为准（Phase 0 ~ Phase 12）。

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase 0 | 需求与架构 | ✅ 已完成 |
| Phase 1 | 建立基础工程 | ✅ 已完成 |
| Phase 2 | 用户系统 | ✅ 已完成 |
| Phase 3 | 项目管理 | ✅ 已完成 |
| Phase 4 | 文档与代码管理 | ✅ 已完成 |
| Phase 5 | 知识库与 RAG | ✅ 已完成 |
| Phase 6 | AI 项目问答 | ✅ 已完成 |
| Phase 7 | AI Code Review | ✅ 已完成 |
| Phase 8 | Git 分析 | ✅ 已完成 |
| Phase 9 | Agent | ✅ 已完成 |
| Phase 10 | 测试 | ✅ 已完成 |
| Phase 11 | 安全 | ✅ 已完成 |
| Phase 12 | Docker 部署 | ⬜ 待开始 |

---

## 核心能力

- **项目知识库**：文档 + 源码 + Git 信息 → 解析 → 切分 → Embedding → 向量库
- **AI 项目问答**：RAG 检索项目上下文后回答，并附带引用来源
- **AI Code Review**：输出结构化结果（严重程度 / 文件 / 行号 / 问题 / 建议）
- **Bug 分析**：解析错误日志 → 定位代码 → 检索文档 → 生成原因与修复建议
- **Git 分析**：导入仓库、同步 Commit、AI 生成提交摘要（只读，不 Push）
- **AI Agent**：通过受控工具（搜代码 / 读文件 / 查文档 / 查提交）完成多步任务
- **执行记录**：记录模型、工具、检索内容、耗时与 Token，用于调试与审计

---

## 安全基线（Phase 11）

| 风险面 | 措施 |
|---|---|
| 认证 | JWT 无状态认证；签名密钥 HS256（≥32 字节）；启动时检测默认密钥并告警 |
| 密码 | BCrypt 加盐哈希存储；登录失败不区分「用户不存在 / 密码错误」，防账号枚举 |
| 暴力破解 | 同一账号连续失败 5 次锁定 15 分钟（Redis 计数，锁定期间不校验密码） |
| 越权（IDOR） | 「当前用户 ID」只取自认证上下文，**从不信任请求参数**；每个接口经 `ProjectPermissionService` 校验项目成员身份与角色 |
| 项目内角色 | OWNER / ADMIN / MEMBER / VIEWER 四级权限矩阵，读 / 写 / 管理分别校验 |
| Token 失效 | 退出登录后 Token 进入 Redis 黑名单，立即失效 |
| CSRF | 凭据只走 `Authorization` 头（不使用 Cookie），已关闭 CSRF 且无攻击面 |
| CORS | 白名单来自 `CORS_ALLOWED_ORIGINS`，禁止 `*` 通配，携带凭据的跨域只放行声明来源 |
| SQL 注入 | 全部通过 Spring Data JPA 参数绑定访问数据库，无字符串拼接 SQL |
| XSS | 纯 JSON 接口，不返回 HTML；响应头开启 `Content-Security-Policy: default-src 'none'`、`nosniff` |
| 文件上传 | 扩展名白名单（md / java / cpp / py / js / ts）、20MB 上限、文件名长度校验；落盘名由数据库 ID 生成，杜绝路径穿越 |
| 错误信息 | 统一异常处理，未预期异常只返回 `500` + 通用文案，堆栈仅进服务端日志 |
| 日志 | 不记录密码、Token、API Key 等敏感字段 |

> 端到端验证脚本覆盖：未认证 / 乱码 Token / 篡改 Token → 401，用户 B 访问用户 A 的项目（详情、文档、成员、上传、修改、删除、伪造 `userId` 参数）→ 403，
> 上传边界 → 400，登出后 Token 立即失效 → 401，登录失败 6 次 → 429，CORS 白名单与安全响应头校验。

---

## 技术栈

```
前端     React · TypeScript
后端     Java · Spring Boot
数据库   MySQL · Redis
向量库   Qdrant
AI       LLM · Embedding · RAG · Agent
工程     Git · GitHub · Maven · Docker · Nginx
测试     JUnit · Spring Boot Test
```

架构形态为 **前后端分离 + 模块化单体**，第一阶段不采用微服务。

```
React + TypeScript ──HTTP──> Spring Boot ──┬──> MySQL
                                          ├──> Redis
                                          └──> AI Service ──> LLM / Embedding / RAG
                                                    └──────> Qdrant
```

> AI 能力通过统一的 `AIProvider` 抽象接入，业务代码不绑定任何具体厂商，可自由切换模型。

---

## 目录结构

```
CodeAtlas/
├── README.md
├── docs/
│   ├── requirements.md     需求规格说明书（SRS）
│   ├── architecture.md     技术选型与系统架构
│   ├── database.md         数据库设计与 ER 图
│   ├── api.md              REST API 设计
│   ├── development.md      开发阶段计划与开发规范
│   └── decisions/          架构决策记录（ADR）
├── backend/                Spring Boot 后端（Java 17 + Maven）
│   └── src/main/java/com/codeatlas
│       ├── common/         统一响应 · 异常 · 安全配置
│       ├── user/           用户实体与查询
│       ├── auth/           JWT 认证 · 注册登录 · 登录失败限流 · Token 黑名单
│       ├── project/        项目 · 成员 · 权限校验
│       ├── document/       文档 · 解析 · 切分
│       ├── code/           代码文件 · 目录结构
│       ├── knowledge/      知识库构建 · 检索 · RAG 问答
│       ├── chat/           会话 · 消息 · 引用（Phase 6）
│       ├── review/         AI Code Review 结构化结果（Phase 7）
│       ├── git/            GitHub 客户端 · 提交同步 · AI 摘要（Phase 8，只读）
│       └── agent/          Agent 引擎 · 5 个只读工具 · 调用轨迹（Phase 9）
│       ├── ai/             Provider 抽象 · Qdrant 客户端 · 执行日志
│       └── storage/        文件存储抽象（本地 / 可迁移对象存储）
├── docker-compose.yml      MySQL + Redis + Qdrant 本地环境
└── frontend/               React 前端（待建）
```

---

## AI 配置（环境变量）

AI 能力通过 `AIProvider` 抽象接入，**不绑定任何模型厂商**。

当前默认组合：

| 能力 | 提供方 | 模型 | 说明 |
|---|---|---|---|
| LLM（生成答案） | DeepSeek | `deepseek-chat` | 需要 API Key |
| Embedding（向量化） | 本地 Ollama | `bge-m3`（1024 维） | 免费、无需 Key、可离线运行 |

密钥通过**环境变量**注入，切勿写进配置文件或提交到仓库：

```bash
# Windows PowerShell
$env:DEEPSEEK_API_KEY = "你的 Key"

# Linux / macOS
export DEEPSEEK_API_KEY=你的Key
```

切换模型只需改配置：

```yaml
codeatlas:
  ai:
    chat:
      provider: deepseek      # deepseek / openai / ollama
      model: deepseek-chat
    embedding:
      provider: ollama        # ollama / siliconflow / openai
      model: bge-m3
      dimension: 1024
```

> 更换 Embedding 模型后，向量维度需与 Qdrant 集合一致，需清空知识库后重建。

---

## 分支模型

采用 `main` / `develop` / `feature/*` 三分支模型：

```
feature/* ──> develop ──> release ──> main
```

- `main`：稳定分支，只接受经过验证的发布版本，始终保持可演示状态
- `develop`：日常集成分支，默认分支，所有 `feature/*` 先合入这里
- `feature/*`：功能分支，命名如 `feature/user-auth`

### Commit 规范

遵循 Conventional Commits：

```
feat: create project module
fix: resolve project permission bug
refactor: simplify document service
test: add project service tests
docs: update API documentation
```

---

## 本地开发

### 1. 启动依赖服务

```bash
docker compose up -d
docker compose ps
```

本地端口分配：MySQL **23306**、Redis **16379**、Qdrant **6333**
（3306 与 6379 在常见开发机上容易被系统服务或其他项目占用，3308 可能落在 Hyper-V 保留段，故错开）

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端启动时会通过 `schema.sql` 自动建表，并初始化 `ROLE_USER` / `ROLE_ADMIN`。

### 2.1 安全相关环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `JWT_SECRET` | 内置占位密钥 | **生产必须覆盖**，HS256 要求 ≥32 字节，否则启动时打印告警 |
| `JWT_EXPIRATION` | `7200` | Token 有效期（秒） |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | 跨域白名单，逗号分隔，禁止 `*` |
| `LOGIN_MAX_ATTEMPTS` | `5` | 登录连续失败多少次后锁定账号 |
| `LOGIN_LOCK_SECONDS` | `900` | 锁定时长（秒） |

### 3. 验证

```bash
curl http://localhost:8080/health
# {"status":"UP","db":"up","redis":"up"}
```

### 4. 试用用户系统

```bash
# 注册
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"password123"}'

# 登录，取得 token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}'

# 访问受保护接口
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <token>"
```

> 本项目为个人开发环境，GitHub 22 端口不可用，SSH 已配置走 `ssh.github.com:443`。
> 如遇连接问题，检查 `~/.ssh/config` 中的 GitHub 配置。

---

## 文档索引

| 文档 | 说明 |
|---|---|
| [需求规格说明书](docs/requirements.md) | 项目背景、用户角色、功能需求、MVP 范围与验收标准 |
| [技术选型说明书](docs/architecture.md) | 技术栈、分层架构、RAG 与 Agent 设计、部署方案 |
| [数据库设计](docs/database.md) | 14 张表字段设计、ER 图、权限矩阵与命名规范 |
| [API 设计](docs/api.md) | REST 接口约定、错误码、各模块端点与权限要求 |
| [开发阶段计划](docs/development.md) | 阶段划分、版本规划、AI 协同开发规范 |

### 架构决策记录（ADR）

| 编号 | 决策 |
|---|---|
| [ADR-001](docs/decisions/ADR-001-database.md) | 为什么选择 MySQL |
| [ADR-002](docs/decisions/ADR-002-vector-db.md) | 为什么选择 Qdrant |
| [ADR-003](docs/decisions/ADR-003-auth.md) | 为什么采用 JWT 无状态认证 |
| [ADR-004](docs/decisions/ADR-004-monolith.md) | 为什么不使用微服务 |
| [ADR-005](docs/decisions/ADR-005-agent-readonly.md) | 为什么 Agent 只读且只能通过工具访问 |
