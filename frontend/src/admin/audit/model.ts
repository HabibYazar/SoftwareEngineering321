import type { Role } from '../../auth/types.ts'

export const auditActions = ['Sign in', 'Sign out', 'View patient', 'Register patient', 'Edit patient'] as const
export type AuditAction = typeof auditActions[number]

export interface AuditEvent {
  id: string
  timestamp: string
  actor: string
  role: Role
  action: AuditAction
  target: string
  status: 'Success' | 'Denied'
}

export interface AuditService {
  list(signal?: AbortSignal): Promise<readonly AuditEvent[]>
}

export interface AuditFilter {
  actor: string
  action: AuditAction | ''
  from: string
  to: string
}

export const emptyAuditFilter: AuditFilter = { actor: '', action: '', from: '', to: '' }

// Veri UTC olarak görüntülenir. Bir bitiş tarihi o günün tamamını kapsar.
export function filterAuditEvents(events: readonly AuditEvent[], filter: AuditFilter, newestFirst: boolean): AuditEvent[] {
  const actor = filter.actor.trim().toLowerCase()
  return events.filter(event => {
    const date = event.timestamp.slice(0, 10)
    return (!actor || event.actor.toLowerCase().includes(actor))
      && (!filter.action || event.action === filter.action)
      && (!filter.from || date >= filter.from) && (!filter.to || date <= filter.to)
  }).sort((left, right) => newestFirst
    ? right.timestamp.localeCompare(left.timestamp) : left.timestamp.localeCompare(right.timestamp))
}
