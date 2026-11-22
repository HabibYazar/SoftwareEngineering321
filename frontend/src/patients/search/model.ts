import type { PatientSummary } from '../recentPatients.ts'
import type { Patient } from '../types.ts'
import { nationalIdPattern } from '../validation.ts'

export type SearchMode = 'nationalId' | 'name'
export type PatientQuery = { mode: SearchMode; value: string }

export interface PatientSearchService {
  // Sadece açık demo servisinde bulunur. Gerçek HTTP servisi genel liste isteği üretmez.
  listDemoPatients?(): PatientSummary[]
  search(query: PatientQuery, accessToken: string | null, signal?: AbortSignal): Promise<Patient[]>
}

export const patientNameLimit = 150

// Tek mod her istekte yalnızca bir parametre üretir. Kimlik metin kalır ve baştaki sıfırlar korunur.
export function validatePatientQuery(mode: SearchMode, input: string): { query: PatientQuery } | { error: string } {
  const value = input.trim()
  if (mode === 'nationalId' && !nationalIdPattern.test(value)) {
    return { error: 'Enter a synthetic national ID with exactly 11 digits.' }
  }
  if (mode === 'name' && !value) return { error: 'Enter a patient name or part of a name.' }
  if (mode === 'name' && value.length > patientNameLimit) return { error: 'Use 150 characters or fewer.' }
  return { query: { mode, value } }
}

const messages = {
  invalid: 'The server rejected the search. Check the search criteria and try again.',
  unauthorized: 'Your session is missing or expired. Sign in again before searching.',
  forbidden: 'You do not have permission to search patients.',
  pending: 'Patient search is unavailable because the backend database is not configured.',
  server: 'Patient search is temporarily unavailable.',
  network: 'Could not reach the server. Check your connection and try again.',
  response: 'The server returned an unexpected patient search response.',
}

export class PatientSearchError extends Error {
  readonly code: keyof typeof messages

  // Arayüz, hasta bilgisi içerebilecek ham sunucu hata metni yerine sabit mesaj kullanır.
  constructor(code: keyof typeof messages) {
    super(messages[code])
    this.name = 'PatientSearchError'
    this.code = code
  }
}
