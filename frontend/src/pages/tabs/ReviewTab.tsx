import { useState, type FormEvent } from 'react'
import { CategoryBadge, SeverityBadge } from '../../components/Badges'
import EmptyState from '../../components/EmptyState'
import FormRow from '../../components/FormRow'
import Modal from '../../components/Modal'
import Spinner from '../../components/Spinner'
import { useAsync } from '../../hooks/useAsync'
import { ApiError } from '../../services/http'
import { reviewApi } from '../../services/review'
import { useToast } from '../../stores/ToastContext'
import {
  REVIEW_SEVERITIES,
  REVIEW_SOURCE_LABELS,
  REVIEW_SOURCE_TYPES,
  SEVERITY_LABELS,
  type ProjectVO,
  type ReviewResultVO,
  type ReviewSeverity,
  type ReviewSourceType,
} from '../../types/api'
import { formatDateTime } from '../../utils/format'

export default function ReviewTab({ project }: { project: ProjectVO }) {
  const toast = useToast()
  const [severity, setSeverity] = useState<ReviewSeverity | ''>('')
  const { data, loading, error, reload } = useAsync<ReviewResultVO[]>(
    () => reviewApi.list(project.id, severity || undefined),
    [project.id, severity],
  )

  const [sourceType, setSourceType] = useState<ReviewSourceType>('SNIPPET')
  const [sourceRef, setSourceRef] = useState('')
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [detail, setDetail] = useState<ReviewResultVO | null>(null)

  const results = data ?? []

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(null)
    if (!content.trim()) {
      setFormError('请输入待审查的代码或差异内容')
      return
    }
    setSubmitting(true)
    try {
      const created = await reviewApi.review(project.id, {
        sourceType,
        sourceRef: sourceRef.trim() || undefined,
        content,
      })
      toast.success(`审查完成，发现 ${created.length} 条问题`)
      setContent('')
      setSourceRef('')
      reload()
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : '提交审查失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="stack">
      <section className="card">
        <h3 className="card__title">提交代码审查</h3>
        <div className="divider" />
        <form className="form" onSubmit={handleSubmit}>
          {formError ? <div className="alert alert--error">{formError}</div> : null}

          <div className="row">
            <div className="form-row" style={{ width: 180 }}>
              <span className="form-row__label">审查来源</span>
              <select
                className="select"
                value={sourceType}
                onChange={(event) => setSourceType(event.target.value as ReviewSourceType)}
              >
                {REVIEW_SOURCE_TYPES.map((item) => (
                  <option key={item} value={item}>
                    {REVIEW_SOURCE_LABELS[item]}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-row" style={{ flex: 1, minWidth: 220 }}>
              <span className="form-row__label">来源标识（可选）</span>
              <input
                className="input"
                value={sourceRef}
                onChange={(event) => setSourceRef(event.target.value)}
                placeholder="如文件名 UserService.java 或 commit hash"
              />
            </div>
          </div>

          <FormRow label="代码 / 差异内容" hint="粘贴待审查的代码片段或 git diff">
            <textarea
              className="textarea"
              style={{ minHeight: 180 }}
              value={content}
              onChange={(event) => setContent(event.target.value)}
              placeholder="// 在此粘贴代码…"
            />
          </FormRow>

          <div className="row">
            <button type="submit" className="btn btn--primary" disabled={submitting}>
              {submitting ? '审查中…' : '开始审查'}
            </button>
            <span className="muted">审查由 AI 完成，耗时取决于内容长度</span>
          </div>
        </form>
      </section>

      <section className="card">
        <div className="row row--between">
          <span className="card__title">历史审查结果（{results.length}）</span>
          <div className="form-row" style={{ width: 160 }}>
            <select
              className="select"
              value={severity}
              onChange={(event) => setSeverity(event.target.value as ReviewSeverity | '')}
            >
              <option value="">全部严重级别</option>
              {REVIEW_SEVERITIES.map((item) => (
                <option key={item} value={item}>
                  {SEVERITY_LABELS[item]}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="divider" />

        {loading ? <Spinner label="正在加载审查结果…" /> : null}

        {error ? (
          <div className="alert alert--error">
            {error}
            <button type="button" className="btn btn--sm" style={{ marginLeft: 10 }} onClick={reload}>
              重试
            </button>
          </div>
        ) : null}

        {!loading && !error ? (
          results.length === 0 ? (
            <EmptyState title="暂无审查结果" description="提交代码后，AI 会按正确性、性能、安全与可维护性给出问题项。" />
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>严重级别</th>
                  <th>类别</th>
                  <th>位置</th>
                  <th>问题描述</th>
                  <th>来源</th>
                  <th>时间</th>
                  <th style={{ width: 90 }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {results.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <SeverityBadge severity={item.severity} />
                    </td>
                    <td>
                      <CategoryBadge category={item.category} />
                    </td>
                    <td className="mono">
                      {item.filePath || '—'}
                      {item.line ? `:${item.line}` : ''}
                    </td>
                    <td>
                      <div className="truncate" style={{ maxWidth: 360 }}>
                        {item.description}
                      </div>
                    </td>
                    <td className="muted">{REVIEW_SOURCE_LABELS[item.sourceType] ?? item.sourceType}</td>
                    <td className="muted">{formatDateTime(item.createdAt)}</td>
                    <td>
                      <button type="button" className="btn btn--sm" onClick={() => setDetail(item)}>
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

      <Modal open={detail !== null} title="审查问题详情" width={720} onClose={() => setDetail(null)}>
        {detail ? (
          <div className="stack">
            <div className="row">
              <SeverityBadge severity={detail.severity} />
              <CategoryBadge category={detail.category} />
              <span className="badge badge--muted">{REVIEW_SOURCE_LABELS[detail.sourceType]}</span>
              {detail.model ? <span className="badge badge--muted">{detail.model}</span> : null}
            </div>

            <table className="table">
              <tbody>
                <tr>
                  <td className="muted" style={{ width: 120 }}>
                    文件路径
                  </td>
                  <td className="mono">
                    {detail.filePath || '—'}
                    {detail.line ? ` （第 ${detail.line} 行）` : ''}
                  </td>
                </tr>
                <tr>
                  <td className="muted">来源标识</td>
                  <td className="mono">{detail.sourceRef || '—'}</td>
                </tr>
                <tr>
                  <td className="muted">发现时间</td>
                  <td>{formatDateTime(detail.createdAt)}</td>
                </tr>
              </tbody>
            </table>

            <div>
              <div className="card__title" style={{ marginBottom: 6 }}>
                问题描述
              </div>
              <pre className="content-block content-block--light">{detail.description}</pre>
            </div>

            {detail.risk ? (
              <div>
                <div className="card__title" style={{ marginBottom: 6 }}>
                  风险说明
                </div>
                <pre className="content-block content-block--light">{detail.risk}</pre>
              </div>
            ) : null}

            {detail.suggestion ? (
              <div>
                <div className="card__title" style={{ marginBottom: 6 }}>
                  修复建议
                </div>
                <pre className="content-block content-block--light">{detail.suggestion}</pre>
              </div>
            ) : null}
          </div>
        ) : null}
      </Modal>
    </div>
  )
}
