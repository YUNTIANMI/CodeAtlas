# CodeAtlas · AI 软件工程研发助手

面向个人开发者与小型团队的 **AI 辅助研发平台**。

它不是又一个 AI Chat Demo，而是让 AI 真正"读懂"你的项目——把源码、文档、Git 历史统一管理成可检索的知识库，再基于**真实项目上下文**提供问答、代码审查与 Bug 分析能力。

> 核心原则：AI 的回答必须基于项目证据，而不是凭模型记忆自由生成。每一条回答都应附带文件、路径、Commit 等引用来源。

---

## 当前状态

**Phase 2：用户系统**（进行中 —— 注册 / 登录 / 退出 / 鉴权已完成）

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase 0 | 需求与架构 | ✅ 已完成 |
| Phase 1 | 建立基础工程 | ✅ 已完成 |
| Phase 2 | 用户与项目管理 | 🚧 进行中（用户系统已完成，项目管理待开发） |
| Phase 3 | 文档与代码管理 | ⬜ 待开始 |
| Phase 4 | RAG 知识库 | ⬜ 待开始 |
| Phase 5 | AI 项目问答 | ⬜ 待开始 |
| Phase 6 | AI Code Review | ⬜ 待开始 |
| Phase 7 | Git 分析 | ⬜ 待开始 |
| Phase 8 | Agent | ⬜ 待开始 |
| Phase 9 | 测试与安全 | ⬜ 待开始 |
| Phase 10 | Docker 部署 | ⬜ 待开始 |

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
│       └── auth/           JWT 认证 · 注册登录
├── docker-compose.yml      MySQL + Redis + Qdrant 本地环境
└── frontend/               React 前端（待建）
```

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
