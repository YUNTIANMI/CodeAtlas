export default function Spinner({ label = '加载中…' }: { label?: string }) {
  return (
    <div className="spinner-wrap">
      <span className="spinner" aria-hidden="true" />
      <span className="spinner__label">{label}</span>
    </div>
  )
}
