import assert from 'node:assert/strict'
import test from 'node:test'
import { createAuthorizedRequest } from '../src/auth/authorizedRequest.ts'
import { hasPermission } from '../src/auth/permissions.ts'
import { canAccessRoute, homePath, listWorkflowRoutes, patientEditPath, readPatientEditPath, resolvePath } from '../src/auth/routing.ts'
import { createAuthSession } from '../src/auth/session.ts'
import { roles } from '../src/auth/types.ts'
import { createAppHistory } from '../src/navigation/history.ts'
import { createMockPatientStore } from '../src/patients/mockPatientStore.ts'
import { createMockRegistrationService } from '../src/patients/mockRegistrationService.ts'
import { createHttpPatientProfileService } from '../src/patients/profile/httpProfileService.ts'
import { createMockPatientProfileService } from '../src/patients/profile/mockProfileService.ts'
import { draftFromPatient, isProfileDirty, PatientProfileError, validateProfile } from '../src/patients/profile/model.ts'
import { createProfileSaver } from '../src/patients/profile/saveRunner.ts'
import { createMockPatientSearchService } from '../src/patients/search/mockSearchService.ts'

const token = 'synthetic-test-token'
const missingId = '00000000-0000-4000-8000-000000000000'
const first = () => createMockPatientStore().list()[0]
const receive = () => ({ accessToken: token, tokenType: 'Bearer', role: 'Receptionist', expiresAt: new Date(Date.now() + 60_000).toISOString() })
const relative = { fullName: 'Synthetic Contact', relationship: 'Sibling', phone: '0000000000' }

// Gerçek depo, sentetik kayıt ve arama servislerine de ayrı ayrı verilir.
function setup() {
  const store = createMockPatientStore()
  return {
    store,
    profile: createMockPatientProfileService(store, 0),
    registration: createMockRegistrationService(0, store),
    search: createMockPatientSearchService(store),
  }
}

test('Receptionist and Administrator may open an edit address and the route creates no new menu item', () => {
  const path = patientEditPath(first().id)
  for (const role of [null, ...roles]) {
    const allowed = role === 'Receptionist' || role === 'Administrator'
    assert.equal(hasPermission(role, 'editPatients'), allowed)
    assert.equal(canAccessRoute(path, role), allowed)
    assert.equal(canAccessRoute('/patients/edit', role), allowed)
    assert.equal(resolvePath(path, role), allowed ? path : role ? homePath(role) : '/login')
    assert.equal(listWorkflowRoutes(role).some(entry => entry.path.startsWith('/patients/edit')), false)
  }
})

test('profile URLs have one UUID segment and never the national ID', () => {
  const patient = first()
  assert.equal(readPatientEditPath(patientEditPath(patient.id)), patient.id)
  assert.equal(patientEditPath(patient.id), '/patients/edit/' + patient.id)
  for (const path of ['/patients/edit', '/patients/edit/', '/patients/edit/invalid', '/patients/edit/' + patient.id + '/extra']) {
    assert.equal(readPatientEditPath(path), null)
    assert.equal(resolvePath(path, 'Receptionist'), path)
  }
  assert.equal(readPatientEditPath('/patients/search'), undefined)
})

test('selecting an existing US-07 result loads the exact record and initializes the current values', async () => {
  const { profile, search } = setup()
  const result = await search.search({ mode: 'name', value: 'Avery' }, token)
  assert.equal(result.length, 2)
  const selected = result[1]
  const id = readPatientEditPath(patientEditPath(selected.id))
  const patient = await profile.load(id, token)
  assert.deepEqual(patient, selected)
  assert.equal(draftFromPatient(patient).firstName, selected.firstName)
  assert.equal(draftFromPatient(patient).nationalId, undefined)
})

test('editing every allowed field reuses validation and updates the shared search record', async () => {
  const { profile, search, store } = setup()
  const patient = store.list()[0]
  const draft = {
    ...draftFromPatient(patient), firstName: ' Synthetic ', lastName: 'Updated',
    dateOfBirth: '2004-02-29', gender: 'Not disclosed in demo', phone: '0000000111', address: 'New synthetic address',
    conditionLevel: 'ADVANCED', relativeFullName: relative.fullName,
    relativeRelationship: relative.relationship, relativePhone: relative.phone,
  }
  const { payload } = validateProfile(draft, true)
  assert.deepEqual(payload, {
    firstName: 'Synthetic', lastName: 'Updated', dateOfBirth: '2004-02-29', gender: 'Not disclosed in demo',
    phone: '0000000111', address: 'New synthetic address', conditionLevel: 'ADVANCED', relativeInformation: relative,
  })
  const updated = await profile.update(patient.id, payload, token)
  assert.deepEqual(updated, { ...payload, id: patient.id, nationalId: patient.nationalId })
  assert.deepEqual(await search.search({ mode: 'name', value: 'Synthetic Updated' }, token), [updated])
  assert.deepEqual(await search.search({ mode: 'nationalId', value: patient.nationalId }, token), [updated])
  assert.equal((await search.search({ mode: 'name', value: 'Avery' }, token)).some(record => record.id === patient.id), false)
})

test('registration records stay editable in the same store and their national IDs stay unique', async () => {
  const { registration, profile, search } = setup()
  const patient = { ...first(), nationalId: '00000000992', firstName: 'Synthetic', lastName: 'Registered' }
  const { id: _ignored, ...newPatient } = patient
  const registered = await registration.register(newPatient, token)
  const { payload } = validateProfile({ ...draftFromPatient(registered), lastName: 'Edited' }, false)
  const updated = await profile.update(registered.id, payload, token)
  assert.equal(updated.lastName, 'Edited')
  assert.deepEqual(await search.search({ mode: 'nationalId', value: registered.nationalId }, token), [updated])
  await assert.rejects(registration.register(newPatient, token), { code: 'duplicate' })
})

test('draft and update-service inputs cannot overwrite IDs through extra properties', async () => {
  const { profile, store } = setup()
  const patient = store.list()[0]
  const draft = { ...draftFromPatient(patient), nationalId: '00000000999', id: missingId, firstName: 'Edited' }
  const { payload } = validateProfile(draft, false)
  assert.equal(Object.hasOwn(payload, 'id'), false)
  assert.equal(Object.hasOwn(payload, 'nationalId'), false)
  const updated = await profile.update(patient.id, { ...payload, nationalId: '00000000999', id: missingId }, token)
  assert.equal(updated.nationalId, patient.nationalId)
  assert.equal(updated.id, patient.id)
  assert.equal(store.list().length, 3)
  updated.firstName = 'Mutated copy'
  assert.equal((await profile.load(patient.id, token)).firstName, 'Edited')
})

test('invalid profile fields produce the same field-level constraints as registration', () => {
  const draft = {
    ...draftFromPatient(first()), firstName: '', lastName: 'x'.repeat(101),
    dateOfBirth: '2001-02-29', gender: '', phone: 'x'.repeat(33), address: 'x'.repeat(501), conditionLevel: 'INVALID',
  }
  const { errors } = validateProfile(draft, false)
  assert.deepEqual(Object.keys(errors).sort(), ['address', 'conditionLevel', 'dateOfBirth', 'firstName', 'gender', 'lastName', 'phone'])
})

for (const conditionLevel of ['MILD', 'MODERATE']) {
  test(conditionLevel + ' permits no relative and requires valid details when a contact is chosen', () => {
    const draft = { ...draftFromPatient(first()), conditionLevel }
    assert.equal(validateProfile(draft, false).payload.relativeInformation, null)
    assert.deepEqual(Object.keys(validateProfile(draft, true).errors).sort(), ['relativeFullName', 'relativePhone', 'relativeRelationship'])
  })
}

test('switching to Advanced requires all relative fields before saving', () => {
  const draft = { ...draftFromPatient(first()), conditionLevel: 'ADVANCED' }
  assert.deepEqual(Object.keys(validateProfile(draft, false).errors).sort(), ['relativeFullName', 'relativePhone', 'relativeRelationship'])
  const result = validateProfile({ ...draft, relativeFullName: 'Synthetic', relativeRelationship: 'Sibling', relativePhone: '0000000000' }, false)
  assert.equal(result.payload.relativeInformation.fullName, 'Synthetic')
  assert.equal(result.payload.conditionLevel, 'ADVANCED')
})

test('existing contacts are preserved when conditions change and removal is explicit', async () => {
  const { profile } = setup()
  const initial = first()
  const withRelative = { ...validateProfile(draftFromPatient(initial), false).payload, relativeInformation: relative }
  const patient = await profile.update(initial.id, withRelative, token)
  const draft = { ...draftFromPatient(patient), conditionLevel: 'MODERATE' }
  const { payload } = validateProfile(draft, true)
  const preserved = await profile.update(initial.id, payload, token)
  assert.deepEqual(preserved.relativeInformation, relative)
  const { relativeInformation: _omitted, ...withoutProperty } = payload
  assert.deepEqual((await profile.update(initial.id, withoutProperty, token)).relativeInformation, relative)
  await assert.rejects(profile.update(initial.id, { ...payload, relativeInformation: false }, token), { code: 'invalid' })
  assert.deepEqual((await profile.load(initial.id, token)).relativeInformation, relative)
  const removed = await profile.update(initial.id, validateProfile(draft, false).payload, token)
  assert.equal(removed.relativeInformation, undefined)
})

test('mock rejects invalid, missing, expired or canceled operations without changing the record', async () => {
  const { profile, store } = setup()
  const initial = store.list()[0]
  const { payload } = validateProfile(draftFromPatient(initial), false)
  await assert.rejects(profile.load(missingId, token), { code: 'missing' })
  await assert.rejects(profile.update(missingId, payload, token), { code: 'missing' })
  await assert.rejects(profile.load(initial.id, null), { code: 'unauthorized' })
  await assert.rejects(profile.update(initial.id, payload, null), { code: 'unauthorized' })
  await assert.rejects(profile.update(initial.id, { ...payload, conditionLevel: 'ADVANCED' }, token), { code: 'invalid' })
  const signal = AbortSignal.abort()
  await assert.rejects(profile.update(initial.id, { ...payload, firstName: 'Cancelled' }, token, signal), { name: 'AbortError' })
  assert.deepEqual(store.find(initial.id), initial)
})

test('HTTP GET and PUT use the UUID and Bearer token and never send an editable identity', async () => {
  const patient = first()
  const calls = []
  const service = createHttpPatientProfileService(async (url, options) => {
    calls.push({ url, options })
    return Response.json(patient)
  })
  assert.equal(service.canUpdate, true)
  assert.deepEqual(await service.load(patient.id, token), patient)
  assert.equal(calls[0].url, '/api/v1/patients/' + patient.id)
  assert.equal(calls[0].options.method, 'GET')
  assert.equal(calls[0].options.headers.get('Authorization'), 'Bearer ' + token)
  assert.equal(calls[0].options.cache, 'no-store')
  const { payload } = validateProfile(draftFromPatient(patient), false)
  assert.deepEqual(await service.update(patient.id, { ...payload, nationalId: '00000000999', id: missingId }, token), patient)
  assert.equal(calls[1].url, calls[0].url)
  assert.equal(calls[1].options.method, 'PUT')
  assert.equal(calls[1].options.headers.get('Content-Type'), 'application/json')
  assert.equal(calls[1].options.headers.get('Authorization'), 'Bearer ' + token)
  const { relativeInformation: _omitted, ...expected } = payload
  assert.deepEqual(JSON.parse(calls[1].options.body), expected)
  await assert.rejects(service.load('malformed', token), { code: 'missing' })
  await assert.rejects(service.load(patient.id, null), { code: 'unauthorized' })
  assert.equal(calls.length, 2)
})

test('HTTP errors, network failures, malformed data and wrong records do not fall back to mock', async () => {
  const patient = first()
  for (const [status, code] of [[401, 'unauthorized'], [403, 'forbidden'], [404, 'missing'], [503, 'server']]) {
    const service = createHttpPatientProfileService(async () => new Response('synthetic server content', { status }))
    await assert.rejects(service.load(patient.id, token), error => error.code === code && !error.message.includes('synthetic server content'))
  }
  const invalid = createHttpPatientProfileService(async () => Response.json({ ...patient, id: missingId }))
  await assert.rejects(invalid.load(patient.id, token), { code: 'response' })
  const network = createHttpPatientProfileService(async () => { throw new Error('offline') })
  await assert.rejects(network.load(patient.id, token), { code: 'network' })
})

test('HTTP profile 403 preserves the session and a current 401 expires it', async () => {
  const session = createAuthSession()
  session.update(receive())
  const patient = first()
  let status = 403
  const service = createHttpPatientProfileService(createAuthorizedRequest(session, 'editPatients', async () => new Response(null, { status })))
  await assert.rejects(service.load(patient.id, token), { code: 'forbidden' })
  assert.equal(session.getAccessToken(), token)
  status = 401
  await assert.rejects(service.load(patient.id, token), { code: 'unauthorized' })
  assert.equal(session.getAccessToken(), null)
})

test('a stale profile 401 cannot log out a newer session', async () => {
  const session = createAuthSession()
  session.update(receive())
  let respond
  const service = createHttpPatientProfileService(createAuthorizedRequest(session, 'editPatients',
    () => new Promise(resolve => { respond = resolve })))
  const pending = service.load(first().id, token)
  session.update(receive())
  respond(new Response(null, { status: 401 }))
  await assert.rejects(pending, { code: 'unauthorized' })
  assert.equal(session.getAccessToken(), token)
})

test('save runner suppresses double submits, reports only a settled success and ignores canceled results', async () => {
  const patient = first()
  const { payload } = validateProfile(draftFromPatient(patient), false)
  let calls = 0
  let settle
  const service = { update: () => { calls++; return new Promise(resolve => { settle = resolve }) } }
  const saver = createProfileSaver(service)
  const pending = saver.save(patient, payload, token)
  assert.equal(saver.pending(), true)
  assert.equal(await saver.save(patient, payload, token), undefined)
  assert.equal(calls, 1)
  settle(patient)
  assert.deepEqual(await pending, { patient })
  assert.equal(saver.pending(), false)
  const canceled = saver.save(patient, payload, token)
  saver.cancel()
  settle(patient)
  assert.equal(await canceled, undefined)
})

test('failed save has safe feedback and a response with a changed national ID cannot be success', async () => {
  const patient = first()
  const { payload } = validateProfile(draftFromPatient(patient), false)
  const failed = createProfileSaver({ update: async () => { throw new PatientProfileError('network') } })
  assert.deepEqual(await failed.save(patient, payload, token), { error: new PatientProfileError('network').message })
  const wrongIdentity = createProfileSaver({ update: async () => ({ ...patient, nationalId: '00000000999' }) })
  assert.deepEqual(await wrongIdentity.save(patient, payload, token), { error: new PatientProfileError('response').message })
})

test('an unsaved profile blocks app navigation and native history until the guard allows departure', () => {
  const patient = first()
  const draft = draftFromPatient(patient)
  assert.equal(isProfileDirty(patient, draft, false), false)
  assert.equal(isProfileDirty(patient, draft, true), true)
  let dirty = isProfileDirty(patient, { ...draft, firstName: 'Edited' }, false)
  let position = 0
  const entries = [{ path: homePath('Receptionist'), state: undefined }]
  const history = createAppHistory({
    read: () => entries[position],
    replace: (path, state) => { entries[position] = { path, state } },
    push: (path, state) => { entries.splice(++position, Infinity, { path, state }) },
    go: n => { position += n },
  }, () => !dirty)
  const edit = patientEditPath(patient.id)
  dirty = false
  history.sync('Receptionist')
  history.navigate('/patients/search')
  history.navigate(edit)
  dirty = true
  assert.equal(history.navigate('/patients/search').path, edit)
  history.move(-1)
  assert.equal(position, 2)
  position--
  assert.equal(history.sync('Receptionist').path, edit)
  assert.equal(position, 2)
  history.sync('Receptionist')
  dirty = false
  history.move(-1)
  assert.equal(history.sync('Receptionist').path, '/patients/search')
  history.move(1)
  assert.equal(history.sync('Receptionist').path, edit)
  assert.equal(history.sync(null).path, '/login')
})