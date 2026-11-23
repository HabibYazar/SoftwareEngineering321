import { DepartmentError, parseDepartments } from './departments.ts'

// Geçici PR şekli ancak açık DEV uyumluluk bayrağıyla kullanılır. Karışık şekiller reddedilir.
export function parsePr251Departments(value: unknown) {
  if (!Array.isArray(value)) throw new DepartmentError('response')
  return parseDepartments(value.map((item: unknown) => {
    if (!item || typeof item !== 'object' || Array.isArray(item) || Object.hasOwn(item, 'name')) throw new DepartmentError('response')
    const { id, department } = item as Record<string, unknown>
    return { id, name: department }
  }))
}
