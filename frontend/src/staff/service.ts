import type { DepartmentService } from './departments'
import { directoryModes } from './previewMode'
import { StaffError, type StaffService } from './types'

export interface DirectoryServices {
  doctors: StaffService
  departments: DepartmentService | null
  modes: ReturnType<typeof directoryModes>
}

const unavailable: StaffService = {
  async listClinicians(_department, signal) {
    signal?.throwIfAborted()
    throw new StaffError('unavailable')
  },
}

// DEV'de iki HTTP bayrağı bağımsızdır. Üretimde demo ve geçici adaptör modülleri derlemeye girmez;
// yalnızca kanonik GET /api/v1/staff ve GET /api/v1/departments sözleşmeleri kullanılır.
export async function createDirectoryServices(request: typeof fetch): Promise<DirectoryServices> {
  const modes = directoryModes(import.meta.env)
  if (import.meta.env.DEV) {
    let doctors = unavailable
    if (modes.staff === 'mock') {
      doctors = (await import('./mockStaffService')).createMockStaffService()
    } else if (modes.staff === 'http') {
      doctors = (await import('./httpStaffService')).createHttpStaffService(request)
    }
    let departments: DepartmentService | null = null
    if (modes.departments) {
      const { createHttpDepartmentService } = await import('./httpDepartmentService')
      const parser = modes.departments === 'pr251' ? (await import('./pr251DepartmentPreview')).parsePr251Departments : undefined
      departments = createHttpDepartmentService(request, parser)
    }
    return { doctors, departments, modes }
  }
  const [{ createHttpStaffService }, { createHttpDepartmentService }] = await Promise.all([
    import('./httpStaffService'), import('./httpDepartmentService'),
  ])
  return { doctors: createHttpStaffService(request), departments: createHttpDepartmentService(request), modes }
}
