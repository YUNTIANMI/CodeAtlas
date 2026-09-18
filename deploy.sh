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

SECRET_HINT="openssl rand -base64 48"

# 从 .env 读取单个变量（不 source，避免密码含特殊字符时的副作用）
get_env() { grep -E "^$1=" .env 2>/dev/null | tail -n1 | cut -d= -f2-; }

# 覆盖写入 .env 中的单个变量（不存在则追加）。用于自动生成密钥。
set_env() {
  if grep -qE "^$1=" .env; then
    # 以 | 作 sed 分隔符，避免密钥中的 / 破坏表达式
    sed -i.bak "s|^$1=.*|$1=$2|" .env && rm -f .env.bak
  else
    printf '%s=%s\n' "$1" "$2" >> .env
  fi
}

# 生成 48 字节随机密钥（Base64 编码）。
generate_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48 | tr -d '\n'
  else
    head -c 48 /dev/urandom | base64 | tr -d '\n'
  fi
}

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
  warn "  SITE_ADDRESS、MYSQL_ROOT_PASSWORD（JWT_SECRET 留空会在下一步自动生成）"
  exit 0
fi

# ---------- 3. 校验必填变量 ----------
SITE_ADDRESS="$(get_env SITE_ADDRESS)"
JWT_SECRET="$(get_env JWT_SECRET)"
MYSQL_ROOT_PASSWORD="$(get_env MYSQL_ROOT_PASSWORD)"
[ -n "$SITE_ADDRESS" ] || die "请在 .env 中设置 SITE_ADDRESS（如 codeatlas.example.com；无域名可用 :80）"

# JWT_SECRET 是唯一身份凭证：曾作为默认值公开的占位密钥必须拒绝，
# 否则任何人拿它签一个 admin 的 Token 就能拿到全部权限。
case "$JWT_SECRET" in
  codeatlas-default-secret*)
    die "JWT_SECRET 仍是曾随源码公开的占位密钥，可被用于伪造任意用户 Token。请重新生成：$SECRET_HINT" ;;
esac

# 留空则自动生成并写回 .env：既保证「一键部署」不因缺密钥中断，
# 又不引入任何公开默认值。仅写一次，之后重跑不会覆盖已有密钥。
if [ -z "$JWT_SECRET" ]; then
  JWT_SECRET="$(generate_secret)"
  set_env JWT_SECRET "$JWT_SECRET"
  info "JWT_SECRET 为空，已自动生成 48 字节随机密钥并写入 .env"
fi

if [ "$(printf '%s' "$JWT_SECRET" | wc -c | tr -d ' ')" -lt 32 ]; then
  die "JWT_SECRET 不足 32 字节（HS256 要求），请重新生成：$SECRET_HINT"
fi

if [ -z "$MYSQL_ROOT_PASSWORD" ] || [ "$MYSQL_ROOT_PASSWORD" = "root" ]; then
  warn "MYSQL_ROOT_PASSWORD 仍是默认值 root，建议更换"
fi

# ---------- 4. 构建并启动 ----------
info "构建并启动全部服务（含 Caddy 入口）……"
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

# ---------- 5. 等待 Embedding 模型就绪 ----------
# 模型由 ollama-init 服务在启动时自动拉取（幂等：模型已存在则秒级完成）。
info "等待 Embedding 模型就绪（首次约 1.2GB，可执行 docker compose logs -f ollama-init 查看进度）……"
for _ in $(seq 1 150); do
  if [ "$(docker inspect -f '{{.State.Status}}' codeatlas-ollama-init 2>/dev/null)" = "exited" ]; then break; fi
  sleep 4
done
if [ "$(docker inspect -f '{{.State.ExitCode}}' codeatlas-ollama-init 2>/dev/null)" = "0" ]; then
  info "Embedding 模型已就绪。"
else
  warn "模型拉取尚未完成或失败，请检查：docker compose logs ollama-init"
fi

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
