import { useEffect, useState } from 'react'
import { startResourceRequest, type ResourceResult } from './resourceRequest.ts'

// Yükleyici veya yenileme değiştiğinde eski veri ilk çizimde gizlenir.
export function useResource<T>(load: ((signal: AbortSignal) => Promise<T>) | null, refreshVersion = 0) {
  const [attempt, setAttempt] = useState(0)
  const [settled, setSettled] = useState<{
    load: typeof load; refreshVersion: number; attempt: number; result: ResourceResult<T>
  } | null>(null)

  useEffect(() => {
    if (!load) return
    return startResourceRequest(load, result => setSettled({ load, refreshVersion, attempt, result }))
  }, [load, refreshVersion, attempt])

  const state = !load ? { status: 'unavailable' as const }
    : settled?.load === load && settled.refreshVersion === refreshVersion && settled.attempt === attempt
      ? settled.result : { status: 'loading' as const }
  return { state, retry: () => setAttempt(previous => previous + 1) }
}
