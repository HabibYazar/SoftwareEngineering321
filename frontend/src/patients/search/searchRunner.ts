import type { Patient } from '../types.ts'
import { PatientSearchError, type PatientQuery, type PatientSearchService } from './model.ts'

export type SearchState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; patients: Patient[] }
  | { status: 'error'; message: string; canRetry: boolean }

// Yeni arama, ölçüt değişikliği veya sayfadan ayrılma eski isteğin sonucu ekrana yazmasını engeller.
export function createSearchRunner(service: PatientSearchService, publish: (state: SearchState) => void) {
  let active: AbortController | null = null

  function cancel() {
    active?.abort()
    active = null
  }

  async function run(query: PatientQuery, accessToken: string | null) {
    cancel()
    const controller = new AbortController()
    active = controller
    publish({ status: 'loading' })
    try {
      const patients = await service.search(query, accessToken, controller.signal)
      if (!controller.signal.aborted) publish({ status: 'success', patients })
    } catch (error: unknown) {
      if (controller.signal.aborted) return
      const failure = error instanceof PatientSearchError ? error : new PatientSearchError('server')
      publish({ status: 'error', message: failure.message, canRetry: failure.code === 'network' || failure.code === 'server' })
    } finally {
      if (active === controller) active = null
    }
  }

  return { run, cancel }
}
