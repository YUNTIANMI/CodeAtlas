import { SOURCE_TYPE_LABELS, type ChunkSourceType } from '../types/api'
import { formatScore } from '../utils/format'

export interface CitationLike {
  sourceType: ChunkSourceType
  sourceId: number
  chunkIndex: number
  score: number
  snippet: string
  /** 对话消息的引用会带文件名。 */
  fileName?: string
}

export default function Citations({ items }: { items: CitationLike[] }) {
  if (!items || items.length === 0) {
    return null
  }

  return (
    <div className="citations">
      <div className="citations__title">引用来源（{items.length}）</div>
      <ol className="citations__list">
        {items.map((item, index) => (
          <li
            key={`${item.sourceType}-${item.sourceId}-${item.chunkIndex}-${index}`}
            className="citation"
          >
            <div className="citation__head">
              <span className={`badge badge--source-${item.sourceType.toLowerCase()}`}>
                {SOURCE_TYPE_LABELS[item.sourceType] ?? item.sourceType}
              </span>
              <span className="citation__name">{item.fileName ?? `来源 #${item.sourceId}`}</span>
              <span className="citation__meta">
                片段 {item.chunkIndex} · 相似度 {formatScore(item.score)}
              </span>
            </div>
            <p className="citation__snippet">{item.snippet}</p>
          </li>
        ))}
      </ol>
    </div>
  )
}
