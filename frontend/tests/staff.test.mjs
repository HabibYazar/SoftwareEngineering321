import test from 'node:test'
import assert from 'node:assert/strict'
import { canViewDoctors, doctorsPath, homePath, resolvePath } from '../src/auth/routing.ts'
import { roles } from '../src/auth/types.ts'
import { filterClinicians, listDepartments } from '../src/staff/filtering.ts'
import { createHttpStaffService } from '../src/staff/httpStaffService.ts'
import { createMockStaffService } from '../src/staff/mockStaffService.ts'

const clinician = {
  id: '00000000-0000-4000-8000-000000000001', displayName: 'Dr. İpek Güneş', department: 'Nöroloji & Rehab',
}
const other = {
  id: '00000000-0000-4000-8000-000000000002', displayName: 'Dr. Avery Rowan', department: 'Pediatrics',
}

// Enjekte edilen fetch, aynı servisi gerçek sunucuya bağımlı olmadan test eder.
test('staff requests use the existing contract and preserve the encoded department name', async () => {
  const calls = []
  const service = createHttpStaffService(async (url, options) => {
    calls.push({ url, options })
    return Response.json([clinician])
  })
  assert.deepEqual(await service.listClinicians(null), [clinician])
  assert.deepEqual(await service.listClinicians(clinician.department), [clinician])
  assert.equal(calls[0].url, '/api/v1/staff')
  assert.equal(calls[1].url, '/api/v1/staff?department=N%C3%B6roloji+%26+Rehab')
  assert.equal(calls[1].options.method, 'GET')
  assert.equal(calls[1].options.headers.Accept, 'application/json')
  assert.equal(calls[1].options.credentials, 'omit')
  assert.ok(calls[1].options.signal instanceof AbortSignal)
})

test('an empty staff array is a successful result', async () => {
  const service = createHttpStaffService(async () => Response.json([]))
  assert.deepEqual(await service.listClinicians(clinician.department), [])
})

// Ham hata içeriği, ayrıştırma veya HTTP hatasında kullanıcıya yansımaz.
test('staff errors never expose raw response content or activate demo data', async () => {
  for (const [status, code] of [[401, 'access'], [403, 'access'], [404, 'unavailable'], [500, 'server']]) {
    const service = createHttpStaffService(async () => new Response('server detail', { status }))
    await assert.rejects(service.listClinicians(null), error => error.code === code && !error.message.includes('server detail'))
  }
  const invalidJson = createHttpStaffService(async () => new Response('<html>server detail</html>'))
  await assert.rejects(invalidJson.listClinicians(null), { code: 'response' })
  const offline = createHttpStaffService(async () => { throw new TypeError('offline details') })
  await assert.rejects(offline.listClinicians(null), { code: 'network' })
})

test('staff data must be an array with valid fields and unique clinician IDs', async () => {
  const invalidValues = [
    { clinicians: [clinician] }, [null], [{ ...clinician, id: 1 }],
    [{ ...clinician, id: 'not-a-uuid' }], [{ ...clinician, displayName: ' ' }],
    [{ ...clinician, department: null }], [clinician, clinician],
  ]
  for (const data of invalidValues) {
    const service = createHttpStaffService(async () => Response.json(data))
    await assert.rejects(service.listClinicians(null), { code: 'response' })
  }
})

test('obsolete staff requests propagate the caller cancellation', async () => {
  const controller = new AbortController()
  const service = createHttpStaffService(async (_url, options) => {
    options.signal.throwIfAborted()
    return Response.json([])
  })
  controller.abort()
  await assert.rejects(service.listClinicians(null, controller.signal), { name: 'AbortError' })
})

test('department choices come from staff records and results exclude mismatched departments', () => {
  assert.deepEqual(listDepartments([other, clinician, clinician]), [clinician.department, other.department])
  assert.deepEqual(listDepartments([]), [])
  assert.deepEqual(filterClinicians([clinician, other], clinician.department, '  ipek gunes  '), [clinician])
  assert.deepEqual(filterClinicians([clinician, other], other.department, 'ipek'), [])
  assert.deepEqual(filterClinicians([clinician, other], other.department, ''), [other])
  assert.deepEqual(filterClinicians([clinician, other], null, ''), [clinician, other])
})

test('the demo staff service filters synthetic records by department', async () => {
  const service = createMockStaffService()
  assert.ok((await service.listClinicians(null)).length > 2)
  const cardiologists = await service.listClinicians('Cardiology')
  assert.ok(cardiologists.length > 0)
  assert.ok(cardiologists.every(doctor => doctor.department === 'Cardiology'))
  assert.deepEqual(await service.listClinicians('Unknown'), [])
})

test('only Receptionist may open the doctor directory', () => {
  assert.equal(resolvePath(doctorsPath, null), '/login')
  for (const role of roles) {
    assert.equal(canViewDoctors(role), role === 'Receptionist')
    assert.equal(resolvePath(doctorsPath, role), role === 'Receptionist' ? doctorsPath : homePath(role))
  }
})
