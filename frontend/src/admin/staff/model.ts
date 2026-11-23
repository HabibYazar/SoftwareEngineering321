import type { Role } from '../../auth/types.ts'

export interface StaffInput {
  username: string
  displayName: string
  role: Role
}

export interface DemoStaff extends StaffInput {
  id: string
  active: boolean
  departmentIds: string[]
}

export const demoPermissions = {
  reviewAudit: 'Review synthetic audit events',
  manageAccounts: 'Manage synthetic staff accounts',
  manageAssignments: 'Assign demo doctors to departments',
  registerPatients: 'Register patients',
  searchPatients: 'Search patients',
} as const

export type DemoPermission = keyof typeof demoPermissions

export interface StaffSnapshot {
  staff: DemoStaff[]
  departments: { id: string; name: string }[]
  rolePermissions: Record<Role, DemoPermission[]>
}

export interface StaffManagementService {
  list(signal?: AbortSignal): Promise<StaffSnapshot>
  save(input: StaffInput, id?: string): Promise<void>
  setActive(id: string, active: boolean): Promise<void>
  requestPasswordReset(id: string): Promise<void>
  setPermission(role: Role, permission: DemoPermission, enabled: boolean): Promise<void>
  setAssignment(id: string, departmentId: string, assigned: boolean): Promise<void>
}

// Sentetik aramada kullanıcı adı ve görünen ad birlikte taranır.
export function filterDemoStaff(staff: DemoStaff[], search: string, role: Role | '') {
  const term = search.trim().toLowerCase()
  return staff.filter(person => (!role || role === person.role)
    && (person.displayName.toLowerCase().includes(term) || person.username.toLowerCase().includes(term)))
}
