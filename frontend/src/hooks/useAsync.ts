import { useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from 'react'
import { ApiError } from '../services/http'

export interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: string | null
  reload: () => void
  setData: Dispatch<SetStateAction<T | null>>
}

/**
 * 通用异步加载 Hook：自动处理 loading / error / 取消，
 * 并暴露 reload 用于操作成功后刷新列表。
 */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [tick, setTick] = useState(0)

  const loaderRef = useRef(loader)
  loaderRef.current = loader

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    loaderRef
      .current()
      .then((result) => {
        if (!cancelled) {
          setData(result)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message : '加载失败，请稍后重试')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
    // deps 由调用方显式控制
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  const reload = useCallback(() => setTick((value) => value + 1), [])

  return { data, loading, error, reload, setData }
}
