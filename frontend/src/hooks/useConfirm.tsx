import { useCallback, useState, type ReactNode } from 'react'
import Modal from '../components/Modal'

export interface ConfirmOptions {
  /** 弹窗标题 */
  title?: string
  /** 需要用户确认的说明文案 */
  message: string
  /** 确认按钮文案 */
  confirmText?: string
  /** 取消按钮文案 */
  cancelText?: string
  /** 危险操作：确认按钮使用红色样式 */
  danger?: boolean
}

interface PendingConfirm extends ConfirmOptions {
  resolve: (confirmed: boolean) => void
}

/**
 * 应用内确认弹窗，用于替代 window.confirm。
 *
 * <p>原生 confirm 在以下场景会被浏览器静默拦截并直接返回 false，
 * 表现为「点击删除毫无反应」（既不弹窗也不发请求）：
 * <ul>
 *   <li>页面运行在沙箱 iframe 中且未声明 allow-modals（IDE 内置浏览器常见）</li>
 *   <li>用户曾勾选「阻止此页面创建更多对话框」</li>
 *   <li>部分 WebView / 移动端浏览器</li>
 * </ul>
 * 改用应用内 Modal 后行为完全可控，且与应用整体样式保持一致。
 *
 * 用法：
 * ```tsx
 * const { confirm, confirmNode } = useConfirm()
 *
 * async function handleDelete(doc: DocumentVO) {
 *   if (!(await confirm({ title: '删除文档', message: `确认删除「${doc.name}」吗？`, danger: true }))) {
 *     return
 *   }
 *   ...
 * }
 *
 * return <>{confirmNode}...</>
 * ```
 */
export function useConfirm() {
  const [pending, setPending] = useState<PendingConfirm | null>(null)

  const confirm = useCallback((options: ConfirmOptions | string) => {
    const resolved: ConfirmOptions = typeof options === 'string' ? { message: options } : options
    return new Promise<boolean>((resolve) => {
      setPending({ ...resolved, resolve })
    })
  }, [])

  const settle = useCallback((confirmed: boolean) => {
    setPending((current) => {
      current?.resolve(confirmed)
      return null
    })
  }, [])

  const confirmNode: ReactNode = pending ? (
    <Modal
      open
      title={pending.title ?? '操作确认'}
      width={420}
      onClose={() => settle(false)}
      footer={
        <>
          <button type="button" className="btn" onClick={() => settle(false)}>
            {pending.cancelText ?? '取消'}
          </button>
          <button
            type="button"
            className={pending.danger ? 'btn btn--danger' : 'btn btn--primary'}
            onClick={() => settle(true)}
          >
            {pending.confirmText ?? '确定'}
          </button>
        </>
      }
    >
      <p style={{ margin: 0, lineHeight: 1.7 }}>{pending.message}</p>
    </Modal>
  ) : null

  return { confirm, confirmNode }
}
