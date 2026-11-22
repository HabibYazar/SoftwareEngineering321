import { listDepartments } from './filtering.ts'
import type { Clinician } from './types.ts'

const uuid = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i

export interface Department { id: string; name: string }
export interface DepartmentService { list(signal?: AbortSignal): Promise<Department[]> }

const messages = {
  response: 'The departments endpoint returned an unexpected response.',
  access: 'You do not have access to the departments endpoint.',
  unavailable: 'The departments endpoint is unavailable in this backend.',
  network: 'Unable to reach the departments endpoint.',
  server: 'The departments endpoint is temporarily unavailable.',
}

export class DepartmentError extends Error {
  readonly code: keyof typeof messages
  constructor(code: keyof typeof messages) {
    super(messages[code])
    this.code = code
  }
}

// Kalıcı sözleşme yalnızca id ve name okur. Geçici alanı tanıyan kod ayrı modüldedir.
export function parseDepartments(value: unknown): Department[] {
  if (!Array.isArray(value)) throw new DepartmentError('response')
  const ids = new Set<string>()
  const names = new Set<string>()
  return value.map((item: unknown) => {
    if (!item || typeof item !== 'object' || Array.isArray(item) || Object.hasOwn(item, 'department')) {
      throw new DepartmentError('response')
    }
    const { id, name } = item as Record<string, unknown>
    if (typeof id !== 'string' || !uuid.test(id) || typeof name !== 'string' || !name.trim()
      || ids.has(id.toLowerCase()) || names.has(name.trim().toLowerCase())) throw new DepartmentError('response')
    ids.add(id.toLowerCase())
    names.add(name.trim().toLowerCase())
    return { id, name: name.trim() }
  })
}

// Kullanıcı filtreyi ad ile seçer. Böylece her istekte değişen önizleme UUID'si seçimi bozmaz.
export function directoryDepartmentNames(canonical: Department[] | undefined, clinicians: Clinician[]): string[] {
  return canonical ? canonical.map(department => department.name).sort((a, b) => a.localeCompare(b))
    : listDepartments(clinicians)
}
