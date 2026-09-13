/**
 * 与后端 com.codeatlas 各 DTO 逐字段对齐的类型定义。
 *
 * 后端统一响应体为 {code, message, data, timestamp}，见 com.codeatlas.common.Result。
 * 字段名与 JSON 输出严格一致，禁止随意改名。
 */

/** 统一响应体。 */
export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

/* ---------------- 认证与用户 ---------------- */

export interface UserVO {
  id: number
  username: string
  email: string
  displayName: string | null
  avatarUrl: string | null
  roles: string[]
  createdAt: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  displayName?: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: UserVO
}

/* ---------------- 项目与成员 ---------------- */

export type ProjectRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER'

export interface ProjectVO {
  id: number
  name: string
  description: string | null
  projectType: string | null
  techStack: string[]
  ownerId: number
  status: number
  myRole: ProjectRole
  createdAt: string
  updatedAt: string
}

/** 创建/修改项目请求（修改时字段为 null 表示不修改）。 */
export interface ProjectRequest {
  name?: string
  description?: string
  projectType?: string
  techStack?: string[]
}

export interface ProjectMemberVO {
  id: number
  userId: number
  username: string
  role: ProjectRole
  joinedAt: string
}

export interface InviteMemberRequest {
  userId: number
  role: ProjectRole
}

export interface UpdateMemberRoleRequest {
  role: ProjectRole
}

/* ---------------- 文档 ---------------- */

export interface DocumentVO {
  id: number
  projectId: number
  name: string
  fileType: string
  fileSize: number
  version: number
  indexed: boolean
  uploadedBy: number
  createdAt: string
}

export interface DocumentDetailVO extends DocumentVO {
  content: string
  chunkCount: number
}

/* ---------------- 代码 ---------------- */

export interface CodeFileVO {
  id: number
  projectId: number
  filePath: string
  fileName: string
  language: string
  fileSize: number
  indexed: boolean
  createdAt: string
}

export interface CodeFileDetailVO extends CodeFileVO {
  content: string
  chunkCount: number
}

export interface StructureNode {
  name: string
  path: string
  directory: boolean
  children: StructureNode[] | null
}

/* ---------------- 知识库 / RAG ---------------- */

/** 知识片段来源类型（FileChunk.SourceType）。 */
export type ChunkSourceType = 'DOCUMENT' | 'CODE'

export interface BuildResultVO {
  total: number
  indexed: number
  failed: number
}

export interface IndexStatusVO {
  totalChunks: number
  indexedChunks: number
  pendingChunks: number
  collection: string
  embeddingModel: string
}

/** 检索 / 问答请求（KnowledgeController）。 */
export interface QueryRequest {
  query: string
  topK?: number
}

export interface SearchResultVO {
  chunkId: number
  content: string
  score: number
  sourceType: ChunkSourceType
  sourceId: number
  chunkIndex: number
}

export interface CitationVO {
  sourceType: ChunkSourceType
  sourceId: number
  chunkIndex: number
  score: number
  snippet: string
}

export interface AskAnswerVO {
  answer: string
  citations: CitationVO[]
  model: string
}

/* ---------------- AI 问答（对话） ---------------- */

export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'

/** MessageVO.CitationItem —— 比 CitationVO 多一个 fileName。 */
export interface CitationItem {
  sourceType: ChunkSourceType
  sourceId: number
  fileName: string
  chunkIndex: number
  score: number
  snippet: string
}

export interface MessageVO {
  id: number
  conversationId: number
  role: MessageRole
  content: string
  model: string | null
  createdAt: string
  citations: CitationItem[]
}

export interface ConversationVO {
  id: number
  projectId: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface ChatRequest {
  /** 不传则新建会话。 */
  conversationId?: number
  question: string
  topK?: number
}

export interface ChatResponse {
  conversationId: number
  message: MessageVO
}

/* ---------------- AI Code Review ---------------- */

export type ReviewSeverity = 'INFO' | 'MINOR' | 'MAJOR' | 'CRITICAL'
export type ReviewCategory = 'CORRECTNESS' | 'PERFORMANCE' | 'SECURITY' | 'MAINTAINABILITY'
export type ReviewSourceType = 'FILE' | 'SNIPPET' | 'COMMIT' | 'DIFF'

export interface ReviewRequest {
  sourceType: ReviewSourceType
  sourceRef?: string
  content: string
}

export interface ReviewResultVO {
  id: number
  projectId: number
  sourceType: ReviewSourceType
  sourceRef: string | null
  severity: ReviewSeverity
  category: ReviewCategory
  filePath: string | null
  line: number | null
  description: string
  risk: string | null
  suggestion: string | null
  model: string | null
  createdAt: string
}

/* ---------------- Git 分析 ---------------- */

export type GitSyncStatus = 'IDLE' | 'SYNCING' | 'FAILED'

export interface ImportRepoRequest {
  repoUrl: string
  accessTokenRef?: string
}

export interface GitRepositoryVO {
  id: number
  projectId: number
  repoUrl: string
  provider: string
  fullName: string
  defaultBranch: string
  lastSyncedAt: string | null
  syncStatus: GitSyncStatus
}

export interface SyncResultVO {
  total: number
  added: number
  skipped: number
}

export interface GitCommitVO {
  id: number
  repoId: number
  commitHash: string
  message: string
  authorName: string
  committedAt: string
  additions: number
  deletions: number
  summary: string | null
  analyzed: boolean
}

/* ---------------- AI Agent ---------------- */

export interface AgentRequest {
  task: string
}

export interface ToolCallVO {
  toolName: string
  input: string
  output: string
  executionTimeMs: number
  success: boolean
  errorMessage: string | null
}

export interface AgentResponse {
  task: string
  answer: string
  toolCalls: ToolCallVO[]
  model: string
}

/* ---------------- 枚举取值常量（供下拉框使用） ---------------- */

export const PROJECT_ROLES: ProjectRole[] = ['OWNER', 'ADMIN', 'MEMBER', 'VIEWER']

export const REVIEW_SEVERITIES: ReviewSeverity[] = ['INFO', 'MINOR', 'MAJOR', 'CRITICAL']

export const REVIEW_SOURCE_TYPES: ReviewSourceType[] = ['FILE', 'SNIPPET', 'COMMIT', 'DIFF']

/** 中文标签映射。 */
export const ROLE_LABELS: Record<ProjectRole, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  MEMBER: '成员',
  VIEWER: '观察者',
}

export const SEVERITY_LABELS: Record<ReviewSeverity, string> = {
  INFO: '提示',
  MINOR: '轻微',
  MAJOR: '严重',
  CRITICAL: '致命',
}

export const CATEGORY_LABELS: Record<ReviewCategory, string> = {
  CORRECTNESS: '正确性',
  PERFORMANCE: '性能',
  SECURITY: '安全',
  MAINTAINABILITY: '可维护性',
}

export const REVIEW_SOURCE_LABELS: Record<ReviewSourceType, string> = {
  FILE: '代码文件',
  SNIPPET: '代码片段',
  COMMIT: '提交记录',
  DIFF: '差异内容',
}

export const SOURCE_TYPE_LABELS: Record<ChunkSourceType, string> = {
  DOCUMENT: '文档',
  CODE: '代码',
}
