import { useState, type FormEvent } from 'react'
import { SyncStatusBadge } from '../../components/Badges'
import EmptyState from '../../components/EmptyState'
import Modal from '../../components/Modal'
import Spinner from '../../components/Spinner'
import { useAsync } from '../../hooks/useAsync'
import { gitApi } from '../../services/git'
import { ApiError } from '../../services/http'
import { useToast } from '../../stores/ToastContext'
import type { GitCommitVO, GitRepositoryVO, ProjectVO } from '../../types/api'
import { formatDateTime, formatRelative } from '../../utils/format'

export default function GitTab({ project }: { project: ProjectVO }) {
  const toast = useToast()
  const { data, loading, error, reload } = useAsync<GitCommitVO[]>(
    () => gitApi.commits(project.id),
    [project.id],
  )

  const [repoUrl, setRepoUrl] = useState('')
  const [accessTokenRef, setAccessTokenRef] = useState('')
  const [importing, setImporting] = useState(false)
  const [repo, setRepo] = useState<GitRepositoryVO | null>(null)
  const [syncing, setSyncing] = useState(false)
  const [limit, setLimit] = useState('30')

  const [detailOpen, setDetailOpen] = useState(false)
  const [detail, setDetail] = useState<GitCommitVO | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [summarizing, setSummarizing] = useState(false)

  const commits = data ?? []

  async function handleImport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const url = repoUrl.trim()
    if (!url) {
      toast.error('请输入仓库地址')
      return
    }
    setImporting(true)
    try {
      const created = await gitApi.importRepo(project.id, {
        repoUrl: url,
        accessTokenRef: accessTokenRef.trim() || undefined,
      })
      setRepo(created)
      toast.success(`已导入仓库 ${created.fullName || created.repoUrl}`)
      setRepoUrl('')
      setAccessTokenRef('')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '导入仓库失败')
    } finally {
      setImporting(false)
    }
  }

  async function handleSync() {
    const parsed = Number(limit)
    setSyncing(true)
    try {
      const result = await gitApi.sync(project.id, Number.isFinite(parsed) ? parsed : undefined)
      toast.success(`同步完成：新增 ${result.added} / 跳过 ${result.skipped} / 共 ${result.total}`)
      reload()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '同步提交失败')
    } finally {
      setSyncing(false)
    }
  }

  async function openDetail(commit: GitCommitVO) {
    setDetailOpen(true)
    setDetailLoading(true)
    setDetail(commit)
    try {
      setDetail(await gitApi.commitDetail(commit.id))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '读取提交详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function handleSummary() {
    if (!detail) {
      return
    }
    setSummarizing(true)
    try {
      const updated = await gitApi.commitSummary(detail.id)
      setDetail(updated)
      toast.success('AI 摘要已生成')
      reload()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '生成摘要失败')
    } finally {
      setSummarizing(false)
    }
  }

  return (
    <div className="stack">
      <section className="card">
        <h3 className="card__title">导入 Git 仓库（只读）</h3>
        <div className="divider" />
        <form className="inline-form" onSubmit={handleImport}>
          <div className="form-row" style={{ flex: 2, minWidth: 260 }}>
            <span className="form-row__label">仓库地址</span>
            <input
              className="input"
              value={repoUrl}
              onChange={(event) => setRepoUrl(event.target.value)}
              placeholder="https://github.com/owner/repo.git"
            />
          </div>
          <div className="form-row" style={{ minWidth: 180 }}>
            <span className="form-row__label">访问令牌引用（可选）</span>
            <input
              className="input"
              value={accessTokenRef}
              onChange={(event) => setAccessTokenRef(event.target.value)}
              placeholder="私有仓库凭证标识"
            />
          </div>
          <button type="submit" className="btn btn--primary" disabled={importing}>
            {importing ? '导入中…' : '导入'}
          </button>
        </form>

        {repo ? (
          <>
            <div className="divider" />
            <div className="row">
              <span className="badge badge--muted">{repo.provider}</span>
              <span className="mono">{repo.fullName || repo.repoUrl}</span>
              <SyncStatusBadge status={repo.syncStatus} />
              <span className="muted">默认分支 {repo.defaultBranch}</span>
              <span className="muted">最近同步 {formatRelative(repo.lastSyncedAt)}</span>
            </div>
          </>
        ) : null}
      </section>

      <section className="card">
        <div className="row row--between">
          <span className="card__title">提交记录（{commits.length}）</span>
          <div className="row">
            <div className="form-row" style={{ width: 130 }}>
              <select className="select" value={limit} onChange={(event) => setLimit(event.target.value)}>
                {['10', '30', '50', '100', '200', '500'].map((item) => (
                  <option key={item} value={item}>
                    同步 {item} 条
                  </option>
                ))}
              </select>
            </div>
            <button type="button" className="btn btn--primary" onClick={() => void handleSync()} disabled={syncing}>
              {syncing ? '同步中…' : '同步提交'}
            </button>
          </div>
        </div>
        <div className="divider" />

        {loading ? <Spinner label="正在加载提交记录…" /> : null}

        {error ? (
          <div className="alert alert--error">
            {error}
            <button type="button" className="btn btn--sm" style={{ marginLeft: 10 }} onClick={reload}>
              重试
            </button>
          </div>
        ) : null}

        {!loading && !error ? (
          commits.length === 0 ? (
            <EmptyState
              title="暂无提交记录"
              description="导入仓库后点击「同步提交」，即可拉取提交历史并生成 AI 摘要。"
            />
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>提交</th>
                  <th>作者</th>
                  <th className="num">变更</th>
                  <th>状态</th>
                  <th>提交时间</th>
                  <th style={{ width: 90 }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {commits.map((commit) => (
                  <tr key={commit.id}>
                    <td>
                      <div className="truncate" style={{ maxWidth: 340, fontWeight: 600 }}>
                        {commit.message}
                      </div>
                      <div className="mono muted">{commit.commitHash.slice(0, 10)}</div>
                    </td>
                    <td>{commit.authorName}</td>
                    <td className="num">
                      <span style={{ color: '#15803d' }}>+{commit.additions}</span>{' '}
                      <span style={{ color: '#b91c1c' }}>-{commit.deletions}</span>
                    </td>
                    <td>
                      <span className={`badge ${commit.analyzed ? 'badge--ok' : 'badge--warn'}`}>
                        {commit.analyzed ? '已分析' : '未分析'}
                      </span>
                    </td>
                    <td className="muted">{formatDateTime(commit.committedAt)}</td>
                    <td>
                      <button type="button" className="btn btn--sm" onClick={() => void openDetail(commit)}>
                        详情
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )
        ) : null}
      </section>

      <Modal open={detailOpen} title="提交详情" width={720} onClose={() => setDetailOpen(false)}>
        {detailLoading ? (
          <Spinner label="正在读取提交详情…" />
        ) : detail ? (
          <div className="stack">
            <div className="mono muted">{detail.commitHash}</div>
            <table className="table">
              <tbody>
                <tr>
                  <td className="muted" style={{ width: 120 }}>
                    提交信息
                  </td>
                  <td>{detail.message}</td>
                </tr>
                <tr>
                  <td className="muted">作者</td>
                  <td>{detail.authorName}</td>
                </tr>
                <tr>
                  <td className="muted">提交时间</td>
                  <td>{formatDateTime(detail.committedAt)}</td>
                </tr>
                <tr>
                  <td className="muted">变更行数</td>
                  <td>
                    <span style={{ color: '#15803d' }}>+{detail.additions}</span>{' '}
                    <span style={{ color: '#b91c1c' }}>-{detail.deletions}</span>
                  </td>
                </tr>
              </tbody>
            </table>

            <div className="row row--between">
              <span className="card__title">AI 摘要</span>
              <button type="button" className="btn btn--sm" onClick={() => void handleSummary()} disabled={summarizing}>
                {summarizing ? '生成中…' : detail.summary ? '重新生成' : '生成摘要'}
              </button>
            </div>
            {detail.summary ? (
              <pre className="content-block content-block--light">{detail.summary}</pre>
            ) : (
              <p className="muted">该提交尚未生成 AI 摘要。</p>
            )}
          </div>
        ) : null}
      </Modal>
    </div>
  )
}
