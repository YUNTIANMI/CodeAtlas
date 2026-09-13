import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { RoleBadge } from '../components/Badges'
import FormRow from '../components/FormRow'
import Modal from '../components/Modal'
import PageHeader from '../components/PageHeader'
import Spinner from '../components/Spinner'
import { useAsync } from '../hooks/useAsync'
import { useConfirm } from '../hooks/useConfirm'
import { ApiError } from '../services/http'
import { projectApi } from '../services/project'
import { useToast } from '../stores/ToastContext'
import type { ProjectRequest, ProjectVO } from '../types/api'
import { formatDateTime } from '../utils/format'
import AgentTab from './tabs/AgentTab'
import ChatTab from './tabs/ChatTab'
import CodeTab from './tabs/CodeTab'
import DocumentsTab from './tabs/DocumentsTab'
import GitTab from './tabs/GitTab'
import KnowledgeTab from './tabs/KnowledgeTab'
import MembersTab from './tabs/MembersTab'
import OverviewTab from './tabs/OverviewTab'
import ReviewTab from './tabs/ReviewTab'

const TABS = [
  { key: 'overview', label: '概览' },
  { key: 'members', label: '成员' },
  { key: 'documents', label: '文档' },
  { key: 'code', label: '代码' },
  { key: 'knowledge', label: '知识库' },
  { key: 'chat', label: 'AI 问答' },
  { key: 'review', label: 'Code Review' },
  { key: 'git', label: 'Git 分析' },
  { key: 'agent', label: 'Agent' },
] as const

type TabKey = (typeof TABS)[number]['key']

export default function ProjectDetailPage() {
  const { projectId: projectIdParam } = useParams<{ projectId: string }>()
  const projectId = Number(projectIdParam)
  const navigate = useNavigate()
  const toast = useToast()
  const { confirm, confirmNode } = useConfirm()
  const [searchParams, setSearchParams] = useSearchParams()

  const { data: project, loading, error, reload } = useAsync<ProjectVO>(
    () => projectApi.detail(projectId),
    [projectId],
  )

  const [editOpen, setEditOpen] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [projectType, setProjectType] = useState('')
  const [techStack, setTechStack] = useState('')
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const rawTab = searchParams.get('tab')
  const activeTab: TabKey = TABS.some((item) => item.key === rawTab) ? (rawTab as TabKey) : 'overview'

  function switchTab(key: TabKey) {
    setSearchParams({ tab: key }, { replace: true })
  }

  function openEdit() {
    if (!project) {
      return
    }
    setName(project.name)
    setDescription(project.description ?? '')
    setProjectType(project.projectType ?? '')
    setTechStack((project.techStack ?? []).join(', '))
    setFormError(null)
    setEditOpen(true)
  }

  async function handleUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(null)

    const trimmedName = name.trim()
    if (!trimmedName) {
      setFormError('项目名称不能为空')
      return
    }

    const payload: ProjectRequest = {
      name: trimmedName,
      description: description.trim(),
      projectType: projectType.trim(),
      techStack: techStack
        .split(/[,，\s]+/)
        .map((item) => item.trim())
        .filter(Boolean),
    }

    setSaving(true)
    try {
      await projectApi.update(projectId, payload)
      toast.success('项目信息已更新')
      setEditOpen(false)
      reload()
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : '更新失败，请稍后重试')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (!project) {
      return
    }
    const confirmed = await confirm({
      title: '删除项目',
      message: `确认删除项目「${project.name}」吗？该操作会同时移除项目的文档、代码与知识库数据，且不可恢复。`,
      confirmText: '删除项目',
      danger: true,
    })
    if (!confirmed) {
      return
    }
    try {
      await projectApi.remove(projectId)
      toast.success('项目已删除')
      navigate('/projects', { replace: true })
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '删除失败，请稍后重试')
    }
  }

  if (loading) {
    return (
      <div className="page-center">
        <Spinner label="正在加载项目…" />
      </div>
    )
  }

  if (error || !project) {
    return (
      <div className="stack">
        <div className="alert alert--error">{error || '项目不存在或无权访问'}</div>
        <Link className="btn" to="/projects">
          返回项目列表
        </Link>
      </div>
    )
  }

  const canManage = project.myRole === 'OWNER' || project.myRole === 'ADMIN'
  const isOwner = project.myRole === 'OWNER'

  return (
    <div className="stack">
      <div className="breadcrumb">
        <Link to="/projects">项目空间</Link>
        <span>/</span>
        <span>{project.name}</span>
      </div>

      <PageHeader
        title={project.name}
        subtitle={
          <div className="row">
            <RoleBadge role={project.myRole} />
            <span>{project.projectType || '未分类'}</span>
            <span>·</span>
            <span>更新于 {formatDateTime(project.updatedAt)}</span>
          </div>
        }
        actions={
          <>
            {canManage ? (
              <button type="button" className="btn" onClick={openEdit}>
                编辑项目
              </button>
            ) : null}
            {isOwner ? (
              <button type="button" className="btn btn--danger" onClick={handleDelete}>
                删除项目
              </button>
            ) : null}
          </>
        }
      />

      <div className="tabs" role="tablist">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.key}
            className={`tab${activeTab === tab.key ? ' tab--active' : ''}`}
            onClick={() => switchTab(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div>
        {activeTab === 'overview' ? <OverviewTab project={project} /> : null}
        {activeTab === 'members' ? <MembersTab project={project} /> : null}
        {activeTab === 'documents' ? (
          <DocumentsTab project={project} onDataChanged={reload} />
        ) : null}
        {activeTab === 'code' ? <CodeTab project={project} onDataChanged={reload} /> : null}
        {activeTab === 'knowledge' ? <KnowledgeTab project={project} /> : null}
        {activeTab === 'chat' ? <ChatTab project={project} /> : null}
        {activeTab === 'review' ? <ReviewTab project={project} /> : null}
        {activeTab === 'git' ? <GitTab project={project} /> : null}
        {activeTab === 'agent' ? <AgentTab project={project} /> : null}
      </div>

      <Modal
        open={editOpen}
        title="编辑项目"
        onClose={() => setEditOpen(false)}
        footer={
          <>
            <button type="button" className="btn" onClick={() => setEditOpen(false)}>
              取消
            </button>
            <button type="submit" form="edit-project-form" className="btn btn--primary" disabled={saving}>
              {saving ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <form id="edit-project-form" className="form" onSubmit={handleUpdate}>
          {formError ? <div className="alert alert--error">{formError}</div> : null}

          <FormRow label="项目名称">
            <input
              className="input"
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
              autoFocus
            />
          </FormRow>

          <FormRow label="项目描述">
            <textarea
              className="textarea textarea--short"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </FormRow>

          <FormRow label="项目类型">
            <input
              className="input"
              value={projectType}
              onChange={(event) => setProjectType(event.target.value)}
            />
          </FormRow>

          <FormRow label="技术栈" hint="用逗号或空格分隔">
            <input
              className="input"
              value={techStack}
              onChange={(event) => setTechStack(event.target.value)}
            />
          </FormRow>
        </form>
      </Modal>

      {confirmNode}
    </div>
  )
}
