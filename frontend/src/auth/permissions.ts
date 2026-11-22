import type { Role } from './types.ts'

// PostgreSQL V3 ve has_permission, bu iki role hasta kayıt, görüntüleme ve güncelleme izni verir.
const patientRoles: readonly Role[] = ['Administrator', 'Receptionist']

// Yönetim demoları yalnızca Administrator içindir. Doktor dizininin erişimi aynı kalır.
const allowedRoles = {
  viewDoctors: ['Receptionist'],
  registerPatients: patientRoles,
  searchPatients: patientRoles,
  editPatients: patientRoles,
  viewAuditLogs: ['Administrator'],
  manageDemoStaff: ['Administrator'],
} satisfies Record<string, readonly Role[]>

export type Permission = keyof typeof allowedRoles

// Yönetici rolü dahil hiçbir rol, açıkça verilmemiş bir izni devralmaz.
export function hasPermission(role: Role | null, permission: Permission): boolean {
  if (role === null || !Object.hasOwn(allowedRoles, permission)) return false
  const permitted: readonly Role[] = allowedRoles[permission]
  return permitted.includes(role)
}