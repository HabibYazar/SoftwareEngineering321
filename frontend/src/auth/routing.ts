import { hasPermission, type Permission } from './permissions.ts'
import { roles, type Role } from './types.ts'
import { isPatientId } from '../patients/validation.ts'

export const roleLabels: Record<Role, string> = {
  Administrator: 'Administrator', Receptionist: 'Receptionist', Physician: 'Physician', Nurse: 'Nurse',
  PatientCareAssistant: 'Patient Care Assistant', LabTechnician: 'Lab Technician', CleaningStaff: 'Cleaning Staff',
}

export const doctorsPath = '/doctors'
export const auditLogsPath = '/admin/audit-logs'
export const staffManagementPath = '/admin/staff'
export const registrationPath = '/patients/register'
export const patientSearchPath = '/patients/search'
const patientEditBase = '/patients/edit'

export function patientEditPath(id: string): string {
  return patientEditBase + '/' + encodeURIComponent(id)
}

// undefined başka bir rotayı, null ise eksik veya hatalı profil adresini ifade eder.
export function readPatientEditPath(path: string): string | null | undefined {
  if (path === patientEditBase) return null
  if (!path.startsWith(patientEditBase + '/')) return undefined
  const id = path.slice(patientEditBase.length + 1)
  return isPatientId(id) ? id : null
}

export const canViewDoctors = (role: Role | null) => hasPermission(role, 'viewDoctors')
export const canRegisterPatients = (role: Role | null) => hasPermission(role, 'registerPatients')
export const canSearchPatients = (role: Role | null) => hasPermission(role, 'searchPatients')

// Yan menü, ana sayfa kartları ve rota denetimi aynı ekran tanımlarını kullanır.
export const workflowRoutes = [
  {
    path: doctorsPath, permission: 'viewDoctors', icon: 'medical',
    label: 'Find a doctor', heading: 'Doctor directory', description: 'Browse doctors by department.',
  },
  {
    path: registrationPath, permission: 'registerPatients', icon: 'patient',
    label: 'Register a patient', heading: 'Patient registration',
    description: 'Enter a new patient’s details and relative contact.',
  },
  {
    path: patientSearchPath, permission: 'searchPatients', icon: 'search',
    label: 'Search patients', heading: 'Patient search', description: 'Find patients by national ID or name.',
  },
  {
    path: auditLogsPath, permission: 'viewAuditLogs', icon: 'archive',
    label: 'Audit logs', heading: 'Audit logs', description: 'Review synthetic audit events in the demo.',
  },
  {
    path: staffManagementPath, permission: 'manageDemoStaff', icon: 'staff',
    label: 'Staff management', heading: 'Staff management', description: 'Manage demo accounts, roles and departments.',
  },
] as const satisfies readonly { path: string; permission: Permission; icon: 'medical' | 'patient' | 'search' | 'archive' | 'staff';
  label: string; heading: string; description: string }[]

// Menü ve kartlara yalnızca geçerli oturumun açabileceği ekranlar gider.
export function listWorkflowRoutes(role: Role | null) {
  return workflowRoutes.filter(route => hasPermission(role, route.permission))
}

export function homePath(role: Role): string {
  return '/home/' + role
}

// Bilinmeyen adresler ve başka bir rolün çalışma alanı varsayılan olarak kapalıdır.
export function canAccessRoute(path: string, role: Role | null): boolean {
  if (path === '/foundation') return true
  if (role === null || !roles.includes(role)) return path === '/login'
  if (path === homePath(role)) return true
  if (readPatientEditPath(path) !== undefined) return hasPermission(role, 'editPatients')
  const workflow = workflowRoutes.find(route => route.path === path)
  return workflow !== undefined && hasPermission(role, workflow.permission)
}

// Doğrudan adres, gezinme geçmişi ve ekran koruması aynı izin denetimine başvurur.
export function resolvePath(path: string, role: Role | null): string {
  if (canAccessRoute(path, role)) return path
  return role !== null && roles.includes(role) ? homePath(role) : '/login'
}