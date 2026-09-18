# CodeAtlas 部署指南

本文档说明如何用 Docker 一键部署完整的 CodeAtlas 系统。

配套文件：

| 文件 | 作用 |
|---|---|
| `docker-compose.yml` | 完整系统编排（6 个服务） |
| `docker-compose.dev.yml` | 仅基础设施，供本地开发使用 |
| `backend/Dockerfile` | 后端多阶段构建 |
| `frontend/Dockerfile` | 前端多阶段构建 |
| `frontend/nginx.conf` | 静态托管 + `/api` 反向代理 |
| `.env.example` | 环境变量模板 |
| `docker-compose.prod.yml` | 生产覆盖：收紧端口 + Caddy 自动 HTTPS 入口 |
| `Caddyfile` | 公网 HTTPS 入口配置（自动签发证书） |
| `deploy.sh` | Linux 一键部署脚本 |

---

## 一、部署形态

```text
                  ┌──────────────────────────────────────────┐
   浏览器 ────────▶│ frontend (Nginx :80 → 宿主 8081)          │
                  │   ├─ /            托管 React 静态资源      │
                  │   └─ /api/       反向代理到 backend        │
                  └────────────────┬─────────────────────────┘
                                   │ codeatlas-net（内部网络）
                  ┌────────────────▼─────────────────────────┐
                  │ backend (Spring Boot :8080 → 宿主 8080)   │
                  └───┬──────────┬──────────┬────────────────┘
                      │          │          │
              ┌───────▼──┐ ┌─────▼────┐ ┌───▼──────┐  ┌──────────────┐
              │ MySQL    │ │ Redis    │ │ Qdrant   │  │ Ollama       │
              │ :3306    │ │ :6379    │ │ :6333    │  │ :11434       │
              └──────────┘ └──────────┘ └──────────┘  └──────────────┘
                 （均不对宿主机暴露端口，仅容器网络内可达）
```

要点：

- **前端与接口同源**。浏览器只访问 Nginx，`/api` 由 Nginx 转发到后端容器，因此生产环境不涉及跨域。
- **数据库、缓存、向量库、Ollama 均不暴露到宿主机**，只有前端（8081）与后端（8080）对外映射端口。
- LLM（DeepSeek）是外部 API，需要出网；Embedding 使用容器内的 Ollama，可离线运行。

---

## 二、前置条件

| 项目 | 要求 |
|---|---|
| Docker | 24 及以上，含 Compose v2（`docker compose` 子命令） |
| 内存 | 建议 4GB 以上（MySQL 1G + Qdrant 300M + Ollama 1G + 构建开销） |
| 磁盘 | 建议 6GB 以上（镜像约 2GB，Ollama 模型约 1.2GB，其余为数据卷） |
| DeepSeek API Key | 用于问答 / 代码审查 / Git 摘要 / Agent；缺失时这些功能不可用 |
| 网络 | 首次构建需拉取镜像与依赖；运行期需能访问 DeepSeek API |

---

## 三、快速开始

### 第 1 步：配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，**至少**补上这三项：

```ini
MYSQL_ROOT_PASSWORD=<换成自己的密码>
DEEPSEEK_API_KEY=sk-xxxxxxxx
JWT_SECRET=<随机字符串，32 字节以上>
```

> `.env` 已被 `.gitignore` 忽略，不会进入版本库。除 `JWT_SECRET` 外的变量都有默认值，不创建 `.env` 也能启动；`JWT_SECRET` 留空时后端会生成随机密钥（Token 不可伪造，但重启后需重新登录）。生产环境必须覆盖以上三项。

> **`.env` 不是唯一途径**：`docker-compose.yml` 中一律写成 `${VAR:-默认值}`，Compose 会**先读宿主机环境变量**，读不到才用默认值。所以只要宿主机已有这些变量，不创建 `.env` 也能正常工作。例如把 Key 设为 Windows 用户级环境变量：
>
> ```powershell
> [Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-xxx", "User")
> ```
>
> 验证当前生效值（`docker inspect` 读的是容器实际拿到的值，注意别把输出贴给别人）：
>
> ```bash
> docker inspect codeatlas-backend --format "{{range .Config.Env}}{{println .}}{{end}}" | grep DEEPSEEK
> ```
>
> ⚠️ **优先级：宿主机环境变量 > `.env` 文件**。两者同时存在时以宿主机为准。因此「改了 `.env` 却不生效」最常见的原因，就是宿主机已有同名变量把它盖掉了。改完任一处后都要 `docker compose up -d` 重建容器才会生效——已经运行的容器不会自动感知新值。

### 第 2 步：构建并启动

```bash
docker compose up -d
```

首次执行会构建前后端镜像，耗时取决于网络与机器性能。后端容器启动时会通过 `schema.sql` 自动建表（建表语句带 `IF NOT EXISTS`，可重复执行），并初始化 `ROLE_USER` / `ROLE_ADMIN`。

### 第 3 步：拉取 Embedding 模型

这一步只需执行一次。模型约 1.2GB，存放在 `ollama-data` 数据卷中：

```bash
docker compose exec ollama ollama pull bge-m3
```

> **不执行这一步，知识库构建会失败**，AI 问答也没有可检索的数据。这是部署后最常见的遗漏。

### 第 4 步：验证

```bash
# 1. 业务容器应全部为 healthy（qdrant / ollama 未定义健康检查，Running 即可）
docker compose ps

# 2. 后端存活 + 依赖连通性：db / redis 均为 up 才算正常
curl http://localhost:8080/health
# {"status":"UP","db":"up","redis":"up"}

# 3. 经 Nginx 访问，验证反向代理链路（应与第 2 步结果一致）
curl http://localhost:8081/health

# 4. 验证 SPA 路由回退，应输出 200 而非 404
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/projects/1
```

浏览器打开 **http://localhost:8081** 即可注册登录。也可以用命令行跑一遍完整业务链路：

```bash
# 注册，返回 "code":0 即成功
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","email":"demo@example.com","password":"Passw0rd123"}'

# 登录，返回 data.token（JWT）即成功
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"Passw0rd123"}'
```

注册与登录都通过，说明「Nginx → 后端 → MySQL / Redis」全链路已打通。

> Windows PowerShell 下请把 `curl` 写成 `curl.exe`，并使用单引号包裹 JSON，避免反引号转义问题。

---

## 四、服务与端口

| 服务 | 容器名 | 宿主端口 | 容器内端口 | 说明 |
|---|---|---|---|---|
| frontend | `codeatlas-frontend` | **8081** | 80 | Nginx，唯一对外的业务入口 |
| backend | `codeatlas-backend` | **8080** | 8080 | Spring Boot，`/health` 用于健康检查 |
| mysql | `codeatlas-mysql` | 不暴露 | 3306 | 业务数据库 |
| redis | `codeatlas-redis` | 不暴露 | 6379 | 缓存 / 登录限流 / Token 黑名单 |
| qdrant | `codeatlas-qdrant` | 不暴露 | 6333 | 向量知识库 |
| ollama | `codeatlas-ollama` | 不暴露 | 11434 | 本地 Embedding 服务 |

容器之间通过服务名通信（如后端连的是 `mysql:3306`，而非 `localhost:23306`），这是容器网络中的正常行为。

**如需连接 MySQL 排查数据**：

```bash
docker compose exec mysql mysql -uroot -p codeatlas
```

**如需使用 Qdrant 控制台**：在 `docker-compose.yml` 的 `qdrant` 服务下临时加上端口映射：

```yaml
    ports:
      - "6333:6333"
```

然后访问 http://localhost:6333/dashboard 。

---

## 五、环境变量

完整清单见 `.env.example`。以下为部署相关的主要变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | `root` | 数据库密码，同时作为后端连接密码 |
| `DB_NAME` | `codeatlas` | 数据库名 |
| `JWT_SECRET` | 空（启动时随机生成） | HS256 要求 ≥32 字节。留空则生成本进程随机密钥，重启后登录态失效；使用曾公开的占位值或长度不足会**拒绝启动** |
| `JWT_EXPIRATION` | `7200` | Token 有效期（秒） |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8081` | 跨域白名单，逗号分隔，禁止 `*` |
| `DEEPSEEK_API_KEY` | 空 | 不配置则 AI 生成类功能不可用 |
| `GITHUB_TOKEN` | 空 | 同步私有仓库时需要；留空仅能访问公开仓库 |
| `AI_EMBEDDING_BASE_URL` | `http://ollama:11434` | 改用宿主机 Ollama 时设为 `http://host.docker.internal:11434` |
| `AI_EMBEDDING_DIMENSION` | `1024` | 必须与 Embedding 模型一致，更换模型后需清空知识库重建 |
| `FRONTEND_PORT` | `8081` | 前端对外端口 |
| `BACKEND_PORT` | `8080` | 后端对外端口 |

---

## 六、常用运维命令

```bash
# 查看全部服务状态
docker compose ps

# 实时查看日志
docker compose logs -f backend
docker compose logs -f frontend

# 只看最近 100 行
docker compose logs --tail=100 backend

# 重新构建并滚动更新（改代码后）
docker compose up -d --build

# 单独重建某个服务
docker compose up -d --build backend

# 重启单个服务
docker compose restart backend

# 停止（保留数据卷）
docker compose down

# 停止并删除数据卷（清空所有数据，慎用）
docker compose down -v

# 进入后端容器
docker compose exec backend sh
```

---

## 七、数据持久化

所有状态都存放在具名数据卷中，`docker compose down` 不会丢失数据：

| 数据卷 | 内容 |
|---|---|
| `codeatlas_mysql-data` | 数据库表与数据 |
| `codeatlas_redis-data` | Redis AOF 持久化文件 |
| `codeatlas_qdrant-data` | 向量集合 |
| `codeatlas_ollama-data` | Embedding 模型文件（约 1.2GB） |
| `codeatlas_backend-uploads` | 上传的文档与源码文件 |

**备份数据库**：

```bash
docker compose exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" codeatlas' > codeatlas-backup.sql
```

**恢复数据库**：

```bash
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" codeatlas' < codeatlas-backup.sql
```

**备份上传文件**（以 Windows PowerShell 为例）：

```bash
docker run --rm -v codeatlas_backend-uploads:/data -v ${PWD}:/backup alpine \
  tar czf /backup/uploads.tar.gz -C /data .
```

---

## 八、端口冲突处理

前端 8081 或后端 8080 被占用时，不必改动编排文件，在 `.env` 中改端口即可：

```ini
FRONTEND_PORT=9090
BACKEND_PORT=9091
```

改完后需要同步调整 `CORS_ALLOWED_ORIGINS` 为新的前端地址，并重新启动：

```bash
docker compose up -d
```

---

## 九、生产环境注意事项

本项目默认配置面向**个人 / 小团队内网或单机演示**。若要对外提供访问，请至少做到以下几点：

1. **必须覆盖 `JWT_SECRET`**
   本项目不提供内置默认密钥：默认值一旦写进仓库就等于公开，攻击者可用它伪造任意用户（含管理员）的 Token，且服务端无法分辨真伪。
   留空时后端会生成随机密钥（不可伪造，但重启后需重新登录、多实例不互通）；`./deploy.sh` 会自动生成并写入 `.env`。
   轮换该值会让所有已签发的 Token 立即失效，用户需重新登录。

2. **必须更换 `MYSQL_ROOT_PASSWORD`**，不要沿用默认的 `root`。

3. **不要直接把 8080 暴露到公网**
   后端是纯 API 服务且带 `/health` 等探测端点。对外只应放行前端端口，由 Nginx 统一转发。建议在 `docker-compose.yml` 中删除后端的 `ports` 映射，或改为只绑定回环地址：

   ```yaml
       ports:
         - "127.0.0.1:8080:8080"
   ```

4. **在前端容器之前再加一层 TLS**
   当前 Nginx 只监听 80，不处理证书。生产环境建议在其前置一层入口网关（云负载均衡 / 独立 Nginx / Caddy）完成 HTTPS 卸载，并把 `X-Forwarded-Proto` 透传下来。

5. **收紧 CORS 白名单**
   把 `CORS_ALLOWED_ORIGINS` 改为真实访问域名，逗号分隔，禁止使用 `*`。

6. **评估 Ollama 的资源占用**
   Ollama 在无 GPU 的机器上以 CPU 推理，向量化速度较慢。大批量文档的首次知识库构建会比较耗时；如条件允许，可将 `AI_EMBEDDING_BASE_URL` 指向带 GPU 的独立 Ollama 实例。

7. **限制容器资源**
   建议为 `mysql`、`ollama`、`backend` 设置内存上限，避免单个服务耗尽宿主机内存：

   ```yaml
       deploy:
         resources:
           limits:
             memory: 1g
   ```

8. **定期备份数据卷**，尤其是 `mysql-data` 与 `qdrant-data`。

---

## 十、故障排查

| 现象 | 原因与处理 |
|---|---|
| `docker compose up -d` 后 backend 反复重启 | 看日志 `docker compose logs backend`。多为数据库未就绪或密码不一致：确认 `.env` 中 `MYSQL_ROOT_PASSWORD` 未被中途修改（MySQL 只在数据卷首次初始化时读取该变量，之后改密码需先 `docker compose down -v` 清空数据）。 |
| 前端能打开但接口全报 502 | 后端未就绪或已崩溃。先 `curl http://localhost:8080/health` 确认后端存活。 |
| 浏览器控制台报 CORS 错误 | 仅发生在直连后端调试时。使用完整部署（同源）不会有此问题；如确需跨域，把前端地址加入 `CORS_ALLOWED_ORIGINS`。 |
| 知识库构建失败 | 多为 Ollama 未拉取模型，执行 `docker compose exec ollama ollama pull bge-m3`。其次确认后端到 Ollama 可达：`docker exec codeatlas-backend curl -s http://ollama:11434/api/tags`，正常应返回模型列表 JSON。 |
| AI 功能报「缺少 API Key」 | 后端没拿到 `DEEPSEEK_API_KEY`。它有两个来源，按优先级排查：① 宿主机环境变量 `$env:DEEPSEEK_API_KEY`；② 项目根目录 `.env`。用 `docker inspect codeatlas-backend --format "{{range .Config.Env}}{{println .}}{{end}}"` 查看容器实际变量（找 `DEEPSEEK_API_KEY=` 那一行）。为空则设置后 `docker compose up -d` 重建容器；注意宿主机变量会覆盖 `.env`。 |
| 多个项目导入同一个仓库时，提交列表明显偏少 | 旧版本 `git_commits` 的唯一键是 `commit_hash` 全局唯一，后导入的项目会把所有提交判为「已存在」而跳过。当前版本已改为 `(repo_id, commit_hash)`，后端启动时经 `schema.sql` 自动迁移。核查是否生效：`docker compose exec mysql mysql -uroot -p codeatlas -e "show index from git_commits"`，应能看到 `uk_git_commits_repo_hash` 且不再有 `uk_git_commits_hash`。之后在页面重新点「同步提交」即可补齐（已入库的提交会跳过，不会产生重复）。 |
| 上传大文件报 413 | 文档上限 20MB，Nginx 侧 `client_max_body_size` 已设为 25m。若自行调大后端上限，需同步调整 `frontend/nginx.conf`。 |
| 前端刷新子页面变 404 | 检查 `frontend/nginx.conf` 是否被正确挂载（`try_files $uri $uri/ /index.html` 负责 SPA 回退）。 |
| 镜像构建很慢或卡在下载依赖 | 首次构建需拉取 Maven / npm 依赖。可配置国内镜像加速，或改用已有基础镜像缓存重试。 |
| 报 `failed to resolve reference "docker.io/..."`、`unexpected status ... 403` 或拉取超时 | 宿主网络无法直连 Docker Hub，属**环境问题**而非配置错误，与项目配置无关。处理方式：① 在 Docker Desktop → Settings → Docker Engine 的 JSON 中加入 `"registry-mirrors": ["https://<可用加速源>"]` 后重启；② 临时经加速源拉取再改回官方名。**路径规则有别**：官方基础镜像带 `library/` 前缀（`docker pull <源>/library/nginx:1.27-alpine`），第三方镜像不带（`docker pull <源>/ollama/ollama:latest`），改名时一律去掉 `<源>/` 前缀还原成官方名。需要这样处理的共 5 个镜像：`maven:3.9-eclipse-temurin-17`、`eclipse-temurin:17-jre-alpine`、`node:20-alpine`、`nginx:1.27-alpine`、`ollama/ollama:latest`。 |
| 磁盘被占满 | 主要是 `ollama-data`（模型）与构建缓存。`docker system prune` 清理悬空镜像，注意不要误删数据卷。 |

---

## 十一、本地开发（不使用完整部署）

只启动依赖服务，前后端在宿主机上直接跑，便于热更新调试：

```bash
docker compose -f docker-compose.dev.yml up -d
```

该文件只含 MySQL（宿主 23306）、Redis（宿主 16379）、Qdrant（宿主 6333），
与 `application.yml` 的默认端口一致，后端无需额外配置。随后：

```bash
# 后端
cd backend && mvn spring-boot:run

# 前端
cd frontend && npm install && npm run dev
# 访问 http://localhost:5173
```

Embedding 服务在此模式下需使用宿主机安装的 Ollama（`ollama serve` + `ollama pull bge-m3`）。

---

## 十二、公网发布（VPS + Caddy 自动 HTTPS）

若要把系统部署到云服务器、通过一个固定链接对外访问，请使用生产覆盖配置 + Caddy：

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

或在 Linux 服务器上直接运行一键脚本 `./deploy.sh`。完整步骤、域名解析与安全加固见
**[生产部署（VPS + Caddy）](deployment-vps.md)**。
