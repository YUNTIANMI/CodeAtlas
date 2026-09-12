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

第一阶段支持：`java`、`cpp`、`py`、`js`、`ts`。

---

## 7. 知识库

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/projects/{id}/knowledge/build` | 触发知识库构建 |
| GET | `/api/v1/projects/{id}/knowledge/status` | 构建状态与进度 |
| DELETE | `/api/v1/projects/{id}/knowledge` | 清空知识库 |

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
| GET | `/api/v1/conversations` | 会话列表 |
| GET | `/api/v1/conversations/{id}/messages` | 会话消息 |

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
| POST | `/api/v1/projects/{id}/git/sync` | 同步 Commits |
| GET | `/api/v1/projects/{id}/git/commits` | 提交列表 |
| GET | `/api/v1/git/commits/{id}/summary` | AI 提交摘要 |

> 接口为**只读**语义，不提供 Push、分支修改等写操作。

---

## 12. 权限校验约定

所有带 `projectId` 的接口，Service 层必须执行：

```text
1. 从 Token 解析 userId
2. 查询 project_members 确认 (project_id, user_id) 存在
3. 校验该操作所需的最低角色
4. 通过后执行业务逻辑，否则返回 403
```

**禁止**：仅凭请求参数中的 `projectId` 直接查询数据。

---

## 13. 限流

| 场景 | 限制 |
|---|---|
| 登录 | 5 次 / 分钟 / IP |
| AI 问答 | 20 次 / 分钟 / 用户 |
| Code Review | 10 次 / 分钟 / 用户 |
| 文件上传 | 30 次 / 分钟 / 用户 |

超出返回 `429`。
