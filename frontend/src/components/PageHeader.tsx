import type { ReactNode } from 'react'

export default function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string
  subtitle?: ReactNode
  actions?: ReactNode
}) {
  return (
    <header className="page-head">
      <div className="page-head__main">
        <h1 className="page-head__title">{title}</h1>
        {subtitle ? <div className="page-head__sub">{subtitle}</div> : null}
      </div>
      {actions ? <div className="page-head__actions">{actions}</div> : null}
    </header>
  )
}
