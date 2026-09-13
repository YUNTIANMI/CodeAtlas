import type { ReactNode } from 'react'

export default function FormRow({
  label,
  hint,
  children,
}: {
  label: string
  hint?: string
  children: ReactNode
}) {
  return (
    <label className="form-row">
      <span className="form-row__label">{label}</span>
      {children}
      {hint ? <span className="form-row__hint">{hint}</span> : null}
    </label>
  )
}
