import {
  CATEGORY_LABELS,
  ROLE_LABELS,
  SEVERITY_LABELS,
  type GitSyncStatus,
  type ProjectRole,
  type ReviewCategory,
  type ReviewSeverity,
} from '../types/api'

export function RoleBadge({ role }: { role: ProjectRole }) {
  return <span className={`badge badge--role-${role.toLowerCase()}`}>{ROLE_LABELS[role] ?? role}</span>
}

export function SeverityBadge({ severity }: { severity: ReviewSeverity }) {
  return (
    <span className={`badge badge--sev-${severity.toLowerCase()}`}>
      {SEVERITY_LABELS[severity] ?? severity}
    </span>
  )
}

export function CategoryBadge({ category }: { category: ReviewCategory }) {
  return <span className="badge badge--muted">{CATEGORY_LABELS[category] ?? category}</span>
}

const SYNC_LABELS: Record<GitSyncStatus, string> = {
  IDLE: '空闲',
  SYNCING: '同步中',
  FAILED: '同步失败',
}

export function SyncStatusBadge({ status }: { status: GitSyncStatus }) {
  return <span className={`badge badge--sync-${status.toLowerCase()}`}>{SYNC_LABELS[status] ?? status}</span>
}

export function IndexedBadge({ indexed }: { indexed: boolean }) {
  return (
    <span className={`badge ${indexed ? 'badge--ok' : 'badge--warn'}`}>
      {indexed ? '已索引' : '未索引'}
    </span>
  )
}
