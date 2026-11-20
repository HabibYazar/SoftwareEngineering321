import { AuthError, type AuthService, type Role } from './types.ts'

// Bu hesaplar yalnızca geliştirme modundaki sentetik girişte kullanılır.
const accounts: Record<string, Role> = {
  'admin.demo': 'Administrator', 'reception.demo': 'Receptionist', 'doctor.demo': 'Physician',
  'nurse.demo': 'Nurse', 'care.demo': 'PatientCareAssistant', 'lab.demo': 'LabTechnician', 'cleaning.demo': 'CleaningStaff',
}
export function createMockAuthService(delayMs = 450): AuthService {
  const sessions = new Set<string>()
  const delay = () => new Promise(resolve => setTimeout(resolve, delayMs))
  return {
    async login({ username, password }) {
      await delay()
      const role = Object.hasOwn(accounts, username) ? accounts[username] : undefined
      if (!role || password !== 'DemoOnly!321') throw new AuthError('invalid')
      const accessToken = `demo-${crypto.randomUUID()}`
      sessions.add(accessToken)
      return { accessToken, tokenType: 'Bearer', role, expiresAt: new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString() }
    },
    async logout(accessToken) { await delay(); sessions.delete(accessToken) },
  }
}
