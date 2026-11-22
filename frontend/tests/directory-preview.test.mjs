import test from 'node:test'
import assert from 'node:assert/strict'
import { startResourceRequest } from '../src/components/resourceRequest.ts'
import { directoryDepartmentNames, parseDepartments } from '../src/staff/departments.ts'
import { createHttpDepartmentService } from '../src/staff/httpDepartmentService.ts'
import { createHttpStaffService } from '../src/staff/httpStaffService.ts'
import { parsePr251Departments } from '../src/staff/pr251DepartmentPreview.ts'
import { directoryModes } from '../src/staff/previewMode.ts'

const zero = { id: '00000000-0000-0000-0000-000000000019', name: 'Emergency & Trauma' }
const cardio = { id: '00000000-0000-0000-0000-00000000001a', name: 'Cardiology' }
const clinician = { id: '00000000-0000-0000-0000-000000000020', displayName: 'Dr. Synthetic', department: cardio.name }
const html = '<h1>Private server details</h1>'

test('the directory requires explicit DEV flags, gives mock priority and selects the adapter independently', () => {
  const input = { VITE_STAFF_MOCK: 'true', VITE_STAFF_HTTP_PREVIEW: 'true', VITE_DEPARTMENTS_HTTP_PREVIEW: 'true', VITE_DEPARTMENTS_PR251_COMPAT: 'true' }
  assert.deepEqual(directoryModes(input), { staff: 'unavailable', departments: null })
  assert.deepEqual(directoryModes({ ...input, DEV: true }), { staff: 'mock', departments: null })
  assert.deepEqual(directoryModes({ ...input, DEV: true, VITE_STAFF_MOCK: 'false' }), { staff: 'http', departments: 'pr251' })
  assert.deepEqual(directoryModes({ DEV: true, VITE_DEPARTMENTS_HTTP_PREVIEW: 'true' }), { staff: 'unavailable', departments: 'canonical' })
  assert.deepEqual(directoryModes({ DEV: true, VITE_DEPARTMENTS_PR251_COMPAT: 'true' }), { staff: 'unavailable', departments: null })
})

test('the canonical department client validates UUIDs and unique names and accepts empty arrays', async () => {
  const calls = []
  const service = createHttpDepartmentService(async (url, init) => {
    calls.push({ url, method: init.method, signal: init.signal })
    return Response.json([zero, cardio])
  })
  assert.deepEqual(await service.list(), [zero, cardio])
  assert.equal(calls[0].url, '/api/v1/departments')
  assert.equal(calls[0].method, 'GET')
  assert.ok(calls[0].signal instanceof AbortSignal)
  assert.deepEqual(parseDepartments([]), [])
  for (const invalid of [[zero, { ...cardio, name: zero.name.toLowerCase() }], [zero, { ...cardio, id: zero.id }], [{ ...zero, id: 'broken' }], { ...zero }, [null], [{ ...zero, name: '   ' }]]) {
    assert.throws(() => parseDepartments(invalid), { code: 'response' })
  }
})

test('the temporary PR #251 payload is rejected by the default client and accepted only by its adapter', async () => {
  const temporary = { id: zero.id, department: zero.name }
  const request = async () => Response.json([temporary])
  await assert.rejects(createHttpDepartmentService(request).list(), { code: 'response' })
  assert.deepEqual(await createHttpDepartmentService(request, parsePr251Departments).list(), [zero])
  assert.throws(() => parsePr251Departments([zero]), { code: 'response' })
  assert.throws(() => parsePr251Departments([temporary, temporary]), { code: 'response' })
})

test('staff and department endpoint failures are independent and never create mock successes', async () => {
  const calls = []
  let unavailable = '/api/v1/staff'
  const fixture = async url => {
    calls.push(url)
    return url === unavailable ? new Response(html, { status: 404 })
      : Response.json(url === '/api/v1/departments' ? [zero] : [clinician])
  }
  const departments = createHttpDepartmentService(fixture)
  const doctors = createHttpStaffService(fixture)
  await assert.rejects(doctors.listClinicians(null), { code: 'unavailable' })
  assert.deepEqual(directoryDepartmentNames(await departments.list(), []), [zero.name])
  unavailable = '/api/v1/departments'
  await assert.rejects(departments.list(), error => error.code === 'unavailable' && !error.message.includes('Private'))
  assert.deepEqual(directoryDepartmentNames(undefined, await doctors.listClinicians(null)), [cardio.name])
  assert.equal(calls.length, 4)
})

test('a department with no doctors remains selectable and is queried by name', async () => {
  const staff = createHttpStaffService(async url => {
    assert.equal(url, '/api/v1/staff?department=Emergency+%26+Trauma')
    return Response.json([])
  })
  assert.deepEqual(directoryDepartmentNames([zero, cardio], [clinician]), [cardio.name, zero.name])
  assert.deepEqual(await staff.listClinicians(zero.name), [])
})

test('a cancelled department GET and a late previous resource completion cannot update current results', async () => {
  const controller = new AbortController()
  controller.abort()
  const departments = createHttpDepartmentService(async (_url, { signal }) => {
    signal.throwIfAborted()
    return Response.json([])
  })
  await assert.rejects(departments.list(controller.signal), { name: 'AbortError' })
  const received = []
  let late
  const stop = startResourceRequest(() => new Promise(resolve => { late = resolve }), value => received.push(value))
  stop()
  startResourceRequest(async () => [zero], value => received.push(value))
  late([cardio])
  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(received, [{ status: 'success', data: [zero] }])
})
