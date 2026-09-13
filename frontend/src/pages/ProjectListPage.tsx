import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { RoleBadge } from '../components/Badges'
import EmptyState from '../components/EmptyState'
import FormRow from '../components/FormRow'
import Modal from '../components/Modal'
import PageHeader from '../components/PageHeader'
import Spinner from '../components/Spinner'
import { useAsync } from '../hooks/useAsync'
import { ApiError } from '../services/http'
import { projectApi } from '../services/project'
import { useAuth } from '../stores/AuthContext'
import { useToast } from '../stores/ToastContext'
import type { ProjectRequest, ProjectVO } from '../types/api'
import { formatDateTime } from '../utils/format'

const PROJECT_TYPE_SUGGESTIONS = ['WEB', 'BACKEND', 'MOBILE', 'DATA', 'DESKTOP', 'OTHER']

export default function ProjectListPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { user } = useAuth()
  const { data, loading, error, reload } = useAsync<ProjectVO[]>(() => projectApi.list(), [])

  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [projectType, setProjectType] = useState('')
  const [techStack, setTechStack] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const projects = data ?? []

  function resetForm() {
    setName('')
    setDescription('')
    setProjectType('')
    setTechStack('')
    setFormError(null)
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(null)

    const trimmedName = name.trim()
    if (!trimmedName) {
      setFormError('项目名称不能为空')
      return
    }

    const payload: ProjectRequest = {
      name: trimmedName,
      description: description.trim() || undefined,
      projectType: projectType.trim() || undefined,
      techStack: techStack
        .split(/[,，\s]+/)
        .map((item) => item.trim())
        .filter(Boolean),
    }

    setSubmitting(true)
    try {
      const created = await projectApi.create(payload)
      toast.success(`项目「${created.name}」创建成功`)
      setOpen(false)
      resetForm()
      reload()
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : '创建失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="stack">
      <PageHeader
        title="项目空间"
        subtitle={`共 ${projects.length} 个项目 · 当前用户 ${user?.displayName || user?.username || ''}`}
        actions={
          <button type="button" className="btn btn--primary" onClick={() => setOpen(true)}>
            + 新建项目
          </button>
        }
      />

      {loading ? <Spinner label="正在加载项目…" /> : null}

      {error ? (
        <div className="alert alert--error">
          {error}
          <button type="button" className="btn btn--sm" style={{ marginLeft: 10 }} onClick={reload}>
            重试
          </button>
        </div>
      ) : null}

      {!loading && !error && projects.length === 0 ? (
        <div className="card">
          <EmptyState
            title="还没有项目"
            description="创建第一个项目，上传文档与代码后即可体验 AI 问答、语义检索、Code Review 与 Agent。"
            action={
              <button type="button" className="btn btn--primary" onClick={() => setOpen(true)}>
                创建项目
              </button>
            }
          />
        </div>
      ) : null}

      <div className="grid grid--projects">
        {projects.map((project) => (
          <article
            key={project.id}
            className="project-card"
            role="button"
            tabIndex={0}
            onClick={() => navigate(`/projects/${project.id}`)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                navigate(`/projects/${project.id}`)
              }
            }}
          >
            <div className="row row--between">
              <span className="project-card__title">{project.name}</span>
              <RoleBadge role={project.myRole} />
            </div>

            <p className="project-card__desc">{project.description || '暂无项目描述'}</p>

            {project.techStack && project.techStack.length > 0 ? (
              <div className="chips">
                {project.techStack.slice(0, 6).map((tech) => (
                  <span key={tech} className="chip">
                    {tech}
                  </span>
                ))}
              </div>
            ) : null}

            <div className="project-card__foot">
              <span>{project.projectType || '未分类'}</span>
              <span>更新于 {formatDateTime(project.updatedAt)}</span>
            </div>
          </article>
        ))}
      </div>

      <Modal
        open={open}
        title="新建项目"
        onClose={() => {
          setOpen(false)
          resetForm()
        }}
        footer={
          <>
            <button
              type="button"
              className="btn"
              onClick={() => {
                setOpen(false)
                resetForm()
              }}
            >
              取消
            </button>
            <button type="submit" form="create-project-form" className="btn btn--primary" disabled={submitting}>
              {submitting ? '创建中…' : '创建'}
            </button>
          </>
        }
      >
        <form id="create-project-form" className="form" onSubmit={handleCreate}>
          {formError ? <div className="alert alert--error">{formError}</div> : null}

          <FormRow label="项目名称">
            <input
              className="input"
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="例如：CodeAtlas"
              required
              autoFocus
            />
          </FormRow>

          <FormRow label="项目描述" hint="可选，简要说明项目目标">
            <textarea
              className="textarea textarea--short"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="一句话说明这个项目要解决什么问题"
            />
          </FormRow>

          <FormRow label="项目类型" hint="可选，自由填写">
            <input
              className="input"
              list="project-type-options"
              value={projectType}
              onChange={(event) => setProjectType(event.target.value)}
              placeholder="WEB / BACKEND / MOBILE …"
            />
            <datalist id="project-type-options">
              {PROJECT_TYPE_SUGGESTIONS.map((item) => (
                <option key={item} value={item} />
              ))}
            </datalist>
          </FormRow>

          <FormRow label="技术栈" hint="可选，用逗号或空格分隔，例如：Java Spring Boot MySQL">
            <input
              className="input"
              value={techStack}
              onChange={(event) => setTechStack(event.target.value)}
              placeholder="Java, Spring Boot, MySQL, React"
            />
          </FormRow>
        </form>
      </Modal>
    </div>
  )
}
