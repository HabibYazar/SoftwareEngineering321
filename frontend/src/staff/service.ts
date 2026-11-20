import { StaffError, type StaffService } from './types'

export const staffMockEnabled = import.meta.env.DEV && import.meta.env.VITE_STAFF_MOCK === 'true'

// Resmî tabanda GET /api/v1/staff bulunmuyor. Birleşmeyen PR'ın API'sine istek gönderilmez.
// Sentetik dizin yalnızca mevcut geliştirme bayrağı açıkken etkinleşir.
export async function createStaffService(): Promise<StaffService> {
  if (import.meta.env.DEV && import.meta.env.VITE_STAFF_MOCK === 'true') {
    const { createMockStaffService } = await import('./mockStaffService')
    return createMockStaffService()
  }
  return {
    async listClinicians(_department, signal) {
      signal?.throwIfAborted()
      throw new StaffError('unavailable')
    },
  }
}