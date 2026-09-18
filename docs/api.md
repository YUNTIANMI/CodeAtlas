# API 设计说明书

**版本：** V1.0
**协议：** HTTP/HTTPS
**数据格式：** JSON（`Content-Type: application/json`）

---

## 1. 通用约定

### 1.1 Base URL

```text
/api/v1
```

### 1.2 认证方式

除登录、注册外，所有接口需在请求头携带 Token：

```http
Authorization: Bearer <token>
```

### 1.3 统一响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1757673600000
}
```

失败：

```json
{
  "code": 403,
  "message": "无权限访问该项目",
  "data": null,
  "timestamp": 1757673600000
}
```

> **HTTP 状态码与业务码同时生效**：失败响应除了在 `code` 中给出业务错误码（如越权返回 `2002`），
> HTTP 状态码也会返回对应语义（如上例为 `403`），便于网关、浏览器与前端拦截器识别。

| HTTP 状态 | 适用场景 | 典型业务码 |
|---|---|---|
| 400 | 参数校验失败、文件为空 / 类型不支持 / 超过大小 | 400 · 3001 · 3002 · 3003 |
| 401 | 未携带 Token、Token 无效或已过期、已登出、密码错误 | 401 · 1003 · 1004 |
| 403 | 非项目成员、项目内角色权限不足 | 403 · 2002 · 2003 · 2006 · 2007 |
| 404 | 用户 / 项目 / 文档 / 代码文件 / 会话 / 审查 / Git 仓库不存在 | 404 · 1005 · 2001 · 3004 · 3005 · 6001 · 7001 · 8001 |
| 409 | 用户名 / 邮箱已存在、已是项目成员、重复配置 Git 仓库 | 409 · 1001 · 1002 · 2004 · 8005 |
| 429 | 登录失败次数过多（默认 5 次锁定 15 分钟）、接口限流 | 429 |
| 500 | 服务端异常、AI 服务失败、向量库不可用 | 500 · 5001 · 5002 · 5003 |

> 未预期的服务端异常只返回 `500` 与通用文案，异常堆栈仅记录在服务端日志，不对外泄露。

### 1.4 分页约定

请求参数：`page`（从 1 开始，默认 1）、`size`（默认 20，最大 100）

分页响应：

```json
{
  "code": 0,
  "data": {
    "items": [],
    "total": 128,
    "page": 1,
    "size": 20
  }
}
```

### 1.5 错误码

| code | 含义 |
|---|---|
| 0 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 / Token 失效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突（如项目名重复） |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |
| 5001 | AI 服务调用失败 |
| 5002 | AI 服务超时 |
| 5003 | 向量库不可用 |

---

## 2. 认证与用户

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/register` | 注册 |
| POST | `/api/v1/auth/login` | 登录，返回 Token |
| POST | `/api/v1/auth/logout` | 退出 |
| GET | `/api/v1/users/me` | 获取当前用户信息 |

**POST /auth/register**

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "********"
}
```

**POST /auth/login** 响应

```json
{
  "code": 0,
  "data": {
    "token": "eyJhbGciOi...",
    "expiresIn": 7200,
    "user": { "id": 1, "username": "alice" }
  }
}
```

---

## 3. 项目管理

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/v1/projects` | 创建项目 | 登录用户 |
| GET | `/api/v1/projects` | 我的项目列表 | 登录用户 |
| GET | `/api/v1/projects/{id}` | 项目详情 | 项目成员 |
| PUT | `/api/v1/projects/{id}` | 修改项目 | ADMIN |
| DELETE | `/api/v1/projects/{id}` | 删除项目（软删） | OWNER |

**POST /projects**

```json
{
  "name": "个人项目管理平台",
  "description": "基于 Spring Boot 与 React 的项目管理工具",
  "projectType": "WEB",
  "techStack": ["Java", "Spring Boot", "React", "MySQL"]
}
```

---

## 4. 项目成员

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/projects/{id}/members` | 成员列表 | 成员 |
| POST | `/api/v1/projects/{id}/members` | 邀请成员 | ADMIN |
| PUT | `/api/v1/projects/{id}/members/{userId}` | 修改角色 | ADMIN |
| DELETE | `/api/v1/projects/{id}/members/{userId}` | 移除成员 | ADMIN |

**POST /projects/{id}/members**

```json
{ "userId": 12, "role": "MEMBER" }
```

---

## 5. 文档管理

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/projects/{id}/documents` | 上传文档（multipart） |
| GET | `/api/v1/projects/{id}/documents` | 文档列表 |
| GET | `/api/v1/projects/{id}/documents/search` | 按文件名关键字检索（`keyword`） |
| GET | `/api/v1/documents/{docId}` | 文档详情 |
| DELETE | `/api/v1/documents/{docId}` | 删除文档 |

支持类型：`md`、`txt`、`pdf`；单文件上限 20MB。

---

## 6. 代码管理

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/projects/{id}/code` | 上传代码文件 |
| GET | `/api/v1/projects/{id}/code` | 代码文件列表 |
| GET | `/api/v1/code/{fileId}` | 查看代码内容 |
| GET | `/api/v1/projects/{id}/code/structure` | 项目目录结构 |
| DELETE | `/api/v1/code/{fileId}` | 删除代码文件 |

**上传参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | file | 是 | 代码文件本体 |
| `path` | string | 否 | 入库相对路径，建议写成完整路径如 `src/main/java/UserService.java`；留空则仅用文件名 |

`path` 会做规整：统一分隔符、剔除空段与 `.` / `..`，长度上限 500 字符（与 `code_files.file_path` 列宽一致）。
由于 `code_files` 上有 `UNIQUE(project_id, file_path)`，同一路径重复上传会覆盖旧记录，可用于增量重传。

**支持类型**：`java`、`cpp`、`cc`、`cxx`、`c`、`h`、`hpp`、`py`、`js`、`jsx`、`ts`、`tsx`；单文件上限 5MB。

前端「上传源码目录」按 `webkitdirectory` 选取整个目录，逐个文件串行调用本接口，并在本地完成过滤：

- 依赖与构建产物目录：`node_modules`、`vendor`、`dist`、`build`、`target`、`venv`、`__pycache__`、`.git`、`.idea` 等
- 测试目录：`test`、`tests`、`__tests__`、`spec`、`mocks` 等（可在界面上勾选保留）
- 生成文件：`*.min.js`、`*.d.ts`、`*.map`、`*.pb.go` 等

上传路径会自动锚定到 `src` 段，因此无论用户选项目根目录还是直接选 `src`，入库后都是 `src/main/java/...` 形式。

---

## 7. 知识库

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/projects/{id}/knowledge/build` | 触发知识库构建 |
| GET | `/api/v1/projects/{id}/knowledge/status` | 构建状态与进度 |
| POST | `/api/v1/projects/{id}/knowledge/search` | 向量检索，只返回片段，不生成答案 |
| POST | `/api/v1/projects/{id}/knowledge/ask` | RAG 问答：检索 + 生成 + 引用 |
| DELETE | `/api/v1/projects/{id}/knowledge` | 清空知识库 |

**检索 / 问答请求体**（`search` 与 `ask` 共用）

```json
{ "query": "登录流程是怎么实现的？", "topK": 5 }
```

`query` 必填；`topK` 默认 5。

**构建状态响应**

```json
{
  "status": "BUILDING",
  "total": 240,
  "indexed": 156,
  "failed": 2
}
```

---

## 8. AI 项目问答

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/projects/{id}/chat` | 发起问答 |
| GET | `/api/v1/projects/{id}/conversations` | 项目内的会话列表 |
| GET | `/api/v1/conversations/{id}/messages` | 会话消息 |
| DELETE | `/api/v1/conversations/{id}` | 删除会话 |

**POST /projects/{id}/chat**

```json
{ "question": "这个项目的登录流程是什么？", "conversationId": 8 }
```

响应：

```json
{
  "code": 0,
  "data": {
    "answer": "登录流程由 UserController、UserService、JwtTokenProvider 三个模块组成……",
    "citations": [
      {
        "type": "CODE",
        "filePath": "src/main/java/UserController.java",
        "lines": "32-58"
      },
      {
        "type": "DOCUMENT",
        "name": "API 设计说明书.md"
      }
    ],
    "model": "gpt-4o-mini",
    "tokenUsage": { "prompt": 1820, "completion": 320 }
  }
}
```

> `citations` 为必返字段。若检索不到任何项目上下文，需在回答中明确说明"未在项目资料中找到依据"，禁止模型凭记忆自由生成。

---

## 9. AI Code Review

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/projects/{id}/review` | 提交代码审查 |
| GET | `/api/v1/projects/{id}/reviews` | 审查结果列表 |
| GET | `/api/v1/reviews/{id}` | 审查结果详情 |

**POST /projects/{id}/review**

```json
{
  "sourceType": "COMMIT",
  "sourceRef": "a3f9c21",
  "content": "（可选：直接提交的代码或 Diff）"
}
```

响应（`ReviewResult` 结构化数组）：

```json
{
  "code": 0,
  "data": {
    "reviewId": 77,
    "results": [
      {
        "severity": "MAJOR",
        "category": "SECURITY",
        "filePath": "src/main/java/UserService.java",
        "line": 42,
        "description": "使用字符串拼接构造 SQL 查询，存在 SQL 注入风险",
        "risk": "攻击者可通过构造用户名绕过认证",
        "suggestion": "改用 PreparedStatement 或 JPA 参数绑定"
      }
    ]
  }
}
```

---

## 10. Bug 分析

> ⬜ **规划中，尚未实现。** 本节为接口设计约定，代码中暂无对应 Controller。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/projects/{id}/bug-analysis` | 提交错误日志分析 |
| GET | `/api/v1/projects/{id}/bug-reports` | 分析报告列表 |
| GET | `/api/v1/bug-reports/{id}` | 报告详情 |

**请求**

```json
{
  "errorLog": "java.lang.NullPointerException: ...\n at UserService.login(UserService.java:42)",
  "relatedCode": ""
}
```

**响应**

```json
{
  "code": 0,
  "data": {
    "summary": "登录时未对用户对象做空值判断",
    "possibleCauses": ["数据库中不存在该用户", "查询返回 null 未处理"],
    "relatedFiles": ["UserService.java", "UserRepository.java"],
    "callChain": "UserController.login → UserService.login → UserRepository.findByUsername",
    "suggestions": "在 login 方法开头增加 null 检查并抛出业务异常",
    "testPlan": "补充用户不存在的单测用例"
  }
}
```

---

## 11. Git 集成

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/projects/{id}/git` | 配置仓库 |
| POST | `/api/v1/projects/{id}/git/sync` | 同步 Commits，可选 `limit`（默认 30，最大 500），超过 100 条自动翻页拉取 |
| GET | `/api/v1/projects/{id}/git/commits` | 提交列表 |
| GET | `/api/v1/git/commits/{id}` | 提交详情 |
| GET | `/api/v1/git/commits/{id}/summary` | AI 提交摘要 |

> 接口为**只读**语义，不提供 Push、分支修改等写操作。

---

## 12. Agent 多步检索

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/projects/{id}/agent/run` | 执行任务，由模型自主选择工具 |
| GET | `/api/v1/projects/{id}/agent/tool-calls` | 工具调用轨迹 |

**请求**

```json
{ "task": "这个项目的登录流程是怎么实现的？" }
```

工具集固定为 5 个**只读**工具，不存在任何写操作：

| 工具名 | 用途 |
|---|---|
| `search_document` | 按关键字搜索已上传的文档（md / txt / pdf），返回名称与简介 |
| `search_code` | 按关键字搜索源代码，返回文件路径与代码片段 |
| `read_file` | 读取某个文件的完整内容，支持源代码与文档 |
| `get_project_structure` | 获取已上传代码的目录结构，了解模块划分 |
| `search_git_commit` | 查询 Git 提交记录，可按关键字过滤提交信息 |

每一步工具调用的入参、出参、耗时与错误都会落库，可通过 `tool-calls` 追溯。

---

## 13. 权限校验约定

所有带 `projectId` 的接口，Service 层必须执行：

```text
1. 从 Token 解析 userId
2. 查询 project_members 确认 (project_id, user_id) 存在
3. 校验该操作所需的最低角色
4. 通过后执行业务逻辑，否则返回 403
```

**禁止**：仅凭请求参数中的 `projectId` 直接查询数据。

---

## 14. 限流

| 场景 | 限制 | 实现状态 |
|---|---|---|
| 登录失败 | 同一账号连续失败 5 次后锁定 15 分钟 | ✅ 已实现（Redis 计数，可通过 `LOGIN_MAX_ATTEMPTS` / `LOGIN_LOCK_SECONDS` 调整） |
| AI 问答 | 20 次 / 分钟 / 用户 | ⬜ 规划中 |
| Code Review | 10 次 / 分钟 / 用户 | ⬜ 规划中 |
| 文件上传 | 30 次 / 分钟 / 用户 | ⬜ 规划中 |

超出返回 `429`。登录限流在**校验密码之前**即拒绝请求，锁定期间即使密码正确也无法登录，
避免给暴力破解留下试探窗口；登录成功后失败计数立即清零。
