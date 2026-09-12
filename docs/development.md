# AI 软件工程研发助手
## 开发阶段计划与开发规范

**版本：** V1.0  
**开发方式：** 个人开发 + AI 协同开发  
**核心原则：** 先完成可运行 MVP，再逐步增加复杂能力。

---

# 1. 总体开发路线

整个项目分为：

```text
Phase 0 需求与架构
        ↓
Phase 1 基础工程
        ↓
Phase 2 用户与项目管理
        ↓
Phase 3 文档与代码管理
        ↓
Phase 4 RAG 知识库
        ↓
Phase 5 AI 问答
        ↓
Phase 6 AI Code Review
        ↓
Phase 7 Git 分析
        ↓
Phase 8 Agent
        ↓
Phase 9 测试与安全
        ↓
Phase 10 Docker 部署
```

不要一开始开发 Agent。

---

# 2. Phase 0：需求与架构

目标：

> 明确系统边界，不开始堆代码。

完成：

- 需求文档
- 技术选型
- 系统架构图
- 数据库 ER 图
- API 初步设计
- Git 仓库建立

输出：

```text
README.md
docs/
├── requirements.md
├── architecture.md
├── database.md
├── api.md
└── development.md
```

---

# 3. Phase 1：建立基础工程

## 3.1 Git

建立：

```text
main
develop
feature/*
```

开发流程：

```text
feature
 ↓
开发
 ↓
commit
 ↓
merge
 ↓
develop
 ↓
release
 ↓
main
```

每次完成一个具有明确意义的功能后提交 Commit。

例如：

```text
feat: create project module
fix: resolve project permission bug
refactor: simplify document service
test: add project service tests
docs: update API documentation
```

---

# 4. Phase 2：用户系统

开发：

```text
User
Auth
Permission
```

实现：

```text
注册
登录
退出
身份认证
权限验证
```

数据库：

```text
users
roles
user_roles
```

完成 API：

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
GET  /api/user/me
```

这一阶段结束后，应能够：

```text
注册
 ↓
登录
 ↓
获取身份
 ↓
访问受保护 API
```

---

# 5. Phase 3：项目管理

建立：

```text
Project
ProjectMember
ProjectPermission
```

功能：

```text
创建项目
查看项目
修改项目
删除项目
邀请成员
修改成员权限
```

完成：

```text
ProjectController
ProjectService
ProjectRepository
```

要求：

> Controller 不直接操作数据库。

---

# 6. Phase 4：文档与代码管理

功能：

```text
上传文档
删除文档
查看文档
上传代码
查看代码
```

支持：

```text
md
txt
pdf
java
cpp
py
js
ts
```

上传以后进入：

```text
文件
 ↓
解析
 ↓
文本内容
 ↓
Chunk
```

这一阶段暂时不接 AI。

先保证：

> 文件能够正确解析、保存和检索。

---

# 7. Phase 5：知识库与 RAG

这是第一个 AI 核心阶段。

流程：

```text
Document / Code
 ↓
Parser
 ↓
Text Chunk
 ↓
Embedding
 ↓
Qdrant
```

查询：

```text
Question
 ↓
Embedding
 ↓
Vector Search
 ↓
Top K Context
 ↓
LLM
 ↓
Answer
```

回答必须保存：

```text
问题
回答
引用
模型
时间
```

---

# 8. Phase 6：AI 项目问答

建立：

```text
Conversation
Message
AIExecution
Citation
```

前端提供 Chat 页面。

例如：

```text
┌──────────────────────────────┐
│ AI Project Assistant         │
├──────────────────────────────┤
│ 用户：登录流程是什么？       │
│                              │
│ AI：登录由三个模块组成……     │
│                              │
│ 来源：                        │
│ UserController.java          │
│ UserService.java             │
└──────────────────────────────┘
```

重点：

> AI 回答必须尽量基于项目知识，而不是单纯依赖模型记忆。

---

# 9. Phase 7：AI Code Review

输入：

```text
Code
Git Diff
Commit
```

输出：

```text
严重程度
文件
行号
问题
原因
建议
```

定义统一结果结构：

```text
ReviewResult
├── severity
├── file
├── line
├── category
├── description
└── suggestion
```

这样前端可以结构化展示，而不是单纯显示一大段 AI 文本。

---

# 10. Phase 8：Git 分析

连接 GitHub。

首先实现：

```text
导入 Repository
 ↓
同步 Repository
 ↓
获取 Commit
 ↓
保存 Commit
```

然后：

```text
Commit
 ↓
Diff
 ↓
AI
 ↓
Commit Summary
```

例如：

> 本次提交主要修改用户认证模块，并增加 Token 过期处理。

---

# 11. Phase 9：Agent

只有前面的系统都稳定后再开发 Agent。

设计工具：

```text
search_code
read_file
search_document
search_git_commit
get_project_structure
```

Agent 流程：

```text
用户任务
 ↓
AI 判断是否需要工具
 ↓
调用 Tool
 ↓
获得结果
 ↓
继续分析
 ↓
最终回答
```

必须记录：

```text
Tool Name
Input
Output
Execution Time
Success / Failure
```

---

# 12. Phase 10：测试

测试不能等到最后一天。

每完成一个模块立即测试。

例如 ProjectService：

```text
创建成功
重复创建
无权限创建
项目不存在
项目删除
成员权限
```

至少保证：

```text
正常流程
异常流程
权限流程
边界情况
```

---

# 13. Phase 11：安全

重点检查：

```text
登录认证
权限控制
SQL Injection
XSS
CSRF
文件上传
Token
敏感数据
```

尤其测试：

> 用户 A 是否能够访问用户 B 的项目。

这是实际项目中非常重要的一类问题。

---

# 14. Phase 12：Docker 部署

编写：

```text
Dockerfile
docker-compose.yml
```

启动：

```text
Frontend
Backend
MySQL
Redis
Qdrant
Nginx
```

最终实现：

```bash
docker compose up -d
```

可以启动完整系统。

---

# 15. AI 协同开发规范

这是本项目非常重要的一部分。

AI 不能直接代替开发者做所有决定。

采用：

```text
需求
 ↓
开发者分析
 ↓
AI 辅助方案
 ↓
开发者确认
 ↓
AI 编码
 ↓
开发者 Review
 ↓
测试
 ↓
Debug
 ↓
Commit
```

---

# 16. 给 AI 的任务必须尽量小

不推荐：

> 帮我把整个后台系统写出来。

推荐：

> 根据当前 Project 实体和数据库设计，实现 ProjectRepository，并解释每个方法的作用。

然后：

> 根据 ProjectRepository 实现 ProjectService，要求保持当前项目分层结构，不修改其他模块。

这样更容易：

- 控制代码质量。
- 发现问题。
- 理解代码。
- 避免 AI 大规模重构。

---

# 17. AI 生成代码后的检查流程

每次 AI 生成代码后：

```text
① 看代码
② 看依赖
③ 看异常处理
④ 看权限
⑤ 看数据库访问
⑥ 运行测试
⑦ 查看日志
⑧ 检查边界情况
```

不允许：

> AI 生成 → 复制 → 运行 → 成功 → 结束。

---

# 18. 每个功能都留下工程记录

例如：

```text
docs/
└── decisions/
    ├── ADR-001-database.md
    ├── ADR-002-vector-db.md
    └── ADR-003-auth.md
```

记录：

```text
为什么选 MySQL？
为什么使用 Qdrant？
为什么不使用微服务？
为什么 Agent 只读？
```

这些内容未来非常适合用于面试。

---

# 19. 推荐的项目目录

后端：

```text
backend/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── ...
│   │           ├── controller
│   │           ├── service
│   │           ├── repository
│   │           ├── entity
│   │           ├── dto
│   │           ├── security
│   │           ├── ai
│   │           ├── git
│   │           ├── document
│   │           └── common
│   └── test/
└── pom.xml
```

前端：

```text
frontend/
├── src/
│   ├── components
│   ├── pages
│   ├── layouts
│   ├── services
│   ├── hooks
│   ├── types
│   ├── stores
│   └── utils
└── package.json
```

文档：

```text
docs/
├── requirements.md
├── architecture.md
├── database.md
├── api.md
├── development.md
└── decisions/
```

---

# 20. 版本规划

## V0.1

```text
用户
项目
Git
基础前端
基础后端
MySQL
```

## V0.2

```text
文档
代码
文件解析
```

## V0.3

```text
Embedding
Vector DB
RAG
```

## V0.4

```text
AI 项目问答
引用
```

## V0.5

```text
AI Code Review
```

## V0.6

```text
Git 分析
```

## V0.7

```text
Agent
Tool Calling
```

## V1.0

```text
测试
安全
Docker
部署
完整 README
项目演示
```

---

# 21. 项目完成后的最终验收

一个完整 Demo 应能够演示：

```text
登录
 ↓
创建项目
 ↓
导入 GitHub
 ↓
查看项目结构
 ↓
上传文档
 ↓
构建知识库
 ↓
向 AI 提问
 ↓
AI 检索项目代码
 ↓
AI 返回答案 + 来源
 ↓
提交代码进行 Review
 ↓
AI 输出结构化 Review
 ↓
导入 Git Commit
 ↓
AI 分析 Commit
```

最终项目需要具备：

```text
可运行
可部署
可测试
可维护
可解释
```

而不是只有：

```text
“AI 能回答问题”
```