# AI 软件工程研发助手
## 技术选型说明书

**版本：** V1.0

---

# 1. 技术选型原则

本项目的技术选择遵循四个原则：

### 原则一：真实工程

技术栈应该接近真实企业项目，而不是为了炫技。

### 原则二：个人可维护

项目由个人开发，因此避免过度微服务化。

### 原则三：适合 AI 协同开发

选择生态成熟、AI 容易辅助开发和生成代码的技术。

### 原则四：能够展示软件工程能力

技术栈需要覆盖：

```text
前端
后端
数据库
缓存
AI
Git
测试
部署
```

---

# 2. 总体架构

采用：

> 前后端分离 + 单体后端 + AI 服务模块

架构：

```text
                    Browser
                       │
                       ↓
              React + TypeScript
                       │
                    HTTP
                       │
                       ↓
                Spring Boot
                       │
       ┌───────────────┼───────────────┐
       ↓               ↓               ↓
     MySQL           Redis          AI Service
                                       │
                              ┌────────┼────────┐
                              ↓        ↓        ↓
                             LLM    Embedding  RAG
                                       │
                                       ↓
                                Vector Database
```

第一阶段不采用微服务。

---

# 3. 前端技术选型

## 3.1 TypeScript

选择 TypeScript 作为前端主要开发语言。

原因：

- 类型安全。
- IDE 支持优秀。
- 大型项目可维护性较好。
- 与 React 生态结合成熟。
- 有利于 AI 辅助代码理解。

---

## 3.2 React

选择 React 作为前端 UI 开发技术。

主要负责：

- 页面渲染
- 组件化
- 状态管理
- 用户交互

推荐使用组件化设计：

```text
Layout
├── Sidebar
├── Header
└── Content

Project
├── ProjectList
├── ProjectDetail
├── ProjectSettings
└── ProjectMembers
```

---

## 3.3 HTTP Client

前端通过 HTTP API 与后端通信。

例如：

```text
GET    /api/projects
POST   /api/projects
PUT    /api/projects/{id}
DELETE /api/projects/{id}
```

数据格式使用 JSON。

---

# 4. 后端技术选型

## 4.1 Java

选择 Java 作为后端主要开发语言。

原因：

- 自己已有 Java 基础。
- 企业后端应用广泛。
- 面向对象体系完整。
- Spring 生态成熟。
- 适合展示软件工程能力。

---

## 4.2 Spring Boot

Spring Boot 作为后端核心框架。

负责：

- HTTP API
- 依赖管理
- 业务逻辑
- 数据库访问
- 权限
- 异常处理
- 日志
- 配置管理

---

# 5. 后端分层

采用经典分层结构：

```text
Controller
    ↓
Service
    ↓
Repository / Mapper
    ↓
Database
```

例如：

```text
ProjectController
        ↓
ProjectService
        ↓
ProjectRepository
        ↓
MySQL
```

职责：

### Controller

负责：

- 接收 HTTP 请求
- 参数校验
- 返回 HTTP 响应

### Service

负责：

- 业务逻辑
- 事务
- 多模块协作

### Repository / Mapper

负责：

- 数据库访问

---

# 6. 数据库技术选型

## 6.1 MySQL

作为主数据库。

用于存储：

```text
User
Project
ProjectMember
Document
CodeFile
GitRepository
GitCommit
Conversation
Message
ReviewResult
BugReport
AIExecutionLog
```

选择原因：

- 关系模型适合项目数据。
- SQL 成熟。
- 与 Spring Boot 集成方便。
- 个人开发成本低。

---

# 7. Redis

Redis 作为缓存与临时数据存储。

主要用途：

```text
登录 Session / Token 辅助
缓存热点项目
AI 任务状态
接口限流
短期数据
```

第一版不追求复杂 Redis 架构。

---

# 8. 向量数据库

第一阶段建议选择：

> Qdrant

用于保存：

```text
Document Chunk
Code Chunk
Embedding
Metadata
```

原因：

- 适合个人项目。
- 独立部署简单。
- 方便实现向量相似度检索。
- 与 RAG 架构适配。

系统最终形成：

```text
MySQL
↓
结构化业务数据

Qdrant
↓
知识库向量数据
```

---

# 9. AI 技术

AI 部分采用抽象化设计。

系统不要把业务代码写死成：

```text
OpenAI API
```

而应该设计统一 AI Service：

```text
AIProvider
├── Provider A
├── Provider B
└── Provider C
```

这样未来可以切换模型。

---

## 9.1 LLM

负责：

- 项目问答
- Code Review
- Bug 分析
- 内容总结
- Agent 决策

---

## 9.2 Embedding

负责：

```text
文档
↓
Embedding
↓
向量
↓
Vector Database
```

查询时：

```text
问题
↓
Embedding
↓
相似度搜索
↓
相关知识
```

---

# 10. RAG 架构

采用标准 RAG 思路：

```text
用户问题
 ↓
Query Embedding
 ↓
Vector Search
 ↓
Top K 文档/代码片段
 ↓
Context
 ↓
LLM
 ↓
Answer
```

回答结果需要保存引用来源。

---

# 11. Git 集成

第一阶段使用 GitHub API / Git 仓库分析。

主要获取：

```text
Repository
Branch
Commit
Commit Diff
File
```

系统暂时只读，不允许 AI 直接 Push。

---

# 12. Agent 工具架构

Agent 不允许直接访问数据库底层。

通过受控工具：

```text
Tool
├── SearchCodeTool
├── ReadFileTool
├── SearchDocumentTool
├── SearchGitCommitTool
└── GetProjectStructureTool
```

AI 只能通过 Tool 获取项目资料。

这样可以：

- 控制权限。
- 记录调用过程。
- 防止 AI 随意修改数据。
- 方便调试。

---

# 13. 文件存储

第一阶段：

> 本地文件存储。

保存：

```text
/uploads/{projectId}/{fileId}
```

后期可以迁移到：

```text
S3 / MinIO / OSS
```

业务代码尽量通过统一 StorageService 访问文件，避免和具体存储方式强耦合。

---

# 14. 构建工具

Java：

> Maven

前端：

> npm

C++ 辅助工具若后续加入：

> CMake

---

# 15. 开发环境

推荐：

```text
IDE
├── IntelliJ IDEA
└── VS Code

Database
├── MySQL
└── Redis

AI
└── LLM API

Version Control
└── Git + GitHub
```

---

# 16. 部署技术

第一阶段：

```text
Linux
Docker
Docker Compose
Nginx
```

部署结构：

```text
Internet
   ↓
Nginx
   ↓
Frontend
   ↓
Spring Boot
   ├── MySQL
   ├── Redis
   └── Qdrant
```

---

# 17. 测试技术

后端：

- 单元测试
- Controller 测试
- Service 测试
- Repository 测试

重点测试：

```text
正常情况
异常情况
权限
边界条件
数据库事务
```

AI 模块额外测试：

```text
检索正确性
引用正确性
Prompt 稳定性
模型异常
API 超时
```

---

# 18. 日志与监控

第一阶段主要记录：

```text
HTTP 请求
业务异常
AI 请求
AI 响应耗时
工具调用
任务状态
```

重要错误必须包含：

```text
时间
用户
项目
请求
异常
Stack Trace
```

---

# 19. 为什么暂时不用微服务？

项目初期不采用：

```text
User Service
Project Service
AI Service
Git Service
Document Service
```

等多个独立服务。

原因：

- 当前是个人项目。
- 部署复杂度过高。
- 不利于快速迭代。
- 微服务不是展示软件工程能力的必要条件。

第一版使用：

> **模块化单体架构**

即可。

未来规模增加后再拆分。

---

# 20. 最终技术栈

```text
前端
React
TypeScript

后端
Java
Spring Boot

数据库
MySQL
Redis

向量数据库
Qdrant

AI
LLM
Embedding
RAG
Agent

工程
Git
GitHub
Maven
Docker
Docker Compose
Linux
Nginx

测试
JUnit
Spring Boot Test
前端测试工具
```

核心目标不是堆技术，而是：

> 每一个技术都实际解决一个问题。

---

# 21. 系统架构图

## 21.1 部署架构

```text
                        Internet
                            │
                            ↓
                         Nginx
                            │
              ┌─────────────┴─────────────┐
              ↓                           ↓
     React 静态资源                 反向代理 /api
                                          │
                                          ↓
                                    Spring Boot
                                          │
              ┌───────────┬───────────────┼───────────────┐
              ↓           ↓               ↓               ↓
            MySQL        Redis         Qdrant         LLM API
         业务数据     缓存/限流      向量知识库      外部模型服务
```

## 21.2 后端分层与模块

```text
┌─────────────────────────────────────────────────────────┐
│                      Controller                          │
│   参数校验 · 身份认证 · 返回统一响应                       │
└───────────────────────────┬─────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│                       Service                            │
│   业务逻辑 · 事务 · 权限校验 · 跨模块协作                  │
└───────────────────────────┬─────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│                  Repository / Mapper                     │
│                    数据库访问                             │
└───────────────────────────┬─────────────────────────────┘
                            ↓
                    MySQL / Qdrant / Redis
```

业务模块划分：

```text
com.codeatlas
├── user          用户与认证
├── project       项目与成员权限
├── document      文档管理
├── code          代码管理
├── git           Git 仓库与提交
├── knowledge     知识库构建与检索
├── ai            AI Provider 抽象与调用
│   ├── provider  LLM / Embedding 具体实现
│   ├── rag       检索增强链路
│   └── agent     工具调用与调度
├── storage       文件存储抽象
└── common        异常处理 · 日志 · 通用工具
```

## 21.3 RAG 数据链路

```text
【写入】
文档/代码/Git ─→ Parser 解析 ─→ Chunk 切分 ─→ Embedding ─→ Qdrant
                                                   │
                                            metadata 回指 MySQL

【查询】
用户提问 ─→ Query Embedding ─→ 向量检索（按 project_id 过滤）
       ─→ Top-K 片段 ─→ 组装 Context ─→ LLM ─→ 回答 + 引用来源
       ─→ 落库（messages / ai_execution_logs）
```

## 21.4 Agent 工具调用链路

```text
用户任务
   ↓
LLM 判断是否需要工具
   ↓
┌──────────────────────────────┐
│ SearchCodeTool               │ ← 均内置 project_id 范围限制
│ ReadFileTool                 │ ← 均执行与人工操作一致的权限校验
│ SearchDocumentTool           │
│ SearchGitCommitTool          │
│ GetProjectStructureTool      │
└──────────────────────────────┘
   ↓
记录调用日志（工具名/入参/出参/耗时/成功与否）
   ↓
继续分析 → 生成最终报告
```

> Agent 不具备写权限，不接触数据库底层，详见 [ADR-005](decisions/ADR-005-agent-readonly.md)。