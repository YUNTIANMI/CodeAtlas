import { useState, type FormEvent } from 'react'
import Citations from '../../components/Citations'
import EmptyState from '../../components/EmptyState'
import Spinner from '../../components/Spinner'
import { useAsync } from '../../hooks/useAsync'
import { useConfirm } from '../../hooks/useConfirm'
import { ApiError } from '../../services/http'
import { knowledgeApi } from '../../services/knowledge'
import { useToast } from '../../stores/ToastContext'
import type {
  AskAnswerVO,
  IndexStatusVO,
  ProjectVO,
  SearchResultVO,
} from '../../types/api'
import { formatScore } from '../../utils/format'

export default function KnowledgeTab({ project }: { project: ProjectVO }) {
  const toast = useToast()
  const { confirm, confirmNode } = useConfirm()
  const { data: status, loading, error, reload } = useAsync<IndexStatusVO>(
    () => knowledgeApi.status(project.id),
    [project.id],
  )

  const [building, setBuilding] = useState(false)
  const [clearing, setClearing] = useState(false)
  const [mode, setMode] = useState<'search' | 'ask'>('search')
  const [query, setQuery] = useState('')
  const [topK, setTopK] = useState('5')
  const [running, setRunning] = useState(false)
  const [results, setResults] = useState<SearchResultVO[]>([])
  const [answer, setAnswer] = useState<AskAnswerVO | null>(null)

  async function handleBuild() {
    setBuilding(true)
    try {
      const result = await knowledgeApi.build(project.id)
      toast.success(`构建完成：成功 ${result.indexed} / 失败 ${result.failed} / 共 ${result.total}`)
      reload()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '知识库构建失败')
    } finally {
      setBuilding(false)
    }
  }

  async function handleClear() {
    const confirmed = await confirm({
      title: '清空知识库',
      message: '确认清空项目知识库吗？已索引的向量数据将被删除，需重新构建。',
      confirmText: '清空',
      danger: true,
    })
    if (!confirmed) {
      return
    }
    setClearing(true)
    try {
      await knowledgeApi.clear(project.id)
      toast.success('知识库已清空')
      setResults([])
      setAnswer(null)
      reload()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '清空知识库失败')
    } finally {
      setClearing(false)
    }
  }

  async function handleQuery(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = query.trim()
    if (!trimmed) {
      toast.error('请输入检索或提问内容')
      return
    }
    const parsedTopK = Number(topK)
    setRunning(true)
    try {
      if (mode === 'search') {
        setAnswer(null)
        setResults(await knowledgeApi.search(project.id, { query: trimmed, topK: parsedTopK }))
      } else {
        setResults([])
        setAnswer(await knowledgeApi.ask(project.id, { query: trimmed, topK: parsedTopK }))
      }
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '请求失败，请稍后重试')
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="stack">
      <div className="row row--between">
        <span className="muted">
          构建知识库会解析文档与代码并生成向量索引，是 AI 问答、检索与审查的基础。
        </span>
        <div className="row">
          <button type="button" className="btn btn--primary" onClick={() => void handleBuild()} disabled={building}>
            {building ? '构建中…' : '构建 / 重建索引'}
          </button>
          <button type="button" className="btn btn--danger" onClick={() => void handleClear()} disabled={clearing}>
            {clearing ? '清空中…' : '清空知识库'}
          </button>
        </div>
      </div>

      <div className="grid grid--stats">
        <div className="stat">
          <div className="stat__label">片段总数</div>
          <div className="stat__value">{status?.totalChunks ?? '—'}</div>
        </div>
        <div className="stat">
          <div className="stat__label">已索引</div>
          <div className="stat__value">{status?.indexedChunks ?? '—'}</div>
        </div>
        <div className="stat">
          <div className="stat__label">待索引</div>
          <div className="stat__value">{status?.pendingChunks ?? '—'}</div>
        </div>
        <div className="stat">
          <div className="stat__label">嵌入模型</div>
          <div className="stat__value stat__value--sm">{status?.embeddingModel ?? '—'}</div>
        </div>
        <div className="stat">
          <div className="stat__label">向量集合</div>
          <div className="stat__value stat__value--sm">{status?.collection ?? '—'}</div>
        </div>
      </div>

      {loading ? <Spinner label="正在加载索引状态…" /> : null}

      {error ? (
        <div className="alert alert--error">
          {error}
          <button type="button" className="btn btn--sm" style={{ marginLeft: 10 }} onClick={reload}>
            重试
          </button>
        </div>
      ) : null}

      <section className="card">
        <h3 className="card__title">知识检索 / RAG 问答</h3>
        <div className="divider" />

        <form className="inline-form" onSubmit={handleQuery}>
          <div className="form-row">
            <span className="form-row__label">检索内容</span>
            <input
              className="input"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="例如：用户认证流程是如何实现的？"
            />
          </div>
          <select className="select" style={{ width: 140 }} value={topK} onChange={(event) => setTopK(event.target.value)}>
            {['3', '5', '8', '10', '15'].map((item) => (
              <option key={item} value={item}>
                TopK = {item}
              </option>
            ))}
          </select>
          <select
            className="select"
            style={{ width: 130 }}
            value={mode}
            onChange={(event) => setMode(event.target.value as 'search' | 'ask')}
          >
            <option value="search">向量检索</option>
            <option value="ask">RAG 问答</option>
          </select>
          <button type="submit" className="btn btn--primary" disabled={running}>
            {running ? '处理中…' : mode === 'search' ? '检索' : '提问'}
          </button>
        </form>

        <div className="divider" />

        {running ? <Spinner label={mode === 'search' ? '正在检索…' : '正在生成回答…'} /> : null}

        {!running && mode === 'ask' && answer ? (
          <div className="stack">
            <div className="row row--between">
              <span className="card__title">AI 回答</span>
              <span className="badge badge--muted">{answer.model}</span>
            </div>
            <pre className="content-block content-block--light">{answer.answer}</pre>
            <Citations items={answer.citations} />
          </div>
        ) : null}

        {!running && mode === 'search' ? (
          results.length === 0 ? (
            <EmptyState title="暂无检索结果" description="输入问题后点击检索，将返回最相近的知识片段。" />
          ) : (
            <div className="stack">
              <span className="card__title">命中片段（{results.length}）</span>
              {results.map((item, index) => (
                <div key={`${item.sourceType}-${item.sourceId}-${item.chunkIndex}-${index}`} className="citation">
                  <div className="citation__head">
                    <span className={`badge badge--source-${item.sourceType.toLowerCase()}`}>
                      {item.sourceType === 'DOCUMENT' ? '文档' : '代码'}
                    </span>
                    <span className="citation__name">
                      来源 #{item.sourceId} · 片段 {item.chunkIndex}
                    </span>
                    <span className="citation__meta">相似度 {formatScore(item.score)}</span>
                  </div>
                  <p className="citation__snippet">{item.content}</p>
                </div>
              ))}
            </div>
          )
        ) : null}
      </section>

      {confirmNode}
    </div>
  )
}
