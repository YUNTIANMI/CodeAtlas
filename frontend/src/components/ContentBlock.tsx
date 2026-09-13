/**
 * 纯文本内容展示。使用 <pre> 保留换行与缩进，且不做任何 HTML 注入，
 * 从根本上避免 XSS（模型输出、文件内容均按纯文本渲染）。
 */
export default function ContentBlock({
  text,
  className,
  maxHeight,
}: {
  text: string
  className?: string
  maxHeight?: number
}) {
  return (
    <pre className={`content-block ${className ?? ''}`} style={maxHeight ? { maxHeight } : undefined}>
      {text}
    </pre>
  )
}
