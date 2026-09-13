/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端接口基础地址；为空时使用同源相对路径（由 dev proxy 转发）。 */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
