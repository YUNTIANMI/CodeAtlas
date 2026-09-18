# CodeAtlas · AI 软件工程研发助手

**面向个人开发者与小型开发团队的 AI 辅助研发平台。**

它解决的不是「AI 会不会写代码」，而是 **AI 不了解你的项目**。把项目源码、文档、Git 历史统一沉淀为可检索的知识库，让 AI 基于真实项目上下文回答问题、审查代码，并把文件、行号、Commit 作为引用一并给出。

> **核心原则：AI 的回答必须基于项目证据。**
> 检索不到依据时，必须明确说明「未在项目资料中找到依据」，禁止凭模型记忆自由生成。

---

## 目录

- [一、这是什么](#一这是什么)
- [二、如何使用](#二如何使用)
- [三、开发进度](#三开发进度)
- [四、安全基线](#四安全基线)
- [五、技术栈与架构](#五技术栈与架构)
- [六、目录结构](#六目录结构)
- [七、AI 配置](#七ai-配置)
- [八、分支模型](#八分支模型)
- [九、文档索引](#九文档索引)

---

## 一、这是什么

### 1.1 它与普通 AI 聊天工具的区别

单纯调用大模型 API 的聊天工具存在明显局限，本项目正是针对这些局限设计的：

| 普通 AI 聊天工具的局限 | CodeAtlas 的做法 |
|---|---|
| AI 不理解当前项目的完整上下文 | 项目资料切分 → 向量化 → 存入独立知识库 |
| 回答没有出处，无法核实 | 每条回答附带文件 / 行号 / Commit 引用，可回溯 |
| 对话结束知识就丢了 | 知识库持久化，项目知识持续积累 |
| 只能聊天，不能做具体工作 | 除问答外还有代码审查、Git 摘要、多步 Agent 任务 |
| 多个项目的信息混在一起 | 项目级隔离 + 成员权限矩阵，一个项目一份知识库 |

### 1.2 一句话概括

**把项目资料灌进知识库 → AI 基于这份资料回答问题、审查代码，并给出引用来源。**

### 1.3 能力边界

只读是这套系统的设计前提，不是能力缺失（原因见 [ADR-005](docs/decisions/ADR-005-agent-readonly.md)）。

| 能做 | 不做 |
|---|---|
| 基于项目资料的问答（必带引用） | 不修改、不生成、不写回你的代码 |
| 代码审查，输出结构化问题清单 | 不执行命令、不运行程序 |
| Git 提交摘要 | 不 Push、不改分支（Git 全程只读） |
| Agent 多步检索（5 个受控只读工具） | 不支持整仓库导入（源码按文件上传） |

> **Bug 分析尚未实现。** 需求、API 与表结构已在 [需求规格](docs/requirements.md)、[API 设计](docs/api.md)、[数据库设计](docs/database.md) 中定义，但尚未进入阶段计划与开发。

---

## 二、如何使用

### 2.1 五步上手

```text
① 注册登录 ──▶ ② 创建项目 ──▶ ③ 灌入资料 ──▶ ④ 构建知识库 ──▶ ⑤ 使用 AI
                              文档 / 代码 / Git      向量化        问答 · 审查
```

> **第 ④ 步不能跳过。** 上传只是把文件存进项目，必须执行「构建知识库」完成切分与向量化，AI 才能检索到这些内容。这也是最常见的使用误区。

### 2.2 界面功能地图

进入项目详情页后有 9 个页签，按上面的流程排列：

| 页签 | 作用 | 对应步骤 |
|---|---|---|
| **概览** | 项目基本信息与统计 | ② |
| **文档** | 上传 md / txt / pdf，查看解析结果 | ③ |
| **代码** | 上传源码文件，查看项目目录结构 | ③ |
| **Git** | 配置 GitHub 仓库、同步 Commit | ③ |
| **知识库** | 触发构建、查看进度、清空重建 | ④ |
| **AI 问答** | 基于知识库提问，回答附带引用来源 | ⑤ |
| **Code Review** | 提交代码或 Commit，输出结构化问题清单 | ⑤ |
| **Agent** | 一句话派发任务，Agent 自动调用工具分步完成 | ⑤ |
| **成员** | 邀请成员、分配角色权限 | ② |

### 2.3 资料准备：格式与限制

| 类型 | 支持格式 | 上限 | 说明 |
|---|---|---|---|
| 文档 | `md`、`txt`、`pdf` | 单文件 20MB | PDF 会自动抽取文本 |
| 代码 | 12 种源码扩展名（`java`、`cpp`、`cc`、`cxx`、`c`、`h`、`hpp`、`py`、`js`、`jsx`、`ts`、`tsx`） | 单文件 5MB | 按文件上传，共同构成项目目录树；上传目录时自动跳过依赖与构建产物 |
| Git | GitHub 仓库地址 | —— | 只读同步，私有仓库需配置 Token |

### 2.4 两类 AI 用法

**① AI 问答** —— 适合「这个东西是怎么实现的」

```json
提问：这个项目的登录流程是什么？
回答：登录流程由 UserController、UserService、JwtTokenProvider 组成……
引用：[CODE] src/main/java/UserController.java 32-58
      [DOCUMENT] API 设计说明书.md
```

**② Code Review** —— 适合「这段代码有没有问题」，产出的是**结构化问题清单**，不是聊天：

| 严重程度 | 类别 | 文件 | 行号 | 问题 | 建议 |
|---|---|---|---|---|---|
| MAJOR | SECURITY | UserService.java | 42 | 字符串拼接构造 SQL，存在注入风险 | 改用参数绑定 |

此外，**Agent** 可以接收一句话任务（如「找出所有处理认证的代码并说明其调用关系」），自动在 5 个只读工具间分步检索后给出结论，工具调用轨迹全程留痕可查。

### 2.5 角色与权限

| 角色 | 权限范围 |
|---|---|
| **OWNER** | 项目创建者，全部权限，含删除项目 |
| **ADMIN** | 管理成员、修改配置、上传资料、使用 AI、查看 AI 使用记录 |
| **MEMBER** | 查看项目 / 文档 / 代码，使用 AI，创建分析任务 |
| **VIEWER** | 只读查看与 AI 问答 |

> 角色分层的意义在于：**由具备工程能力的成员负责灌资料，提问的人可以完全不懂代码。**

### 2.6 在本地跑起来

**环境要求**：JDK 17、Maven、Node.js 18+、Docker、[Ollama](https://ollama.com/)（本地 Embedding）

**第 1 步：启动依赖服务**

```bash
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps
```

本地端口分配：MySQL **23306**、Redis **16379**、Qdrant **6333**
（3306 与 6379 在常见开发机上容易被系统服务或其他项目占用，3308 可能落在 Hyper-V 保留段，故错开）

> 本节是**开发模式**：只有依赖服务跑在容器里，前后端在宿主机上直接运行，便于热更新。
> 若想一条命令启动完整系统（含前后端容器），见 [2.7 部署到 Docker](#27-部署到-docker)。

**第 2 步：准备 Embedding 模型**

```bash
ollama pull bge-m3
```

**第 3 步：配置环境变量**

```bash
# Windows PowerShell
$env:DEEPSEEK_API_KEY = "你的 Key"

# Linux / macOS
export DEEPSEEK_API_KEY=你的Key
```

**第 4 步：启动后端**

```bash
cd backend
mvn spring-boot:run
```

启动时通过 `schema.sql` 自动建表，并初始化 `ROLE_USER` / `ROLE_ADMIN`。

**第 5 步：启动前端**

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 **http://localhost:5173** 即可。前端开发服务器已把 `/api` 与 `/health` 代理到后端 8080，同源请求，无需处理跨域。

**验证**

```bash
curl http://localhost:8080/health
# {"status":"UP","db":"up","redis":"up"}
```

**不想开浏览器时，也可以用命令行试用**

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

### 2.7 部署到 Docker

2.6 是开发模式。若想**一条命令启动完整系统**（前端 + 后端 + MySQL + Redis + Qdrant + Ollama），使用根目录的 `docker-compose.yml`：

```bash
# 1. 配置环境变量（至少填 MYSQL_ROOT_PASSWORD 与 DEEPSEEK_API_KEY）
cp .env.example .env

# 2. 构建并启动全部服务
docker compose up -d

# 3. 拉取 Embedding 模型（只需一次，约 1.2GB）
docker compose exec ollama ollama pull bge-m3
```

启动后访问 **http://localhost:8081**（前端），健康检查在 **http://localhost:8080/health**。

两种方式的区别：

| | 开发模式（2.6） | Docker 完整部署 |
|---|---|---|
| 启动命令 | `docker compose -f docker-compose.dev.yml up -d` | `docker compose up -d` |
| 前端 | 宿主机 `npm run dev`，端口 **5173** | Nginx 容器，端口 **8081** |
| 后端 | 宿主机 `mvn spring-boot:run` | 容器，端口 **8080** |
| 数据库等依赖 | 端口对宿主机开放（23306 / 16379 / 6333） | 仅容器网络内可达，不对外暴露 |
| 前端如何调接口 | Vite Dev Server 代理 `/api` | Nginx 反向代理 `/api` |
| 适用场景 | 改代码即时热更新 | 演示、交付、长期运行 |

容器编排、数据卷备份、生产环境注意事项与故障排查详见 **[部署指南](docs/deployment.md)**。

如需**对外发布**（云服务器 + Caddy 自动 HTTPS，一个固定链接访问），见 **[生产部署（VPS + Caddy）](docs/deployment-vps.md)**。

### 2.8 常见问题

| 现象 | 原因与处理 |
|---|---|
| AI 回答「未在项目资料中找到依据」 | 知识库没构建，或资料未上传 —— 先执行「构建知识库」 |
| 上传被拒绝 | 扩展名不在白名单（文档 md/txt/pdf，代码 12 种源码扩展名），或文档超过 20MB、代码超过 5MB |
| 知识库构建一直失败 | 检查 Ollama 是否运行、`bge-m3` 是否已拉取 |
| 升级过 Embedding 模型后检索异常 | 向量维度不匹配，需清空知识库后重建 |
| 登录报 429 | 连续失败 5 次触发锁定，等待 15 分钟 |

---

## 三、开发进度

**Phase 12：Docker 部署**（已完成 —— 前后端多阶段镜像构建 + 六服务编排，`docker compose up -d` 一条命令启动完整系统）

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
| Phase 12 | Docker 部署 | ✅ 已完成 |

### 核心能力清单

- **项目知识库**：文档 + 源码 + Git 信息 → 解析 → 切分 → Embedding → 向量库
- **AI 项目问答**：RAG 检索项目上下文后回答，并附带引用来源
- **AI Code Review**：输出结构化结果（严重程度 / 文件 / 行号 / 问题 / 建议）
- **Git 分析**：导入仓库、同步 Commit、AI 生成提交摘要（只读，不 Push）
- **AI Agent**：通过 5 个受控只读工具（搜代码 / 读文件 / 查文档 / 查提交 / 看目录结构）完成多步任务
- **执行记录**：记录模型、工具、检索内容、耗时与 Token，用于调试与审计

---

## 四、安全基线

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
| 文件上传 | 扩展名白名单（文档 md / txt / pdf，代码 12 种源码扩展名）、体积上限（文档 20MB / 代码 5MB）、文件名与路径长度校验；落盘名由数据库 ID 生成，杜绝路径穿越 |
| 错误信息 | 统一异常处理，未预期异常只返回 `500` + 通用文案，堆栈仅进服务端日志 |
| 日志 | 不记录密码、Token、API Key 等敏感字段 |

> 端到端验证脚本覆盖：未认证 / 乱码 Token / 篡改 Token → 401，用户 B 访问用户 A 的项目（详情、文档、成员、上传、修改、删除、伪造 `userId` 参数）→ 403，
> 上传边界 → 400，登出后 Token 立即失效 → 401，登录失败 6 次 → 429，CORS 白名单与安全响应头校验。

---

## 五、技术栈与架构

```text
前端     React · TypeScript
后端     Java · Spring Boot
数据库   MySQL · Redis
向量库   Qdrant
AI       LLM · Embedding · RAG · Agent
工程     Git · GitHub · Maven · Docker · Nginx
测试     JUnit · Spring Boot Test
```

架构形态为 **前后端分离 + 模块化单体**，第一阶段不采用微服务。

```text
React + TypeScript ──HTTP──> Spring Boot ──┬──> MySQL
                                          ├──> Redis
                                          └──> AI Service ──> LLM / Embedding / RAG
                                                    └──────> Qdrant
```

> AI 能力通过统一的 `AIProvider` 抽象接入，业务代码不绑定任何具体厂商，可自由切换模型。

---

## 六、目录结构

```text
CodeAtlas/
├── README.md
├── docs/
│   ├── requirements.md     需求规格说明书（SRS）
│   ├── architecture.md     技术选型与系统架构
│   ├── database.md         数据库设计与 ER 图
│   ├── api.md              REST API 设计
│   ├── development.md      开发阶段计划与开发规范
│   ├── deployment.md       Docker 部署指南
│   └── decisions/          架构决策记录（ADR）
├── backend/                Spring Boot 后端（Java 17 + Maven）
│   ├── Dockerfile          多阶段构建：Maven 编译 → JRE 运行
│   └── src/main/java/com/codeatlas/
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
│       ├── agent/          Agent 引擎 · 5 个只读工具 · 调用轨迹（Phase 9）
│       ├── ai/             Provider 抽象 · Qdrant 客户端 · 执行日志
│       └── storage/        文件存储抽象（本地 / 可迁移对象存储）
├── frontend/               React 前端（React 18 + TypeScript + Vite）
│   ├── Dockerfile          多阶段构建：Node 编译 → Nginx 托管
│   ├── nginx.conf          静态托管 + /api 反向代理 + SPA 路由回退
│   └── src/
│       ├── pages/          登录 / 注册 / 项目列表 / 项目详情（9 个功能页签）
│       ├── components/     布局 · 路由守卫 · 通用组件
│       ├── services/       按模块封装的后端接口调用
│       ├── stores/         认证状态 · 全局提示
│       ├── hooks/          通用 Hook
│       ├── types/          与后端对齐的 TypeScript 类型
│       ├── utils/          工具函数
│       └── styles/         样式
├── docker-compose.yml      完整系统编排（前端 / 后端 / MySQL / Redis / Qdrant / Ollama）
├── docker-compose.prod.yml 生产覆盖：收紧端口 + Caddy 自动 HTTPS 入口
├── docker-compose.dev.yml  仅基础设施，供本地开发使用
├── Caddyfile               公网 HTTPS 入口配置（自动签发证书）
├── deploy.sh               生产环境一键部署脚本（Linux）
└── .env.example            部署环境变量模板（复制为 .env 使用）
```

---

## 七、AI 配置

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

### 其他环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `JWT_SECRET` | 内置占位密钥 | **生产必须覆盖**，HS256 要求 ≥32 字节，否则启动时打印告警 |
| `JWT_EXPIRATION` | `7200` | Token 有效期（秒） |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | 跨域白名单，逗号分隔，禁止 `*`；Docker 部署时请改为前端实际地址 |
| `LOGIN_MAX_ATTEMPTS` | `5` | 登录连续失败多少次后锁定账号 |
| `LOGIN_LOCK_SECONDS` | `900` | 锁定时长（秒） |
| `GITHUB_TOKEN` | 空 | 同步私有仓库时需要 |

---

## 八、分支模型

采用 `main` / `develop` / `feature/*` 三分支模型：

```text
feature/* ──> develop ──> release ──> main
```

- `main`：稳定分支，只接受经过验证的发布版本，始终保持可演示状态
- `develop`：日常集成分支，默认分支，所有 `feature/*` 先合入这里
- `feature/*`：功能分支，命名如 `feature/user-auth`

### Commit 规范

遵循 Conventional Commits：

```text
feat: create project module
fix: resolve project permission bug
refactor: simplify document service
test: add project service tests
docs: update API documentation
```

---

## 九、文档索引

| 文档 | 说明 |
|---|---|
| [需求规格说明书](docs/requirements.md) | 项目背景、用户角色、功能需求、MVP 范围与验收标准 |
| [技术选型说明书](docs/architecture.md) | 技术栈、分层架构、RAG 与 Agent 设计、部署方案 |
| [数据库设计](docs/database.md) | 14 张表字段设计、ER 图、权限矩阵与命名规范 |
| [API 设计](docs/api.md) | REST 接口约定、错误码、各模块端点与权限要求 |
| [开发阶段计划](docs/development.md) | 阶段划分、版本规划、AI 协同开发规范 |
| [部署指南](docs/deployment.md) | Docker 一键部署、端口规划、数据备份、生产注意事项与故障排查 |
| [生产部署（VPS + Caddy）](docs/deployment-vps.md) | 云服务器 + Docker + Caddy 自动 HTTPS，一个固定链接对外访问 |

### 架构决策记录（ADR）

| 编号 | 决策 |
|---|---|
| [ADR-001](docs/decisions/ADR-001-database.md) | 为什么选择 MySQL |
| [ADR-002](docs/decisions/ADR-002-vector-db.md) | 为什么选择 Qdrant |
| [ADR-003](docs/decisions/ADR-003-auth.md) | 为什么采用 JWT 无状态认证 |
| [ADR-004](docs/decisions/ADR-004-monolith.md) | 为什么不使用微服务 |
| [ADR-005](docs/decisions/ADR-005-agent-readonly.md) | 为什么 Agent 只读且只能通过工具访问 |

---

> 本项目为个人开发环境，GitHub 22 端口不可用，SSH 已配置走 `ssh.github.com:443`。
> 如遇连接问题，检查 `~/.ssh/config` 中的 GitHub 配置。
