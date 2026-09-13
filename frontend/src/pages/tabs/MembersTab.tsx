import { useState, type FormEvent } from 'react'
import { RoleBadge } from '../../components/Badges'
import EmptyState from '../../components/EmptyState'
import FormRow from '../../components/FormRow'
import Modal from '../../components/Modal'
import Spinner from '../../components/Spinner'
import { useAsync } from '../../hooks/useAsync'
import { useConfirm } from '../../hooks/useConfirm'
import { ApiError } from '../../services/http'
import { memberApi } from '../../services/member'
import { useAuth } from '../../stores/AuthContext'
import { useToast } from '../../stores/ToastContext'
import { PROJECT_ROLES, type ProjectMemberVO, type ProjectRole, type ProjectVO } from '../../types/api'
import { formatDateTime } from '../../utils/format'

export default function MembersTab({ project }: { project: ProjectVO }) {
  const toast = useToast()
  const { confirm, confirmNode } = useConfirm()
  const { user } = useAuth()
  const { data, loading, error, reload } = useAsync<ProjectMemberVO[]>(
    () => memberApi.list(project.id),
    [project.id],
  )

  const [inviteOpen, setInviteOpen] = useState(false)
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<ProjectRole>('MEMBER')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const canManage = project.myRole === 'OWNER' || project.myRole === 'ADMIN'
  const members = data ?? []

  async function handleInvite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(null)

    const parsed = Number(userId)
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setFormError('请输入有效的用户 ID（正整数）')
      return
    }

    setSubmitting(true)
    try {
      const created = await memberApi.invite(project.id, { userId: parsed, role })
      toast.success(`已添加成员 ${created.username}`)
      setInviteOpen(false)
      setUserId('')
      setRole('MEMBER')
      reload()
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : '添加成员失败')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRoleChange(member: ProjectMemberVO, nextRole: ProjectRole) {
    if (nextRole === member.role) {
      return
    }
    try {
      await memberApi.updateRole(project.id, member.userId, { role: nextRole })
      toast.success(`已将 ${member.username} 的角色调整为 ${nextRole}`)
      reload()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '调整角色失败')
    }
  }

  async function handleRemove(member: ProjectMemberVO) {
    const confirmed = await confirm({
      title: '移出项目',
      message: `确认将 ${member.username} 移出项目吗？`,
      confirmText: '移出',
      danger: true,
    })
    if (!confirmed) {
      return
    }
    try {
      await memberApi.remove(project.id, member.userId)
      toast.success(`已移除成员 ${member.username}`)
      reload()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '移除成员失败')
    }
  }

  return (
    <div className="stack">
      <div className="row row--between">
        <span className="muted">
          共 {members.length} 位成员 · 我的角色 {project.myRole}
        </span>
        {canManage ? (
          <button type="button" className="btn btn--primary" onClick={() => setInviteOpen(true)}>
            + 添加成员
          </button>
        ) : null}
      </div>

      {loading ? <Spinner label="正在加载成员…" /> : null}

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
          {members.length === 0 ? (
            <EmptyState title="暂无成员" description="添加成员后即可协作。" />
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>用户</th>
                  <th>角色</th>
                  <th>加入时间</th>
                  <th style={{ width: 220 }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {members.map((member) => {
                  const isSelf = member.userId === user?.id
                  const isOwnerRow = member.role === 'OWNER'
                  const editable = canManage && !isOwnerRow
                  return (
                    <tr key={member.id}>
                      <td>
                        <div className="row">
                          <span style={{ fontWeight: 600 }}>{member.username}</span>
                          {isSelf ? <span className="badge badge--muted">我</span> : null}
                        </div>
                        <span className="muted">用户 ID {member.userId}</span>
                      </td>
                      <td>
                        {editable ? (
                          <select
                            className="select"
                            style={{ width: 130 }}
                            value={member.role}
                            onChange={(event) =>
                              void handleRoleChange(member, event.target.value as ProjectRole)
                            }
                          >
                            {PROJECT_ROLES.filter((item) => item !== 'OWNER').map((item) => (
                              <option key={item} value={item}>
                                {item}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <RoleBadge role={member.role} />
                        )}
                      </td>
                      <td className="muted">{formatDateTime(member.joinedAt)}</td>
                      <td>
                        {editable ? (
                          <button
                            type="button"
                            className="btn btn--sm btn--danger"
                            onClick={() => void handleRemove(member)}
                          >
                            移出项目
                          </button>
                        ) : (
                          <span className="muted">{isOwnerRow ? '项目所有者不可移除' : '无操作权限'}</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>
      ) : null}

      <Modal
        open={inviteOpen}
        title="添加项目成员"
        onClose={() => setInviteOpen(false)}
        footer={
          <>
            <button type="button" className="btn" onClick={() => setInviteOpen(false)}>
              取消
            </button>
            <button type="submit" form="invite-member-form" className="btn btn--primary" disabled={submitting}>
              {submitting ? '添加中…' : '添加'}
            </button>
          </>
        }
      >
        <form id="invite-member-form" className="form" onSubmit={handleInvite}>
          {formError ? <div className="alert alert--error">{formError}</div> : null}

          <FormRow label="用户 ID" hint="当前后端按用户 ID 邀请，请填写对方注册后的用户 ID">
            <input
              className="input"
              value={userId}
              onChange={(event) => setUserId(event.target.value)}
              placeholder="例如：2"
              inputMode="numeric"
              required
              autoFocus
            />
          </FormRow>

          <FormRow label="项目角色" hint="OWNER 为项目所有者，不可通过该入口授予">
            <select
              className="select"
              value={role}
              onChange={(event) => setRole(event.target.value as ProjectRole)}
            >
              {PROJECT_ROLES.filter((item) => item !== 'OWNER').map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          </FormRow>
        </form>
      </Modal>

      {confirmNode}
    </div>
  )
}
