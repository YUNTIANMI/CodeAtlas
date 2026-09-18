# ============================================================
# CodeAtlas 前端镜像
#
# 采用多阶段构建：
#   阶段 1（build）  用 Node 编译 React 静态资源
#   阶段 2（runtime）用 Nginx 托管静态资源并反向代理 /api
# 最终镜像不含 Node 与 node_modules，只保留 Nginx 与 dist 产物。
# ============================================================

# ---------------- 阶段 1：构建 ----------------
FROM node:20-alpine AS build

WORKDIR /build

# 先只复制依赖清单，命中缓存时跳过重复的 npm ci
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# 生产环境默认与后端同源：Nginx 已把 /api 反向代理到后端容器，
# 因此留空即可走相对路径，前端不需要知道后端地址，也不涉及跨域。
# 如需让构建产物直连其它后端地址，可传入：
#   docker compose build --build-arg VITE_API_BASE_URL=https://api.example.com frontend
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# 该脚本为 tsc --noEmit && vite build，类型检查不通过则构建失败
RUN npm run build

# ---------------- 阶段 2：运行 ----------------
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

# 必须用 127.0.0.1 而非 localhost：
# Alpine 的 musl 会把 localhost 优先解析为 IPv6 的 ::1，
# 而 nginx 的 listen 80 默认只绑定 IPv4，走 ::1 会 Connection refused。
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -q --spider http://127.0.0.1/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
