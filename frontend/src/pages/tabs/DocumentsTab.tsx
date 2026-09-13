import { useState, type FormEvent } from 'react'
import { IndexedBadge } from '../../components/Badges'
import ContentBlock from '../../components/ContentBlock'
import EmptyState from '../../components/EmptyState'
import Modal from '../../components/Modal'
import Spinner from '../../components/Spinner'
import { useAsync } from '../../hooks/useAsync'
import { useConfirm } from '../../hooks/useConfirm'
import { DOCUMENT_ACCEPT, documentApi } from '../../services/document'
import { ApiError } from '../../services/http'
import { useToast } from '../../stores/ToastContext'
import type { DocumentDetailVO, DocumentVO, ProjectVO } from '../../types/api'
import { formatBytes, formatDateTime } from '../../utils/format'

interface Props {
  project: ProjectVO
  onDataChanged?: () => void
}

export default function DocumentsTab({ project, onDataChanged }: Props) {
  const toast = useToast()
  const { confirm, confirmNode } = useConfirm()
  const { data, loading, error, reload, setData } = useAsync<DocumentVO[]>(
    () => documentApi.list(project.id),
    [project.id],
  )

  const [keyword, setKeyword] = useState('')
  const [searching, setSearching] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detail, setDetail] = useState<DocumentDetailVO | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const documents = data ?? []

  function refresh() {
    reload()
    onDataChanged?.()
  }

  async function handleUpload(file: File | undefined) {
    if (!file) {
      return
    }
    setUploading(true)
    setProgress(0)
    try {
      const created = await documentApi.upload(project.id, file, setProgress)
      toast.success(`文档「${created.name}」上传成功`)
      setKeyword('')
      refresh()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '上传失败，请检查文件类型与大小')
    } finally {
      setUploading(false)
      setProgress(0)
    }
  }

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = keyword.trim()
    setSearching(true)
    try {
      const result = trimmed
        ? await documentApi.search(project.id, trimmed)
        : await documentApi.list(project.id)
      setData(result)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '搜索失败')
    } finally {
      setSearching(false)
    }
  }

  async function openDetail(documentId: number) {
    setDetailOpen(true)
    setDetailLoading(true)
    setDetail(null)
    try {
      setDetail(await documentApi.detail(documentId))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '读取文档内容失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function handleDelete(doc: DocumentVO) {
    const confirmed = await confirm({
      title: '删除文档',
      message: `确认删除文档「${doc.name}」吗？该操作不可恢复。`,
      confirmText: '删除',
      danger: true,
    })
    if (!confirmed) {
      return
    }
    try {
      await documentApi.remove(doc.id)
      toast.success('文档已删除')
      refresh()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  return (
    <div className="stack">
      <div className="row row--between">
        <form className="inline-form" onSubmit={handleSearch} style={{ flex: 1, maxWidth: 520 }}>
          <div className="form-row">
            <span className="form-row__label">按名称搜索</span>
            <input
              className="input"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="输入关键字后回车"
            />
          </div>
          <button type="submit" className="btn" disabled={searching}>
            {searching ? '搜索中…' : '搜索'}
          </button>
          {keyword ? (
            <button
              type="button"
              className="btn"
              onClick={() => {
                setKeyword('')
                reload()
              }}
            >
              清空
            </button>
          ) : null}
        </form>

        <div className="row">
          <span className="muted">支持 md / txt / pdf，单个不超过 20MB</span>
          <label className="btn btn--primary" style={{ cursor: uploading ? 'wait' : 'pointer' }}>
            {uploading ? `上传中 ${progress}%` : '+ 上传文档'}
            <input
              type="file"
              accept={DOCUMENT_ACCEPT}
              hidden
              disabled={uploading}
              onChange={(event) => {
                const file = event.target.files?.[0]
                event.target.value = ''
                void handleUpload(file)
              }}
            />
          </label>
        </div>
      </div>

      {uploading ? (
        <div className="progress">
          <div className="progress__bar" style={{ width: `${progress}%` }} />
        </div>
      ) : null}

      {loading ? <Spinner label="正在加载文档…" /> : null}

      {error ? (
        <div className="alert alert--error">
          {error}
          <button type="button" className="btn btn--sm" style={{ marginLeft: 10 }} onClick={reload}>
            重试
          </button>
        </div>
      ) : null}

      {!loading && !error ? (
        <div className="card card--flush">
          {documents.length === 0 ? (
            <EmptyState
              title="还没有文档"
              description="上传 Markdown、TXT 或 PDF 文档，构建知识库后即可进行语义检索与 AI 问答。"
            />
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>文档名称</th>
                  <th>类型</th>
                  <th className="num">大小</th>
                  <th>版本</th>
                  <th>索引状态</th>
                  <th>上传时间</th>
                  <th style={{ width: 150 }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {documents.map((doc) => (
                  <tr key={doc.id}>
                    <td>
                      <span style={{ fontWeight: 600 }}>{doc.name}</span>
                      <div className="muted">ID {doc.id}</div>
                    </td>
                    <td className="mono">{doc.fileType}</td>
                    <td className="num">{formatBytes(doc.fileSize)}</td>
                    <td className="mono">v{doc.version}</td>
                    <td>
                      <IndexedBadge indexed={doc.indexed} />
                    </td>
                    <td className="muted">{formatDateTime(doc.createdAt)}</td>
                    <td>
                      <div className="row">
                        <button
                          type="button"
                          className="btn btn--sm"
                          onClick={() => void openDetail(doc.id)}
                        >
                          查看
                        </button>
                        <button
                          type="button"
                          className="btn btn--sm btn--danger"
                          onClick={() => void handleDelete(doc)}
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      ) : null}

      <Modal
        open={detailOpen}
        title={detail ? detail.name : '文档详情'}
        width={820}
        onClose={() => setDetailOpen(false)}
      >
        {detailLoading ? (
          <Spinner label="正在读取文档内容…" />
        ) : detail ? (
          <div className="stack">
            <div className="row">
              <span className="badge badge--muted">类型 {detail.fileType}</span>
              <span className="badge badge--muted">{formatBytes(detail.fileSize)}</span>
              <span className="badge badge--muted">版本 v{detail.version}</span>
              <span className="badge badge--muted">知识片段 {detail.chunkCount}</span>
              <IndexedBadge indexed={detail.indexed} />
            </div>
            <ContentBlock text={detail.content || '（文档内容为空）'} maxHeight={520} />
          </div>
        ) : (
          <div className="alert alert--error">未能加载文档内容</div>
        )}
      </Modal>

      {confirmNode}
    </div>
  )
}
