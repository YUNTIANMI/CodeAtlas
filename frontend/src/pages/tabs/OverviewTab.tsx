import Spinner from '../../components/Spinner'
import { useAsync } from '../../hooks/useAsync'
import { codeApi } from '../../services/code'
import { documentApi } from '../../services/document'
import { knowledgeApi } from '../../services/knowledge'
import { memberApi } from '../../services/member'
import type { ProjectVO } from '../../types/api'
import { formatDateTime } from '../../utils/format'

export default function OverviewTab({ project }: { project: ProjectVO }) {
  const status = useAsync(() => knowledgeApi.status(project.id), [project.id])
  const members = useAsync(() => memberApi.list(project.id), [project.id])
  const documents = useAsync(() => documentApi.list(project.id), [project.id])
  const codeFiles = useAsync(() => codeApi.list(project.id), [project.id])

  const loading =
    status.loading || members.loading || documents.loading || codeFiles.loading

  return (
    <div className="stack">
      <div className="grid grid--stats">
        <div className="stat">
          <div className="stat__label">项目成员</div>
          <div className="stat__value">{members.data?.length ?? '—'}</div>
        </div>
        <div className="stat">
          <div className="stat__label">文档数量</div>
          <div className="stat__value">{documents.data?.length ?? '—'}</div>
        </div>
        <div className="stat">
          <div className="stat__label">代码文件</div>
          <div className="stat__value">{codeFiles.data?.length ?? '—'}</div>
        </div>
        <div className="stat">
          <div className="stat__label">知识片段 / 已索引</div>
          <div className="stat__value">
            {status.data ? `${status.data.totalChunks} / ${status.data.indexedChunks}` : '—'}
          </div>
        </div>
      </div>

      <div className="grid grid--2">
        <section className="card">
          <h3 className="card__title">项目信息</h3>
          <div className="divider" />
          {loading ? (
            <Spinner label="加载中…" />
          ) : (
            <table className="table">
              <tbody>
                <tr>
                  <td className="muted">项目 ID</td>
                  <td className="mono">{project.id}</td>
                </tr>
                <tr>
                  <td className="muted">项目类型</td>
                  <td>{project.projectType || '未分类'}</td>
                </tr>
                <tr>
                  <td className="muted">我的角色</td>
                  <td>{project.myRole}</td>
                </tr>
                <tr>
                  <td className="muted">技术栈</td>
                  <td>
                    {project.techStack && project.techStack.length > 0 ? (
                      <div className="chips">
                        {project.techStack.map((tech) => (
                          <span key={tech} className="chip">
                            {tech}
                          </span>
                        ))}
                      </div>
                    ) : (
                      '—'
                    )}
                  </td>
                </tr>
                <tr>
                  <td className="muted">创建时间</td>
                  <td>{formatDateTime(project.createdAt)}</td>
                </tr>
                <tr>
                  <td className="muted">更新时间</td>
                  <td>{formatDateTime(project.updatedAt)}</td>
                </tr>
              </tbody>
            </table>
          )}
          {project.description ? (
            <>
              <div className="divider" />
              <p className="muted" style={{ whiteSpace: 'pre-wrap' }}>
                {project.description}
              </p>
            </>
          ) : null}
        </section>

        <section className="card">
          <h3 className="card__title">知识库状态</h3>
          <div className="divider" />
          {status.loading ? (
            <Spinner label="加载中…" />
          ) : status.error ? (
            <div className="alert alert--error">{status.error}</div>
          ) : (
            <table className="table">
              <tbody>
                <tr>
                  <td className="muted">向量集合</td>
                  <td className="mono">{status.data?.collection ?? '—'}</td>
                </tr>
                <tr>
                  <td className="muted">嵌入模型</td>
                  <td className="mono">{status.data?.embeddingModel ?? '—'}</td>
                </tr>
                <tr>
                  <td className="muted">片段总数</td>
                  <td className="num">{status.data?.totalChunks ?? 0}</td>
                </tr>
                <tr>
                  <td className="muted">已索引</td>
                  <td className="num">{status.data?.indexedChunks ?? 0}</td>
                </tr>
                <tr>
                  <td className="muted">待索引</td>
                  <td className="num">{status.data?.pendingChunks ?? 0}</td>
                </tr>
              </tbody>
            </table>
          )}

          <div className="divider" />
          <h4 className="card__title">建议上手顺序</h4>
          <ol className="list-plain" style={{ marginTop: 8, lineHeight: 2 }}>
            <li>在「成员」中邀请协作成员并分配角色</li>
            <li>在「文档」「代码」中上传资料</li>
            <li>到「知识库」构建索引</li>
            <li>使用「AI 问答」「Code Review」「Git 分析」「Agent」体验 AI 能力</li>
          </ol>
        </section>
      </div>
    </div>
  )
}
