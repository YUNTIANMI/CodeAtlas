import type { ReactNode } from 'react'

interface Props {
  title: string
  description?: string
  action?: ReactNode
}

export default function EmptyState({ title, description, action }: Props) {
  return (
    <div className="empty">
      <div className="empty__icon" aria-hidden="true">
        ◎
      </div>
      <div className="empty__title">{title}</div>
      {description ? <p className="empty__desc">{description}</p> : null}
      {action ? <div className="empty__action">{action}</div> : null}
    </div>
  )
}
