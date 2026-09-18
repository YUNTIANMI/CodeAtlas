#!/usr/bin/env bash
# ============================================================
# CodeAtlas 生产部署脚本（方案一：VPS + Docker + Caddy 自动 HTTPS）
#
# 用法（在 Linux 服务器上、项目根目录执行）：
#   chmod +x deploy.sh
#   ./deploy.sh
#
# 前置：在 .env 中填好 JWT_SECRET / MYSQL_ROOT_PASSWORD / SITE_ADDRESS
# 详见 docs/deployment-vps.md
# ============================================================
set -euo pipefail

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info() { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
die()  { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

# 从 .env 读取单个变量（不 source，避免密码含特殊字符时的副作用）
get_env() { grep -E "^$1=" .env 2>/dev/null | tail -n1 | cut -d= -f2-; }

# ---------- 1. 检查 Docker ----------
if ! command -v docker >/dev/null 2>&1; then
  info "未检测到 Docker，正在安装……"
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker || true
fi
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 不可用，请安装 docker-compose-plugin"

# ---------- 2. 检查 .env ----------
if [ ! -f .env ]; then
  cp .env.example .env
  warn "已从 .env.example 生成 .env，请先编辑以下必填项后重新运行："
  warn "  JWT_SECRET、MYSQL_ROOT_PASSWORD、SITE_ADDRESS（可选 DEEPSEEK_API_KEY、GITHUB_TOKEN）"
  exit 0
fi

# ---------- 3. 校验必填变量 ----------
SITE_ADDRESS="$(get_env SITE_ADDRESS)"
JWT_SECRET="$(get_env JWT_SECRET)"
MYSQL_ROOT_PASSWORD="$(get_env MYSQL_ROOT_PASSWORD)"
[ -n "$SITE_ADDRESS" ] || die "请在 .env 中设置 SITE_ADDRESS（如 codeatlas.example.com；无域名可用 :80）"
[ -n "$JWT_SECRET" ] || die "请在 .env 中设置 JWT_SECRET（随机 32 字节以上）"
if [ -z "$MYSQL_ROOT_PASSWORD" ] || [ "$MYSQL_ROOT_PASSWORD" = "root" ]; then
  warn "MYSQL_ROOT_PASSWORD 仍是默认值 root，建议更换"
fi

# ---------- 4. 构建并启动 ----------
info "构建并启动全部服务（含 Caddy 入口）……"
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

# ---------- 5. 拉取 Embedding 模型（幂等） ----------
info "确保 Embedding 模型已就绪（首次约 1.2GB）……"
docker compose exec ollama ollama pull bge-m3 || warn "模型拉取失败，稍后可手动执行：docker compose exec ollama ollama pull bge-m3"

# ---------- 6. 健康检查 ----------
info "等待后端就绪……"
for _ in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:8080/health" >/dev/null 2>&1; then break; fi
  sleep 2
done
curl -fsS "http://127.0.0.1:8080/health" || warn "后端健康检查未通过，请执行 docker compose logs backend 排查"

# ---------- 7. 输出访问地址 ----------
echo ""
info "部署完成！"
if [ "$SITE_ADDRESS" = ":80" ]; then
  info "访问地址：http://<服务器公网IP>"
else
  info "访问地址：https://${SITE_ADDRESS}"
fi
info "查看状态：docker compose ps"
info "查看日志：docker compose logs -f backend"
