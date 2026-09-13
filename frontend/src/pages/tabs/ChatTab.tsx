import { useEffect, useRef, useState } from 'react'
import Citations from '../../components/Citations'
import EmptyState from '../../components/EmptyState'
import Spinner from '../../components/Spinner'
import { useAsync } from '../../hooks/useAsync'
import { useConfirm } from '../../hooks/useConfirm'
import { chatApi } from '../../services/chat'
import { ApiError } from '../../services/http'
import { useToast } from '../../stores/ToastContext'
import type { ConversationVO, MessageVO, ProjectVO } from '../../types/api'
import { formatRelative } from '../../utils/format'

export default function ChatTab({ project }: { project: ProjectVO }) {
  const toast = useToast()
  const { confirm, confirmNode } = useConfirm()
  const { data: conversations, loading, error, reload } = useAsync<ConversationVO[]>(
    () => chatApi.conversations(project.id),
    [project.id],
  )

  const [activeId, setActiveId] = useState<number | null>(null)
  const [messages, setMessages] = useState<MessageVO[]>([])
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [question, setQuestion] = useState('')
  const [asking, setAsking] = useState(false)
  const threadRef = useRef<HTMLDivElement>(null)

  const list = conversations ?? []

  useEffect(() => {
    if (threadRef.current) {
      threadRef.current.scrollTop = threadRef.current.scrollHeight
    }
  }, [messages, asking])

  async function openConversation(id: number) {
    setActiveId(id)
    setMessagesLoading(true)
    try {
      setMessages(await chatApi.messages(id))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '读取会话消息失败')
    } finally {
      setMessagesLoading(false)
    }
  }

  function startNew() {
    setActiveId(null)
    setMessages([])
  }

  async function handleAsk() {
    const trimmed = question.trim()
    if (!trimmed) {
      toast.error('请输入问题内容')
      return
    }
    const pending: MessageVO = {
      id: -Date.now(),
      conversationId: activeId ?? 0,
      role: 'USER',
      content: trimmed,
      model: null,
      createdAt: new Date().toISOString(),
      citations: [],
    }
    setMessages((prev) => [...prev, pending])
    setQuestion('')
    setAsking(true)
    try {
      const response = await chatApi.ask(project.id, {
        conversationId: activeId ?? undefined,
        question: trimmed,
      })
      setMessages((prev) => {
        const withoutPending = prev.filter((item) => item.id !== pending.id)
        return [...withoutPending, { ...pending, id: pending.id, conversationId: response.conversationId }, response.message]
      })
      setActiveId(response.conversationId)
      reload()
    } catch (err) {
      setMessages((prev) => prev.filter((item) => item.id !== pending.id))
      toast.error(err instanceof ApiError ? err.message : '提问失败，请稍后重试')
    } finally {
      setAsking(false)
    }
  }

  async function handleDeleteConversation(conversation: ConversationVO) {
    const confirmed = await confirm({
      title: '删除会话',
      message: `确认删除会话「${conversation.title}」吗？`,
      confirmText: '删除',
      danger: true,
    })
    if (!confirmed) {
      return
    }
    try {
      await chatApi.removeConversation(conversation.id)
      toast.success('会话已删除')
      if (activeId === conversation.id) {
        startNew()
      }
      reload()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '删除会话失败')
    }
  }

  return (
    <div className="chat">
      <aside className="card">
        <div className="row row--between">
          <span className="card__title">我的会话</span>
          <button type="button" className="btn btn--sm" onClick={startNew}>
            + 新会话
          </button>
        </div>
        <div className="divider" />

        {loading ? <Spinner label="加载中…" /> : null}
        {error ? <div className="alert alert--error">{error}</div> : null}

        {!loading && !error ? (
          list.length === 0 ? (
            <p className="muted">暂无历史会话，直接在右侧提问即可创建。</p>
          ) : (
            <div className="conv-list">
              {list.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={`conv-item${activeId === item.id ? ' conv-item--active' : ''}`}
                  onClick={() => void openConversation(item.id)}
                >
                  <span className="conv-item__title">{item.title}</span>
                  <span
                    role="button"
                    tabIndex={0}
                    className="icon-btn"
                    title="删除会话"
                    onClick={(event) => {
                      event.stopPropagation()
                      void handleDeleteConversation(item)
                    }}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') {
                        event.stopPropagation()
                        void handleDeleteConversation(item)
                      }
                    }}
                  >
                    ×
                  </span>
                </button>
              ))}
            </div>
          )
        ) : null}
      </aside>

      <section className="card">
        <div className="row row--between">
          <span className="card__title">
            {activeId ? `会话 #${activeId}` : '新的对话'}
          </span>
          <span className="muted">回答会附带知识库引用来源</span>
        </div>
        <div className="divider" />

        {messagesLoading ? (
          <Spinner label="正在加载会话消息…" />
        ) : messages.length === 0 ? (
          <EmptyState
            title="开始与项目知识库对话"
            description="提问将基于已构建的知识库检索并生成带引用的回答。若尚未构建索引，请先到「知识库」标签页构建。"
          />
        ) : (
          <div className="chat-thread" ref={threadRef}>
            {messages.map((message) => (
              <div
                key={message.id}
                className={`bubble ${message.role === 'USER' ? 'bubble--user' : 'bubble--assistant'}`}
              >
                <div className="bubble__meta">
                  <span>{message.role === 'USER' ? '我' : 'AI'}</span>
                  {message.model ? <span className="badge badge--muted">{message.model}</span> : null}
                  <span>{formatRelative(message.createdAt)}</span>
                </div>
                <div className="bubble__text">{message.content}</div>
                {message.role === 'ASSISTANT' ? <Citations items={message.citations} /> : null}
              </div>
            ))}
            {asking ? (
              <div className="bubble bubble--assistant">
                <div className="bubble__meta">AI</div>
                <div className="bubble__text muted">正在检索并生成回答…</div>
              </div>
            ) : null}
          </div>
        )}

        <div className="chat-input">
          <textarea
            className="textarea"
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="输入问题后点击发送（Shift+Enter 换行）"
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                void handleAsk()
              }
            }}
          />
          <button type="button" className="btn btn--primary" onClick={() => void handleAsk()} disabled={asking}>
            {asking ? '发送中…' : '发送'}
          </button>
        </div>
      </section>

      {confirmNode}
    </div>
  )
}
