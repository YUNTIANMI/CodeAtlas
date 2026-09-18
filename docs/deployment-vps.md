# CodeAtlas 生产部署：VPS + Docker + Caddy 自动 HTTPS

本文说明如何把 CodeAtlas 部署到一台云服务器（VPS），拿到一个**固定链接直接访问**。

> 完整系统的容器编排、数据卷、备份与故障排查见 [deployment.md](deployment.md)；
> 本文只聚焦「对外发布」这一层。

---

## 一、为什么是 VPS

CodeAtlas 是「有状态 + 重依赖」的完整后端栈（Spring Boot + MySQL + Redis + Qdrant 向量库 + Ollama 模型），
无法跑在 Vercel / Netlify / Cloudflare Workers 等无服务器平台上。最省心的方式是：

> 一台能跑 Docker 的服务器 + 现有 `docker-compose.yml`，前面加一层 Caddy 自动 HTTPS。

整条链路：

```text
浏览器 ──https──▶ Caddy(:443, 自动证书) ──▶ frontend(Nginx:80, 容器网络)
                                              ├─ /        静态资源
                                              └─ /api/    反向代理 → backend:8080
```

---

## 二、前置条件

| 项目 | 要求 |
|---|---|
| 服务器 | 任意 Linux VPS，2C4G 起步（Ollama CPU 推理吃内存），建议 4C8G |
| 系统 | Ubuntu 22.04 / 24.04 或 Debian 12（脚本按此编写） |
| 域名 | **可选**。有域名走自动 HTTPS；没域名也能用 `:80` 走 HTTP + 公网 IP |
| 端口 | 放行 80 / 443（云厂商安全组里配置） |
| 成本 | 参考：腾讯云轻量 / 阿里云轻量、AWS Lightsail、Hetzner、DigitalOcean 均有低价档 |

---

## 三、部署步骤

### 第 1 步：把项目放到服务器

任选其一：

```bash
# 方式 A：git clone（推荐）
git clone <你的仓库地址> codeatlas && cd codeatlas

# 方式 B：从本机 scp 上传
# 本机执行：scp -r ./CodeAtlas user@<服务器IP>:/opt/codeatlas
```

### 第 2 步：配置 `.env`

```bash
cp .env.example .env
vi .env
```

**必须填**的三项（其余保持默认即可）：

```ini
# 生产对外地址（本方案的入口）
SITE_ADDRESS=codeatlas.example.com    # 有域名；无域名则填 :80

# 安全（生产必须改，勿用默认值）
MYSQL_ROOT_PASSWORD=<强密码>
JWT_SECRET=<随机 32 字节以上>

# 功能（可选，缺了对应功能不可用）
DEEPSEEK_API_KEY=sk-xxx
GITHUB_TOKEN=github_pat_xxx
```

> `JWT_SECRET` 生成示例：`openssl rand -base64 48`。留空也不要紧——`./deploy.sh` 会检测到并自动生成、写回 `.env`（不会覆盖已有值）。
>
> 注意：本项目**不提供内置默认密钥**，也不允许使用曾随源码公开的占位值——这类值等于公开的签名凭证，配置后 `deploy.sh` 会直接报错退出。

### 第 3 步：一键部署

```bash
chmod +x deploy.sh
./deploy.sh
```

脚本会自动：安装 Docker（若缺失）→ 校验 `.env` → `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build` → 等待 Embedding 模型自动就绪 → 健康检查 → 打印访问地址。

等价的手动命令：

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

# Embedding 模型由 ollama-init 服务自动拉取，无需手动执行；
# 查看进度：docker compose logs -f ollama-init
# 确认完成：docker compose ps -a   （ollama-init 应为 Exited (0)）
```

### 第 4 步：配置域名解析（仅「有域名」时）

在域名服务商处加一条 **A 记录**，把 `codeatlas.example.com` 指向服务器公网 IP。
Caddy 会在首次访问时自动向 Let's Encrypt 申请并续期证书。

> 需要确认 80 端口可达：Let's Encrypt 的 HTTP-01 校验需要 80 端口能被公网访问。

### 第 5 步：验证

```bash
# 有域名
curl https://codeatlas.example.com/health
# 无域名
curl http://<服务器IP>/health

# 期望输出
# {"status":"UP","db":"up","redis":"up"}
```

浏览器打开对应地址即可注册登录。

---

## 四、与「Cloudflare 代理」二选一

如果你的域名托管在 Cloudflare，也可以**不用 Caddy**：直接跑基础 `docker-compose.yml`，把前端 8081 对外，
在 Cloudflare 把 DNS 记录开「橙色云朵」（代理模式），Cloudflare 自动提供 HTTPS 边缘证书。
此时不需要 `docker-compose.prod.yml` 和 `Caddyfile`。

| | Caddy（本文方案） | Cloudflare 代理 |
|---|---|---|
| HTTPS 证书 | Let's Encrypt，自动签发 | Cloudflare 边缘证书 |
| 额外组件 | 一个 caddy 容器 | 无（域名需托管在 CF） |
| 适合 | 域名不在 CF / 想要自控入口 | 域名已在 CF |

---

## 五、生产加固（务必做）

完整清单见 [deployment.md 第九节](deployment.md#九生产环境注意事项)，重点三条：

1. **`JWT_SECRET` 必须是你自己的随机值**——项目不提供内置默认密钥，若沿用曾公开的占位值，后端会直接拒绝启动。
2. **`MYSQL_ROOT_PASSWORD` 必须改**（默认 `root` 一旦公网暴露等于裸奔）。
3. 后端 8080 / 前端 8081 已在 `docker-compose.prod.yml` 中改为只绑 `127.0.0.1`，公网只开放 80 / 443。

> 本项目前端采用**开放注册**（任何人拿到链接都能注册账号）。若只想自己用，
> 上线后建议在安全组/防火墙层面限制来源 IP，或自行在代码中关闭注册入口。

---

## 六、更新与运维

```bash
# 拉取新代码后重新构建
git pull
./deploy.sh

# 查看状态 / 日志
docker compose ps
docker compose logs -f backend

# 备份数据库
docker compose exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" codeatlas' > backup-$(date +%F).sql
```
