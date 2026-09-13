import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * 开发期通过 Vite 代理访问后端，避免任何跨域问题（同源请求）。
 * 后端默认监听 http://localhost:8080，接口前缀为 /api/v1，健康检查为 /health。
 * 如需直连后端（走 CORS 白名单），把 .env.local 里的 VITE_API_BASE_URL 设为
 * http://localhost:8080 即可（后端已放行 localhost:5173 与 127.0.0.1:5173）。
 */
const BACKEND_ORIGIN = 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: BACKEND_ORIGIN, changeOrigin: true },
      '/health': { target: BACKEND_ORIGIN, changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 900,
  },
})
