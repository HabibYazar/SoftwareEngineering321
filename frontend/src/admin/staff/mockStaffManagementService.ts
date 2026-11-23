import { roles, type Role } from '../../auth/types.ts'
import { demoPermissions, type DemoPermission, type DemoStaff, type StaffManagementService } from './model.ts'

const activeAdministrator = 'demo-admin'
const departments = [
  { id: 'demo-dept-cardiology', name: 'Cardiology' },
  { id: 'demo-dept-emergency', name: 'Emergency' },
  { id: 'demo-dept-pediatrics', name: 'Pediatrics' },
]
const seed: DemoStaff[] = [
  { id: activeAdministrator, username: 'admin.demo', displayName: 'Demo Administrator', role: 'Administrator', active: true, departmentIds: [] },
  { id: 'demo-backup-admin', username: 'backup-admin.demo', displayName: 'Backup Demo Administrator', role: 'Administrator', active: true, departmentIds: [] },
  { id: 'demo-reception', username: 'reception.demo', displayName: 'Demo Receptionist', role: 'Receptionist', active: true, departmentIds: [] },
  { id: 'demo-physician', username: 'doctor.demo', displayName: 'Dr. Demo Rowan', role: 'Physician', active: true, departmentIds: ['demo-dept-cardiology'] },
]

// Sadece bu servisin belleğine yazılır. Uygulamanın giriş hesapları ve yetki tablosu bu kayıtlardan bağımsızdır.
export function createMockStaffManagementService(): StaffManagementService {
  const staff = new Map(seed.map(person => [person.id, structuredClone(person)]))
  const rolePermissions = Object.fromEntries(roles.map(role => [role, [] as DemoPermission[]])) as Record<Role, DemoPermission[]>
  rolePermissions.Administrator = Object.keys(demoPermissions) as DemoPermission[]
  rolePermissions.Receptionist = ['registerPatients', 'searchPatients']

  function get(id: string) {
    const person = staff.get(id)
    if (!person) throw new Error('Demo staff account not found.')
    return person
  }

  // Son etkin yönetici kontrolü servis katmanında da çalışır, böylece arayüz denetimi atlanamaz.
  function protectAdministrator(person: DemoStaff) {
    if (person.role === 'Administrator' && person.active
      && !Array.from(staff.values()).some(other => other.id !== person.id && other.role === 'Administrator' && other.active)) {
      throw new Error('At least one active demo Administrator is required.')
    }
    if (person.id === activeAdministrator) throw new Error('The signed-in demo Administrator must stay active and retain their role.')
  }

  return {
    async list(signal) {
      signal?.throwIfAborted()
      return structuredClone({ staff: [...staff.values()], departments, rolePermissions })
    },
    async save(input, id) {
      const current = id ? get(id) : undefined
      const username = input.username.trim().toLowerCase()
      const displayName = input.displayName.trim()
      if (!/^[a-z0-9][a-z0-9._-]{2,39}$/.test(username)) {
        throw new Error('Use a 3–40 character demo username with letters, digits, dots, underscores or dashes.')
      }
      if (!displayName || displayName.length > 100 || !roles.includes(input.role)) throw new Error('Enter a name and a valid staff role.')
      if ([...staff.values()].some(person => person.id !== id && person.username === username)) {
        throw new Error('That demo username already exists.')
      }
      if (current?.role === 'Administrator' && input.role !== 'Administrator') protectAdministrator(current)
      const person: DemoStaff = {
        id: current?.id ?? crypto.randomUUID(), username, displayName, role: input.role,
        active: current?.active ?? true,
        departmentIds: input.role === 'Physician' ? current?.departmentIds ?? [] : [],
      }
      staff.set(person.id, person)
    },
    async setActive(id, active) {
      const person = get(id)
      if (!active) protectAdministrator(person)
      staff.set(id, { ...person, active })
    },
    async requestPasswordReset(id) {
      if (!get(id).active) throw new Error('Activate this demo account before requesting a reset.')
      // Gösterim isteği parola oluşturmaz ve gerçek bir bildirim göndermez.
    },
    async setPermission(role, permission, enabled) {
      if (!roles.includes(role) || !Object.hasOwn(demoPermissions, permission)) throw new Error('Invalid demo permission.')
      const current = rolePermissions[role].filter(item => item !== permission)
      rolePermissions[role] = enabled ? [...current, permission] : current
    },
    async setAssignment(id, departmentId, assigned) {
      const person = get(id)
      if (person.role !== 'Physician' || !departments.some(department => department.id === departmentId)) {
        throw new Error('Choose a demo physician and a listed department.')
      }
      const current = person.departmentIds.filter(item => item !== departmentId)
      staff.set(id, { ...person, departmentIds: assigned ? [...current, departmentId] : current })
    },
  }
}
