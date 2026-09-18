/**
 * 源码目录上传：过滤 + 路径规整。
 *
 * 只把用户编写的核心代码送进知识库，排除依赖、构建产物、缓存与配置，
 * 避免无关 chunk 挤占 RAG 的 top-k 名额。
 *
 * 扩展名与后端 FileParser 对齐，单文件上限与 CodeFileService.MAX_FILE_SIZE 对齐。
 */

/** 后端可解析、且属于源码的扩展名。 */
export const CODE_EXTENSIONS = [
  'java', 'cpp', 'cc', 'cxx', 'c', 'h', 'hpp',
  'py', 'js', 'jsx', 'ts', 'tsx',
] as const

const CODE_EXTENSION_SET: ReadonlySet<string> = new Set<string>(CODE_EXTENSIONS)

/** 与后端 CodeFileService.MAX_FILE_SIZE 一致。 */
export const CODE_MAX_FILE_SIZE = 5 * 1024 * 1024

/** 单批文件数上限，防止误选整个磁盘。 */
export const CODE_MAX_BATCH_FILES = 400

/** 与 code_files.file_path VARCHAR(500) 一致。 */
const CODE_MAX_PATH_LENGTH = 500

/** 依赖 / 构建产物 / 缓存 / 工具目录，一律跳过。 */
const IGNORED_DIRECTORIES: ReadonlySet<string> = new Set([
  'node_modules', 'bower_components', 'jspm_packages', 'vendor',
  'third_party', 'thirdparty', 'external', 'libs',
  'dist', 'build', 'out', 'output', 'target', 'bin', 'obj', 'classes',
  'coverage', 'reports', '.nyc_output',
  'venv', '.venv', 'env', 'virtualenv', 'site-packages',
  '__pycache__', '.pytest_cache', '.mypy_cache', '.ruff_cache', '.tox',
  '.gradle', '.mvn', '.next', '.nuxt', '.cache', '.parcel-cache', '.turbo', '.svelte-kit',
  '.git', '.svn', '.hg', '.idea', '.vscode', '.vs', '.settings',
  'generated', 'gen', 'autogen', 'migrations',
])

/** 测试目录，默认跳过（界面可勾选保留）。 */
const TEST_DIRECTORIES: ReadonlySet<string> = new Set([
  'test', 'tests', '__tests__', 'spec', 'specs', 'e2e',
  'mocks', '__mocks__', 'fixtures', '__snapshots__', 'snapshots',
])

/** 生成 / 压缩产物，按后缀跳过。 */
const IGNORED_FILE_SUFFIXES: readonly string[] = [
  '.min.js', '.min.css', '.bundle.js', '.chunk.js',
  '.d.ts', '.map', '.generated.ts', '.generated.js',
  '.g.dart', '.pb.go', '_pb2.py',
]

export type SkipReason =
  | 'ignored-directory'
  | 'test-directory'
  | 'generated-file'
  | 'extension'
  | 'empty'
  | 'too-large'
  | 'path-too-long'

const SKIP_REASON_LABELS: Record<SkipReason, string> = {
  'ignored-directory': '依赖 / 构建产物',
  'test-directory': '测试目录',
  'generated-file': '生成文件',
  extension: '非源码类型',
  empty: '空文件',
  'too-large': '超过 5MB',
  'path-too-long': '路径过长',
}

export interface UploadCandidate {
  file: File
  /** 规整后的入库路径，形如 src/main/java/UserService.java */
  path: string
  /** 纯文件名 */
  name: string
  size: number
}

export interface SkippedFile {
  /** 展示用的原始相对路径 */
  name: string
  reason: SkipReason
}

export interface CollectResult {
  accepted: UploadCandidate[]
  skipped: SkippedFile[]
  /** 超过单批上限而未纳入的文件数 */
  overflow: number
  /** 本次选中的文件总数（含被跳过的） */
  totalScanned: number
}

export interface CollectOptions {
  /** 是否保留测试目录下的文件，默认 false。 */
  includeTests?: boolean
}

export interface SkipGroup {
  reason: SkipReason
  label: string
  count: number
  /** 前若干条示例，避免弹窗堆出上百行 */
  samples: string[]
}

/** 取路径中的文件名。 */
function fileNameOf(path: string): string {
  const idx = path.lastIndexOf('/')
  return idx >= 0 ? path.slice(idx + 1) : path
}

/** 取小写扩展名（不含点）；无扩展名返回空串。 */
function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.')
  if (dot <= 0 || dot === name.length - 1) {
    return ''
  }
  return name.slice(dot + 1).toLowerCase()
}

/**
 * 目录上传时 File 自带 webkitRelativePath，形如 my-app/src/main/java/A.java。
 * TS 类型里没有该属性，这里安全兜底为文件名。
 */
function relativePathOf(file: File): string {
  const rel = (file as File & { webkitRelativePath?: string }).webkitRelativePath
  return rel && rel.length > 0 ? rel : file.name
}

/**
 * 规整为入库路径，规则与后端 CodeFileService.resolveFilePath 保持一致：
 * 1. 统一分隔符，去掉空段与 `.` / `..`；
 * 2. 路径中出现 src 段则从该段截断（兼容用户直接选项目根目录）；
 * 3. 否则丢弃首段（即系统对话框里选中的那一层文件夹名）。
 */
function normalizeSourcePath(relativePath: string): string {
  const segments = relativePath
    .replace(/\\/g, '/')
    .split('/')
    .filter((segment) => segment.length > 0 && segment !== '.' && segment !== '..')

  if (segments.length === 0) {
    return ''
  }
  const srcIndex = segments.indexOf('src')
  const anchored = srcIndex >= 0 ? segments.slice(srcIndex) : segments.slice(1)
  return (anchored.length > 0 ? anchored : segments).join('/')
}

/** 按过滤规则筛选出一批可上传文件。 */
export function collectSourceFiles(
  files: readonly File[],
  options: CollectOptions = {},
): CollectResult {
  const includeTests = options.includeTests ?? false
  const accepted: UploadCandidate[] = []
  const skipped: SkippedFile[] = []
  let overflow = 0

  for (const file of files) {
    const relative = relativePathOf(file)
    const segments = relative.replace(/\\/g, '/').split('/').filter(Boolean)
    const dirs = segments.slice(0, -1).map((segment) => segment.toLowerCase())

    const skip = (reason: SkipReason) => skipped.push({ name: relative, reason })

    if (dirs.some((dir) => IGNORED_DIRECTORIES.has(dir))) {
      skip('ignored-directory')
      continue
    }
    if (!includeTests && dirs.some((dir) => TEST_DIRECTORIES.has(dir))) {
      skip('test-directory')
      continue
    }
    const lowerName = file.name.toLowerCase()
    if (IGNORED_FILE_SUFFIXES.some((suffix) => lowerName.endsWith(suffix))) {
      skip('generated-file')
      continue
    }
    if (!CODE_EXTENSION_SET.has(extensionOf(file.name))) {
      skip('extension')
      continue
    }
    if (file.size === 0) {
      skip('empty')
      continue
    }
    if (file.size > CODE_MAX_FILE_SIZE) {
      skip('too-large')
      continue
    }
    const path = normalizeSourcePath(relative)
    if (path.length === 0 || path.length > CODE_MAX_PATH_LENGTH) {
      skip('path-too-long')
      continue
    }
    if (accepted.length >= CODE_MAX_BATCH_FILES) {
      overflow += 1
      continue
    }
    accepted.push({ file, path, name: fileNameOf(path), size: file.size })
  }

  return { accepted, skipped, overflow, totalScanned: files.length }
}

/** 按跳过原因聚合，便于弹窗里给出可读的汇总。 */
export function summarizeSkips(skipped: readonly SkippedFile[]): SkipGroup[] {
  const groups = new Map<SkipReason, SkipGroup>()
  for (const item of skipped) {
    const group = groups.get(item.reason)
    if (group) {
      group.count += 1
      if (group.samples.length < 5) {
        group.samples.push(item.name)
      }
    } else {
      groups.set(item.reason, {
        reason: item.reason,
        label: SKIP_REASON_LABELS[item.reason],
        count: 1,
        samples: [item.name],
      })
    }
  }
  return [...groups.values()].sort((a, b) => b.count - a.count)
}

/** 按顶层目录分组统计，用于预检清单展示。 */
export function groupByTopDirectory(
  candidates: readonly UploadCandidate[],
): { name: string; count: number }[] {
  const counts = new Map<string, number>()
  for (const item of candidates) {
    const idx = item.path.indexOf('/')
    const top = idx >= 0 ? item.path.slice(0, idx) : '(根目录)'
    counts.set(top, (counts.get(top) ?? 0) + 1)
  }
  return [...counts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
}
