import assert from 'node:assert/strict'
import test from 'node:test'
import { syntheticTurkishNationalId } from './fixtures/turkishNationalId.mjs'
import { canSearchPatients, homePath, patientSearchPath, registrationPath, resolvePath } from '../src/auth/routing.ts'
import { roles } from '../src/auth/types.ts'
import { createAppHistory } from '../src/navigation/history.ts'
import { createMockPatientStore } from '../src/patients/mockPatientStore.ts'
import { createMockRegistrationService } from '../src/patients/mockRegistrationService.ts'
import { createHttpPatientSearchService } from '../src/patients/search/httpSearchService.ts'
import { createMockPatientSearchService } from '../src/patients/search/mockSearchService.ts'
import { PatientSearchError, validatePatientQuery } from '../src/patients/search/model.ts'
import { createSearchRunner } from '../src/patients/search/searchRunner.ts'

const token = 'synthetic-search-test-token'
const idQuery = { mode: 'nationalId', value: '00000000001' }
const nameQuery = { mode: 'name', value: 'Avery' }

function makeMock() {
  const store = createMockPatientStore()
  return { store, service: createMockPatientSearchService(store, 0) }
}

test('synthetic search matches the complete national ID and preserves leading zeros', async () => {
  const { service } = makeMock()
  const patients = await service.search(idQuery, token)
  assert.equal(patients.length, 1)
  assert.equal(patients[0].nationalId, idQuery.value)
  assert.deepEqual(await service.search({ ...idQuery, value: '000001' }, token), [])
  assert.deepEqual(validatePatientQuery('nationalId', ' 00000000001 ').query, idQuery)
})

test('short, long and non-ASCII national IDs are invalid', () => {
  for (const value of ['012345', '000000000001', '0000000000X', '٠٠٠٠٠٠٠٠٠٠١']) {
    assert.match(validatePatientQuery('nationalId', value).error, /11 digits/)
  }
})

test('partial name finds every matching synthetic patient and supports spaces', async () => {
  const { service } = makeMock()
  const patients = await service.search({ mode: 'name', value: 'veRy' }, token)
  assert.deepEqual(patients.map(patient => patient.lastName), ['Demo', 'Sample'])
  const { query } = validatePatientQuery('name', '  Avery De  ')
  assert.equal(query.value, 'Avery De')
  assert.equal((await service.search(query, token))[0].lastName, 'Demo')
})

test('empty input in either mode and names past the schema limit are rejected', () => {
  assert.ok(validatePatientQuery('nationalId', ' ').error)
  assert.ok(validatePatientQuery('name', '').error)
  assert.ok(validatePatientQuery('name', '   ').error)
  assert.ok(validatePatientQuery('name', 'x'.repeat(151)).error)
  assert.equal(validatePatientQuery('name', 'x'.repeat(150)).query.value.length, 150)
})

test('no matching patients is a successful empty result', async () => {
  const { service } = makeMock()
  const states = []
  const runner = createSearchRunner(service, state => states.push(state))
  await runner.run({ mode: 'name', value: 'No demo match' }, token)
  assert.deepEqual(states, [{ status: 'loading' }, { status: 'success', patients: [] }])
})

test('a synthetic registration is searchable when both demo services share a store', async () => {
  const { store, service } = makeMock()
  const register = createMockRegistrationService(0, store)
  const { id: _fixtureId, ...patient } = store.list()[0]
  const created = await register.register({ ...patient, firstName: 'Searchable', nationalId: syntheticTurkishNationalId('100000044') }, token)
  assert.deepEqual(await service.search({ mode: 'nationalId', value: created.nationalId }, token), [created])
  assert.equal((await service.search({ mode: 'name', value: 'Searchable De' }, token)).length, 1)
})

test('HTTP search sends one encoded parameter, uses Bearer auth and retains all returned name matches', async () => {
  const { store } = makeMock()
  const patients = store.list()
  const calls = []
  const service = createHttpPatientSearchService(async (url, options) => {
    calls.push([url, options])
    return Response.json(url.includes('nationalId=') ? [patients[0]] : patients)
  })
  assert.deepEqual(await service.search(idQuery, token), [patients[0]])
  assert.deepEqual(await service.search({ mode: 'name', value: 'Avery & Example' }, token), patients)
  assert.equal(calls[0][0], '/api/v1/patients?nationalId=00000000001')
  assert.equal(calls[1][0], '/api/v1/patients?name=Avery+%26+Example')
  assert.equal(calls[1][1].method, 'GET')
  assert.equal(calls[1][1].headers.Authorization, 'Bearer ' + token)
  assert.equal(calls[1][1].credentials, 'omit')
  assert.equal(calls[1][1].cache, 'no-store')
})

test('unauthenticated search sends no HTTP request and access errors are distinguished', async () => {
  let requested = false
  const missingToken = createHttpPatientSearchService(async () => { requested = true; return Response.json([]) })
  await assert.rejects(missingToken.search(idQuery, null), { code: 'unauthorized' })
  assert.equal(requested, false)
  for (const [status, code] of [[400, 'invalid'], [401, 'unauthorized'], [403, 'forbidden'], [503, 'server']]) {
    const search = createHttpPatientSearchService(async () => new Response('private error details', { status }))
    await assert.rejects(search.search(idQuery, token), err => err.code === code && !err.message.includes('private error details'))
  }
})

test('malformed results and an ID response for a different patient are rejected', async () => {
  const { store } = makeMock()
  const records = store.list()
  const responses = [
    new Response('{'),
    Response.json({ patients: [] }),
    Response.json([{ firstName: 'Synthetic' }]),
    Response.json([records[1]]),
    Response.json([records[0], records[0]]),
  ]
  for (const response of responses) {
    const search = createHttpPatientSearchService(async () => response)
    await assert.rejects(search.search(idQuery, token), { code: 'response' })
  }
})

test('a real-service failure is not masked by a matching synthetic record', async () => {
  const http = createHttpPatientSearchService(async () => { throw new Error('Synthetic network failure') })
  await assert.rejects(http.search(idQuery, token), { code: 'network' })
  assert.equal((await makeMock().service.search(idQuery, token)).length, 1)
})

test('loading begins before the request resolves and an edited query cancels stale results', async () => {
  const { store } = makeMock()
  const states = []
  let complete
  let pendingSignal
  const runner = createSearchRunner({
    search: (_query, _token, signal) => {
      pendingSignal = signal
      return new Promise(resolve => { complete = resolve })
    },
  }, state => states.push(state))
  const request = runner.run(nameQuery, token)
  assert.deepEqual(states, [{ status: 'loading' }])
  runner.cancel()
  assert.equal(pendingSignal.aborted, true)
  complete(store.list())
  await request
  assert.deepEqual(states, [{ status: 'loading' }])
})

test('search state offers retry for transient errors and keeps authorization errors separate', async () => {
  const states = []
  let code = 'network'
  const runner = createSearchRunner({ search: async () => { throw new PatientSearchError(code) } }, state => states.push(state))
  await runner.run(nameQuery, token)
  assert.equal(states.at(-1).status, 'error')
  assert.equal(states.at(-1).canRetry, true)
  code = 'forbidden'
  await runner.run(nameQuery, token)
  assert.equal(states.at(-1).status, 'error')
  assert.equal(states.at(-1).canRetry, false)
})

test('Receptionist and Administrator can open search and shared history safely returns after sign-out', () => {
  assert.equal(resolvePath(patientSearchPath, null), '/login')
  for (const role of roles) {
    const allowed = role === 'Receptionist' || role === 'Administrator'
    assert.equal(canSearchPatients(role), allowed)
    assert.equal(resolvePath(patientSearchPath, role), allowed ? patientSearchPath : homePath(role))
  }

  // Tarayıcı taklidi, arama ve kayıt rotalarını ortak geçmiş üzerinden sınar.
  const entries = [{ path: homePath('Receptionist'), state: null }]
  let index = 0
  let pending = 0
  const history = createAppHistory({
    read: () => entries[index],
    replace: (path, state) => { entries[index] = { path, state } },
    push: (path, state) => { entries.splice(++index, Infinity, { path, state }) },
    go: delta => { pending = delta },
  })
  history.sync('Receptionist')
  history.navigate(registrationPath)
  assert.equal(history.navigate(patientSearchPath).path, patientSearchPath)
  history.move(-1)
  index += pending
  assert.equal(history.sync('Receptionist').path, registrationPath)
  history.move(1)
  index += pending
  assert.equal(history.sync('Receptionist').path, patientSearchPath)
  assert.equal(history.navigate(homePath('Receptionist')).path, homePath('Receptionist'))
  assert.equal(history.sync(null).path, '/login')
  index--
  assert.equal(history.sync(null).path, '/login')
})
