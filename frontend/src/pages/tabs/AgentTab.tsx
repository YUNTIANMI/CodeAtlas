import { useState } from 'react'
import EmptyState from '../../components/EmptyState'
import Spinner from '../../components/Spinner'
import { useAsync } from '../../hooks/useAsync'
import { agentApi } from '../../services/agent'
import { ApiError } from '../../services/http'
import { useToast } from '../../stores/ToastContext'
import type { AgentResponse, ProjectVO, ToolCallVO } from '../../types/api'

const PRESETS = [
  '分析本项目的整体结构与主要模块职责',
  '梳理项目的认证与权限控制流程',
  '总结项目当前存在的潜在风险点',
]

export default function AgentTab({ project }: { project: ProjectVO }) {
  const toast = useToast()
  const history = useAsync<ToolCallVO[]>(() => agentApi.toolCalls(project.id), [project.id])

  const [task, setTask] = useState('')
  const [running, setRunning] = useState(false)
  const [response, setResponse] = useState<AgentResponse | null>(null)

  async function handleRun() {
    const trimmed = task.trim()
    if (!trimmed) {
      toast.error('请输入任务描述')
      return
    }
    setRunning(true)
    try {
      const result = await agentApi.run(project.id, { task: trimmed })
      setResponse(result)
      toast.success(`任务完成，调用工具 ${result.toolCalls.length} 次`)
      history.reload()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Agent 任务执行失败')
    } finally {
      setRunning(false)
    }
  }

  const historyList = history.data ?? []

  return (
    <div className="stack">
      <section className="card">
        <h3 className="card__title">AI Agent 任务</h3>
        <div className="divider" />
        <p className="muted">
          Agent 会自动调用只读工具（检索知识库、读取文件、查询提交等）分析项目并给出结论。
        </p>

        <div className="chips" style={{ marginTop: 12 }}>
          {PRESETS.map((preset) => (
            <button
              key={preset}
              type="button"
              className="chip"
              style={{ border: 'none', cursor: 'pointer' }}
              onClick={() => setTask(preset)}
            >
              {preset}
            </button>
          ))}
        </div>

        <div className="chat-input">
          <textarea
            className="textarea"
            value={task}
            onChange={(event) => setTask(event.target.value)}
            placeholder="用自然语言描述你的任务，例如：定位用户登录相关的实现代码"
          />
          <button type="button" className="btn btn--primary" onClick={() => void handleRun()} disabled={running}>
            {running ? '执行中…' : '执行任务'}
          </button>
        </div>
      </section>

      {running ? <Spinner label="Agent 正在调用工具分析项目…" /> : null}

      {!running && response ? (
        <section className="card">
          <div className="row row--between">
            <span className="card__title">执行结果</span>
            <span className="badge badge--muted">{response.model}</span>
          </div>
          <div className="divider" />
          <div className="muted" style={{ marginBottom: 8 }}>
            任务：{response.task}
          </div>
          <pre className="content-block content-block--light">{response.answer}</pre>

          {response.toolCalls.length > 0 ? (
            <div className="stack" style={{ marginTop: 16 }}>
              <span className="card__title">工具调用（{response.toolCalls.length}）</span>
              {response.toolCalls.map((call, index) => (
                <div key={`${call.toolName}-${index}`} className="citation">
                  <div className="citation__head">
                    <span className={`badge ${call.success ? 'badge--ok' : 'badge--warn'}`}>
                      {call.success ? '成功' : '失败'}
                    </span>
                    <span className="citation__name mono">{call.toolName}</span>
                    <span className="citation__meta">{call.executionTimeMs} ms</span>
                  </div>
                  {call.input ? (
                    <div className="mono muted" style={{ marginBottom: 6, whiteSpace: 'pre-wrap' }}>
                      入参：{call.input}
                    </div>
                  ) : null}
                  <pre className="content-block" style={{ maxHeight: 260 }}>
                    {call.errorMessage || call.output || '（无输出）'}
                  </pre>
                </div>
              ))}
            </div>
          ) : null}
        </section>
      ) : null}

      <section className="card">
        <div className="row row--between">
          <span className="card__title">工具调用历史</span>
          <button type="button" className="btn btn--sm" onClick={history.reload}>
            刷新
          </button>
        </div>
        <div className="divider" />

        {history.loading ? <Spinner label="正在加载历史记录…" /> : null}
        {history.error ? <div className="alert alert--error">{history.error}</div> : null}

        {!history.loading && !history.error ? (
          historyList.length === 0 ? (
            <EmptyState title="暂无工具调用记录" description="执行一次 Agent 任务后，这里会显示其调用过的工具。" />
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>工具</th>
                  <th>状态</th>
                  <th className="num">耗时</th>
                  <th>入参</th>
                </tr>
              </thead>
              <tbody>
                {historyList.map((call, index) => (
                  <tr key={`history-${call.toolName}-${index}`}>
                    <td className="mono">{call.toolName}</td>
                    <td>
                      <span className={`badge ${call.success ? 'badge--ok' : 'badge--warn'}`}>
                        {call.success ? '成功' : '失败'}
                      </span>
                    </td>
                    <td className="num">{call.executionTimeMs} ms</td>
                    <td className="mono truncate" style={{ maxWidth: 360 }}>
                      {call.input || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )
        ) : null}
      </section>
    </div>
  )
}
