import type { AuditEvent, AuditService } from './model.ts'

const events: readonly AuditEvent[] = [
  { id: 'demo-event-01', timestamp: '2026-11-19T08:30:00Z', actor: 'admin.demo', role: 'Administrator', action: 'Sign in', target: 'Demo session A', status: 'Success' },
  { id: 'demo-event-02', timestamp: '2026-11-19T08:47:00Z', actor: 'reception.demo', role: 'Receptionist', action: 'Register patient', target: 'Synthetic patient 001', status: 'Success' },
  { id: 'demo-event-03', timestamp: '2026-11-19T09:19:00Z', actor: 'reception.demo', role: 'Receptionist', action: 'Edit patient', target: 'Synthetic patient 001', status: 'Success' },
  { id: 'demo-event-04', timestamp: '2026-11-19T10:00:00Z', actor: 'doctor.demo', role: 'Physician', action: 'View patient', target: 'Synthetic patient 002', status: 'Denied' },
  { id: 'demo-event-05', timestamp: '2026-11-20T12:06:00Z', actor: 'reception.demo', role: 'Receptionist', action: 'Sign out', target: 'Demo session B', status: 'Success' },
]

// Yalnızca sabit sentetik olaylar okunur. Kullanıcı etkileşimleri gerçek denetim kaydına dönüşmez.
export function createMockAuditService(): AuditService {
  return { async list(signal) {
    signal?.throwIfAborted()
    return structuredClone(events)
  } }
}
