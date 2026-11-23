import type { AuditService } from './audit/model'
import type { StaffManagementService } from './staff/model'

// Geliştirme dışındaki derlemeler sentetik servisi yüklemez. Onaylı uç nokta gelene kadar ağ çağrısı yoktur.
export async function createAdminServices(): Promise<{ audit: AuditService | null; staffManagement: StaffManagementService | null }> {
  const [auditModule, staffModule] = await Promise.all([
    import.meta.env.DEV && import.meta.env.VITE_ADMIN_AUDIT_MOCK === 'true' ? import('./audit/mockAuditService') : null,
    import.meta.env.DEV && import.meta.env.VITE_ADMIN_STAFF_MOCK === 'true' ? import('./staff/mockStaffManagementService') : null,
  ])
  return { audit: auditModule?.createMockAuditService() ?? null, staffManagement: staffModule?.createMockStaffManagementService() ?? null }
}
