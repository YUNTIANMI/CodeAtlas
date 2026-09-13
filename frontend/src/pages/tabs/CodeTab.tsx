import { useEffect, useMemo, useRef, useState, type ChangeEvent, type ReactNode } from 'react'
import { IndexedBadge } from '../../components/Badges'
import ContentBlock from '../../components/ContentBlock'
import EmptyState from '../../components/EmptyState'
import Modal from '../../components/Modal'
import Spinner from '../../components/Spinner'
import { useAsync } from '../../hooks/useAsync'
import { useConfirm } from '../../hooks/useConfirm'
import { CODE_ACCEPT, CODE_LANGUAGES, codeApi } from '../../services/code'
import { ApiError } from '../../services/http'
import { useToast } from '../../stores/ToastContext'
import type { CodeFileDetailVO, CodeFileVO, ProjectVO, StructureNode } from '../../types/api'
import {
  CODE_MAX_BATCH_FILES,
  CODE_MAX_FILE_SIZE,
  collectSourceFiles,
  groupByTopDirectory,
  summarizeSkips,
  type CollectResult,
  type SkipGroup,
} from '../../utils/codeUpload'
import { formatBytes, formatDateTime } from '../../utils/format'

interface Props {
  project: ProjectVO
  onDataChanged?: () => void
}

interface BatchReport {
  total: number
  success: number
  failed: { path: string; message: string }[]
  skipGroups: SkipGroup[]
  overflow: number
  aborted: boolean
}

function renderTree(node: StructureNode, depth: number): ReactNode {
  return (
    <div key={`${node.path}-${depth}`}>
      <div className={`tree__node${node.directory ? ' tree__node--dir' : ''}`}>
        {'  '.repeat(depth)}
        {node.directory ? '▸ ' : '· '}
        {node.name}
      </div>
      {node.children?.map((child) => renderTree(child, depth + 1))}
    </div>
  )
}

export default function CodeTab({ project, onDataChanged }: Props) {
  const toast = useToast()
  const { confirm, confirmNode } = useConfirm()
  const [language, setLanguage] = useState('')

  const { data, loading, error, reload } = useAsync<CodeFileVO[]>(
    () => codeApi.list(project.id, language || undefined),
    [project.id, language],
  )

  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const [virtualPath, setVirtualPath] = useState('')
  const [detailOpen, setDetailOpen] = useState(false)
  const [detail, setDetail] = useState<CodeFileDetailVO | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [structureOpen, setStructureOpen] = useState(false)
  const [structure, setStructure] = useState<StructureNode | null>(null)
  const [structureLoading, setStructureLoading] = useState(false)

  const folderInputRef = useRef<HTMLInputElement>(null)
  const folderFilesRef = useRef<File[]>([])
  const abortRef = useRef(false)
  const [includeTests, setIncludeTests] = useState(false)
  const [plan, setPlan] = useState<CollectResult | null>(null)
  const [planOpen, setPlanOpen] = useState(false)
  const [batchRunning, setBatchRunning] = useState(false)
  const [batchDone, setBatchDone] = useState(0)
  const [batchFailed, setBatchFailed] = useState<{ path: string; message: string }[]>([])
  const [batchReport, setBatchReport] = useState<BatchReport | null>(null)

  // webkitdirectory 不在 React 的 InputHTMLAttributes 类型定义里，用 ref 挂上属性
  useEffect(() => {
    const input = folderInputRef.current
    if (input) {
      input.setAttribute('webkitdirectory', '')
      input.setAttribute('directory', '')
    }
  }, [])

  const planGroups = useMemo(() => (plan ? groupByTopDirectory(plan.accepted) : []), [plan])
  const skipGroups = useMemo(() => (plan ? summarizeSkips(plan.skipped) : []), [plan])

  const files = data ?? []

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
      const created = await codeApi.upload(project.id, file, {
        path: virtualPath.trim() || undefined,
        onProgress: setProgress,
      })
      toast.success(`代码文件「${created.fileName}」上传成功`)
      setVirtualPath('')
      refresh()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '上传失败，请检查文件类型与大小')
    } finally {
      setUploading(false)
      setProgress(0)
    }
  }

  function handleFolderPick(event: ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (picked.length === 0) {
      return
    }
    folderFilesRef.current = picked
    setPlan(collectSourceFiles(picked, { includeTests }))
    setPlanOpen(true)
  }

  function handleIncludeTestsChange(next: boolean) {
    setIncludeTests(next)
    if (folderFilesRef.current.length > 0) {
      setPlan(collectSourceFiles(folderFilesRef.current, { includeTests: next }))
    }
  }

  /**
   * 逐个串行上传：单个文件失败不影响其余文件，结束后统一汇总。
   * 不用并发是为了避免打爆连接池，也避免后续知识库构建排队失控。
   */
  async function startBatch() {
    const current = plan
    if (!current || current.accepted.length === 0 || batchRunning) {
      return
    }
    abortRef.current = false
    setBatchRunning(true)
    setBatchDone(0)
    setBatchFailed([])
    setPlanOpen(false)

    const failed: { path: string; message: string }[] = []
    let success = 0
    let aborted = false

    for (const item of current.accepted) {
      if (abortRef.current) {
        aborted = true
        break
      }
      try {
        await codeApi.upload(project.id, item.file, { path: item.path, fileName: item.name })
        success += 1
      } catch (err) {
        failed.push({
          path: item.path,
          message: err instanceof ApiError ? err.message : '上传失败',
        })
      }
      setBatchFailed([...failed])
      setBatchDone(success + failed.length)
    }

    setBatchRunning(false)
    setBatchReport({
      total: current.accepted.length,
      success,
      failed,
      skipGroups: summarizeSkips(current.skipped),
      overflow: current.overflow,
      aborted,
    })
    if (success > 0) {
      refresh()
    }
  }

  async function openDetail(fileId: number) {
    setDetailOpen(true)
    setDetailLoading(true)
    setDetail(null)
    try {
      setDetail(await codeApi.detail(fileId))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '读取代码内容失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function openStructure() {
    setStructureOpen(true)
    setStructureLoading(true)
    try {
      setStructure(await codeApi.structure(project.id))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '读取目录结构失败')
    } finally {
      setStructureLoading(false)
    }
  }

  async function handleDelete(file: CodeFileVO) {
    const confirmed = await confirm({
      title: '删除代码文件',
      message: `确认删除代码文件「${file.fileName}」吗？`,
      confirmText: '删除',
      danger: true,
    })
    if (!confirmed) {
      return
    }
    try {
      await codeApi.remove(file.id)
      toast.success('代码文件已删除')
      refresh()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  return (
    <div className="stack">
      <div className="row row--between">
        <div className="row">
          <div className="form-row">
            <span className="form-row__label">按语言筛选</span>
            <select
              className="select"
              value={language}
              onChange={(event) => setLanguage(event.target.value)}
            >
              <option value="">全部语言</option>
              {CODE_LANGUAGES.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          </div>
          <button type="button" className="btn" onClick={() => void openStructure()}>
            查看目录结构
          </button>
        </div>

        <div className="row">
          <input
            className="input"
            style={{ width: 260 }}
            value={virtualPath}
            onChange={(event) => setVirtualPath(event.target.value)}
            placeholder="虚拟路径（可选）src/main/java/UserService.java"
          />
          <span className="muted">
            单文件 ≤ {formatBytes(CODE_MAX_FILE_SIZE)}，单批 ≤ {CODE_MAX_BATCH_FILES} 个
          </span>
          <button
            type="button"
            className="btn"
            disabled={uploading || batchRunning}
            onClick={() => folderInputRef.current?.click()}
          >
            上传源码目录
          </button>
          <label className="btn btn--primary" style={{ cursor: uploading ? 'wait' : 'pointer' }}>
            {uploading ? `上传中 ${progress}%` : '+ 上传单文件'}
            <input
              type="file"
              accept={CODE_ACCEPT}
              hidden
              disabled={uploading}
              onChange={(event) => {
                const file = event.target.files?.[0]
                event.target.value = ''
                void handleUpload(file)
              }}
            />
          </label>
          <input
            ref={folderInputRef}
            type="file"
            multiple
            hidden
            disabled={uploading || batchRunning}
            onChange={handleFolderPick}
          />
        </div>
      </div>

      {uploading ? (
        <div className="progress">
          <div className="progress__bar" style={{ width: `${progress}%` }} />
        </div>
      ) : null}

      {batchRunning ? (
        <div className="card">
          <div className="row row--between">
            <span className="card__title">
              正在上传源码目录 {batchDone} / {plan?.accepted.length ?? 0}
            </span>
            <button
              type="button"
              className="btn btn--sm"
              onClick={() => {
                abortRef.current = true
              }}
            >
              停止
            </button>
          </div>
          <div className="progress" style={{ marginTop: 10 }}>
            <div
              className="progress__bar"
              style={{
                width: `${
                  plan && plan.accepted.length > 0
                    ? Math.round((batchDone / plan.accepted.length) * 100)
                    : 0
                }%`,
              }}
            />
          </div>
          {batchFailed.length > 0 ? (
            <div className="muted" style={{ marginTop: 8 }}>
              已失败 {batchFailed.length} 个，其余继续上传中…
            </div>
          ) : null}
        </div>
      ) : null}

      {loading ? <Spinner label="正在加载代码文件…" /> : null}

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
          {files.length === 0 ? (
            <EmptyState
              title="还没有代码文件"
              description="上传源码目录（自动跳过依赖与构建产物，只收核心代码）或单个文件，构建知识库后即可进行代码语义检索与审查。"
            />
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>文件</th>
                  <th>语言</th>
                  <th>路径</th>
                  <th className="num">大小</th>
                  <th>索引状态</th>
                  <th>上传时间</th>
                  <th style={{ width: 150 }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {files.map((file) => (
                  <tr key={file.id}>
                    <td>
                      <span style={{ fontWeight: 600 }}>{file.fileName}</span>
                      <div className="muted">ID {file.id}</div>
                    </td>
                    <td className="mono">{file.language}</td>
                    <td className="mono truncate" style={{ maxWidth: 260 }}>
                      {file.filePath}
                    </td>
                    <td className="num">{formatBytes(file.fileSize)}</td>
                    <td>
                      <IndexedBadge indexed={file.indexed} />
                    </td>
                    <td className="muted">{formatDateTime(file.createdAt)}</td>
                    <td>
                      <div className="row">
                        <button
                          type="button"
                          className="btn btn--sm"
                          onClick={() => void openDetail(file.id)}
                        >
                          查看
                        </button>
                        <button
                          type="button"
                          className="btn btn--sm btn--danger"
                          onClick={() => void handleDelete(file)}
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
        title={detail ? detail.fileName : '代码详情'}
        width={860}
        onClose={() => setDetailOpen(false)}
      >
        {detailLoading ? (
          <Spinner label="正在读取代码内容…" />
        ) : detail ? (
          <div className="stack">
            <div className="row">
              <span className="badge badge--muted">{detail.language}</span>
              <span className="badge badge--muted">{formatBytes(detail.fileSize)}</span>
              <span className="badge badge--muted">知识片段 {detail.chunkCount}</span>
              <IndexedBadge indexed={detail.indexed} />
            </div>
            <div className="mono muted">{detail.filePath}</div>
            <ContentBlock text={detail.content || '（文件内容为空）'} maxHeight={520} />
          </div>
        ) : (
          <div className="alert alert--error">未能加载代码内容</div>
        )}
      </Modal>

      <Modal
        open={structureOpen}
        title="项目代码目录结构"
        width={720}
        onClose={() => setStructureOpen(false)}
      >
        {structureLoading ? (
          <Spinner label="正在生成目录结构…" />
        ) : structure ? (
          <div className="tree">{renderTree(structure, 0)}</div>
        ) : (
          <EmptyState title="暂无目录结构" description="上传代码文件后即可查看目录结构。" />
        )}
      </Modal>

      <Modal
        open={planOpen}
        title="确认上传源码目录"
        width={720}
        onClose={() => setPlanOpen(false)}
        footer={
          <>
            <button type="button" className="btn" onClick={() => setPlanOpen(false)}>
              取消
            </button>
            <button
              type="button"
              className="btn btn--primary"
              disabled={!plan || plan.accepted.length === 0}
              onClick={() => void startBatch()}
            >
              上传 {plan?.accepted.length ?? 0} 个文件
            </button>
          </>
        }
      >
        {plan ? (
          <div className="stack">
            <div className="grid grid--stats">
              <div className="stat">
                <div className="stat__label">将上传</div>
                <div className="stat__value">{plan.accepted.length}</div>
              </div>
              <div className="stat">
                <div className="stat__label">已过滤</div>
                <div className="stat__value">{plan.skipped.length + plan.overflow}</div>
              </div>
              <div className="stat">
                <div className="stat__label">扫描总数</div>
                <div className="stat__value">{plan.totalScanned}</div>
              </div>
            </div>

            <label className="row" style={{ cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={includeTests}
                onChange={(event) => handleIncludeTestsChange(event.target.checked)}
              />
              <span>保留测试目录（默认跳过，避免测试用例稀释检索质量）</span>
            </label>

            {plan.accepted.length === 0 ? (
              <div className="alert alert--error">
                没有可上传的源码文件。请确认选择的是源码目录（如 src），而不是只含依赖或构建产物的目录。
              </div>
            ) : (
              <div>
                <div className="citations__title">目录分布</div>
                <ul className="filelist">
                  {planGroups.map((group) => (
                    <li key={group.name} className="filelist__item">
                      {group.name} · {group.count} 个文件
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {skipGroups.length > 0 ? (
              <div>
                <div className="citations__title">跳过原因</div>
                <ul className="filelist">
                  {skipGroups.map((group) => (
                    <li key={group.reason} className="filelist__item filelist__item--warn">
                      {group.label} · {group.count} 个
                      {group.samples.length > 0 ? `（如 ${group.samples[0]}）` : ''}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}

            {plan.overflow > 0 ? (
              <div className="alert alert--info">
                单批上限 {CODE_MAX_BATCH_FILES} 个，本次有 {plan.overflow} 个文件未纳入，请分批上传。
              </div>
            ) : null}

            <div className="muted">
              路径会自动锚定到 src，入库后形成 src/main/java/... 这样的目录结构。
              上传完成后需到「知识库」触发构建，代码才会进入向量库。
            </div>
          </div>
        ) : null}
      </Modal>

      <Modal
        open={batchReport !== null}
        title="源码目录上传结果"
        width={620}
        onClose={() => setBatchReport(null)}
        footer={
          <button type="button" className="btn btn--primary" onClick={() => setBatchReport(null)}>
            知道了
          </button>
        }
      >
        {batchReport ? (
          <div className="stack">
            <div className="grid grid--stats">
              <div className="stat">
                <div className="stat__label">成功</div>
                <div className="stat__value">{batchReport.success}</div>
              </div>
              <div className="stat">
                <div className="stat__label">失败</div>
                <div className="stat__value">{batchReport.failed.length}</div>
              </div>
              <div className="stat">
                <div className="stat__label">本地过滤</div>
                <div className="stat__value">
                  {batchReport.skipGroups.reduce((sum, group) => sum + group.count, 0) +
                    batchReport.overflow}
                </div>
              </div>
            </div>

            {batchReport.aborted ? (
              <div className="alert alert--info">
                已手动停止：成功 {batchReport.success} / 计划 {batchReport.total}。
              </div>
            ) : (
              <div className="alert alert--success">
                计划 {batchReport.total} 个文件，成功 {batchReport.success} 个。
              </div>
            )}

            {batchReport.failed.length > 0 ? (
              <div>
                <div className="citations__title">失败明细</div>
                <ul className="filelist">
                  {batchReport.failed.slice(0, 20).map((item) => (
                    <li key={item.path} className="filelist__item filelist__item--warn">
                      {item.path} — {item.message}
                    </li>
                  ))}
                </ul>
                {batchReport.failed.length > 20 ? (
                  <div className="muted">仅显示前 20 条。</div>
                ) : null}
              </div>
            ) : null}

            {batchReport.success > 0 ? (
              <div className="muted">
                下一步：到「知识库」标签页触发构建，代码才会进入向量库参与检索。
              </div>
            ) : null}
          </div>
        ) : null}
      </Modal>

      {confirmNode}
    </div>
  )
}
