import { useEffect, useState } from 'react'
import { StaffError, type Clinician, type StaffService } from './types'

type CompletedRequest =
  | { status: 'success'; clinicians: Clinician[] }
  | { status: 'error'; message: string; canRetry: boolean }

// Yenileme veya tekrar deneme numarası değiştiğinde önceki sonuç yükleniyor olarak görünür.
export function useClinicians(service: StaffService, department: string | null, refreshVersion = 0) {
  const [result, setResult] = useState<{
    refreshVersion: number
    attempt: number
    state: CompletedRequest
  } | null>(null)
  const [attempt, setAttempt] = useState(0)
  const state = result?.refreshVersion === refreshVersion && result.attempt === attempt
    ? result.state : { status: 'loading' as const }

  useEffect(() => {
    const controller = new AbortController()

    // Geç gelen yanıt, iptal edilen veya ekrandan kaldırılan bir isteğin sonucunu yazamaz.
    function finish(state: CompletedRequest) {
      if (!controller.signal.aborted) setResult({ refreshVersion, attempt, state })
    }

    service.listClinicians(department, controller.signal).then(clinicians => {
      finish({ status: 'success', clinicians })
    }).catch((error: unknown) => {
      finish({
        status: 'error',
        message: error instanceof StaffError ? error.message : 'Unable to load doctors.',
        // Yetki yoksa veya backend sürümünde uç nokta yoksa tekrar deneme sunulmaz.
        canRetry: !(error instanceof StaffError && (error.code === 'access' || error.code === 'unavailable')),
      })
    })
    return () => controller.abort()
  }, [service, department, refreshVersion, attempt])

  // Sayfa yenilemesiyle aynı yükleme akışını kullanır, yalnızca bu sorguyu yineler.
  function retry() {
    setAttempt(previous => previous + 1)
  }

  return { state, retry }
}
