import assert from 'node:assert/strict'
import test from 'node:test'
import { createHttpAuthService } from '../src/auth/httpAuthService.ts'
import { createAuthSession } from '../src/auth/session.ts'
import { createAuthorizedRequest } from '../src/auth/authorizedRequest.ts'
import { createHttpRegistrationService } from '../src/patients/httpRegistrationService.ts'
import { createHttpPatientSearchService } from '../src/patients/search/httpSearchService.ts'
import { createHttpPatientProfileService } from '../src/patients/profile/httpProfileService.ts'
import { createProfileSaver } from '../src/patients/profile/saveRunner.ts'
import { draftFromPatient, validateProfile } from '../src/patients/profile/model.ts'
import { emptyRegistration, validateRegistration } from '../src/patients/validation.ts'
import { syntheticTurkishNationalId } from './fixtures/turkishNationalId.mjs'

const isoFuture = () => new Date(Date.now() + 60_000).toISOString()
const credentials = { username: 'synthetic.reception', password: 'synthetic-password-only' }
const token = 'synthetic-opaque-token'
const auth = () => ({ accessToken: token, tokenType: 'Bearer', role: 'Receptionist', expiresAt: isoFuture() })
const id = '00000000-0000-4000-8000-000000000042'
const draft = {
  ...emptyRegistration, firstName: 'Synthetic', lastName: 'Contract', nationalId: syntheticTurkishNationalId('100000042'),
  dateOfBirth: '2000-01-01', gender: 'Not disclosed', phone: '05000000000',
  address: 'Synthetic address', conditionLevel: 'MILD',
}
const { payload } = validateRegistration(draft, false)
const patient = { ...payload, id }
const relative = { fullName: 'Synthetic Relative', relationship: 'Sibling', phone: '+905320000000' }

// Resmî Problem biçimiyle yanıt üretir. Bu kayıtlar backend veya kalıcı depoya gitmez.
function problem(status, code, errors) {
  return Response.json({ status, code, title: 'Synthetic problem', detail: 'Do not copy this detail', errors }, {
    status, headers: { 'Content-Type': 'application/problem+json' },
  })
}

test('a controlled HTTP flow uses official auth, registration, search, retrieval, update and logout contracts', async () => {
  const calls = []
  let record
  const responseSession = auth()
  const transport = async (url, options) => {
    calls.push([options.method, url])
    if (url === '/api/v1/auth/login') {
      assert.deepEqual(JSON.parse(options.body), credentials)
      return Response.json(responseSession)
    }
    assert.equal(new Headers(options.headers).get('Authorization'), 'Bearer ' + token)
    if (url === '/api/v1/auth/logout') return new Response(null, { status: 204 })
    if (options.method === 'POST') {
      assert.deepEqual(JSON.parse(options.body), payload)
      record = patient
      return Response.json(record, { status: 201 })
    }
    if (options.method === 'PUT') {
      assert.equal(url, '/api/v1/patients/' + id)
      const body = JSON.parse(options.body)
      assert.equal(Object.hasOwn(body, 'nationalId'), false)
      assert.equal(Object.hasOwn(body, 'id'), false)
      assert.deepEqual(body.relativeInformation, relative)
      record = { ...record, ...body }
      return Response.json(record)
    }
    if (url.includes('?')) return Response.json([record])
    return Response.json(record)
  }
  const session = createAuthSession()
  const authentication = createHttpAuthService(transport)
  session.update(await authentication.login(credentials))
  const protectedRequest = permission => createAuthorizedRequest(session, permission, transport)
  const register = createHttpRegistrationService(protectedRequest('registerPatients'))
  const search = createHttpPatientSearchService(protectedRequest('searchPatients'))
  const profile = createHttpPatientProfileService(protectedRequest('editPatients'))
  assert.deepEqual(await register.register(payload, token), patient)
  const results = await search.search({ mode: 'nationalId', value: patient.nationalId }, token)
  const selected = await profile.load(results[0].id, token)
  const { payload: edit } = validateProfile({
    ...draftFromPatient(selected), lastName: 'Updated', conditionLevel: 'ADVANCED',
    relativeFullName: relative.fullName, relativeRelationship: relative.relationship, relativePhone: '0532 000 00 00',
  }, true)
  const saved = await createProfileSaver(profile).save(selected, edit, token)
  assert.equal(saved.patient.nationalId, patient.nationalId)
  assert.deepEqual(await search.search({ mode: 'name', value: 'Updated' }, token), [saved.patient])
  await authentication.logout(token)
  assert.deepEqual(calls, [
    ['POST', '/api/v1/auth/login'], ['POST', '/api/v1/patients'],
    ['GET', '/api/v1/patients?nationalId=' + patient.nationalId], ['GET', '/api/v1/patients/' + id],
    ['PUT', '/api/v1/patients/' + id], ['GET', '/api/v1/patients?name=Updated'],
    ['POST', '/api/v1/auth/logout'],
  ])
})

test('HTTP 503 INTEGRATION_PENDING never produces a synthetic success or signs the user out', async () => {
  const pending = () => problem(503, 'INTEGRATION_PENDING')
  await assert.rejects(createHttpAuthService(pending).login(credentials), { code: 'pending' })
  const session = createAuthSession()
  session.update(auth())
  const withRole = permission => createAuthorizedRequest(session, permission, pending)
  await assert.rejects(createHttpRegistrationService(withRole('registerPatients')).register(payload, token), { code: 'pending' })
  await assert.rejects(createHttpPatientSearchService(withRole('searchPatients')).search({ mode: 'name', value: 'Synthetic' }, token), { code: 'pending' })
  const profile = createHttpPatientProfileService(withRole('editPatients'))
  await assert.rejects(profile.load(id, token), { code: 'pending' })
  const { payload: edit } = validateProfile(draftFromPatient(patient), false)
  const saved = await createProfileSaver(profile).save(patient, edit, token)
  assert.match(saved.error, /database is not configured/)
  assert.equal(saved.patient, undefined)
  assert.equal(session.getAccessToken(), token)
})

test('login DTO requires a future expiry and protected requests clear an expired local session without network', async () => {
  const { expiresAt: _expiry, ...incomplete } = auth()
  for (const value of [incomplete, { ...incomplete, expiresAt: '2026' }, { ...incomplete, expiresAt: '2000-01-01T00:00:00Z' }]) {
    await assert.rejects(createHttpAuthService(() => Promise.resolve(Response.json(value))).login(credentials), { code: 'response' })
  }
  const session = createAuthSession()
  session.update({ ...auth(), expiresAt: '2000-01-01T00:00:00Z' })
  const request = createAuthorizedRequest(session, 'editPatients', async () => assert.fail('An expired token reached the network'))
  assert.equal((await request('/api/v1/patients/' + id)).status, 401)
  assert.equal(session.getAccessToken(), null)
  assert.match(session.getSnapshot().notice, /expired/)
})

test('Java validation paths map to local messages and never surface arbitrary details', async () => {
  const invalid = () => problem(400, 'VALIDATION_FAILED', [
    { field: 'firstName', code: 'Size', message: 'Do not copy this value' },
    { field: 'relativeInformation.phone', code: 'Pattern', message: 'private input' },
    { field: 'dateOfBirth', code: 'Min', message: 'private input' },
    { field: '__proto__', code: 'Size', message: 'private input' },
  ])
  await assert.rejects(createHttpRegistrationService(invalid).register(payload, token), error => {
    assert.equal(error.code, 'invalid')
    assert.deepEqual(Object.keys(error.fieldErrors), ['firstName', 'relativePhone', 'dateOfBirth'])
    assert.match(error.fieldErrors.firstName, /100/)
    assert.match(error.fieldErrors.dateOfBirth, /1900-01-01/)
    assert.doesNotMatch(JSON.stringify(error.fieldErrors), /private input|Do not copy/)
    return true
  })
  const { payload: edit } = validateProfile(draftFromPatient(patient), false)
  const saved = await createProfileSaver(createHttpPatientProfileService(invalid)).save(patient, edit, token)
  assert.deepEqual(Object.keys(saved.fieldErrors), ['firstName', 'relativePhone', 'dateOfBirth'])
  const immutable = () => problem(400, 'VALIDATION_FAILED', [{ field: 'nationalId', code: 'Immutable', message: 'private input' }])
  await assert.rejects(createHttpPatientProfileService(immutable).update(id, edit, token), { code: 'identity' })
})

test('future birth dates and invalid public-contract phone formats are rejected for both forms', () => {
  const fields = { dateOfBirth: '2999-01-01', phone: 'not a phone' }
  const badRegistration = validateRegistration({ ...draft, ...fields }, false)
  const badProfile = validateProfile({ ...draftFromPatient(patient), ...fields }, false)
  assert.deepEqual(badProfile.errors, badRegistration.errors)
  assert.deepEqual(Object.keys(badProfile.errors).sort(), ['dateOfBirth', 'phone'])
  assert.equal(validateRegistration({ ...draft, phone: '0532 000 00 00' }, false).payload.phone, relative.phone)
})

test('a role without verified patient permissions cannot reach the official patient methods even with a valid session', async () => {
  const session = createAuthSession()
  session.update({ ...auth(), role: 'Nurse' })
  const guarded = permission => createAuthorizedRequest(session, permission, async () => assert.fail('An unauthorized call reached transport'))
  await assert.rejects(createHttpRegistrationService(guarded('registerPatients')).register(payload, token), { code: 'forbidden' })
  await assert.rejects(createHttpPatientSearchService(guarded('searchPatients')).search({ mode: 'name', value: 'Synthetic' }, token), { code: 'forbidden' })
  await assert.rejects(createHttpPatientProfileService(guarded('editPatients')).load(id, token), { code: 'forbidden' })
})
test('the minimum supported birth date is inclusive in both patient forms', () => {
  // SQL tarih sınırı, aynı sınır gününü kayıt ve düzenleme için kabul etmelidir.
  for (const [dateOfBirth, expected] of [['1899-12-31', false], ['1900-01-01', true]]) {
    assert.equal('payload' in validateRegistration({ ...draft, dateOfBirth }, false), expected)
    assert.equal('payload' in validateProfile({ ...draftFromPatient(patient), dateOfBirth }, false), expected)
  }
})

test('Administrator reaches the patient transport and a backend 403 remains authoritative', async () => {
  // Yerel rota izni, sunucunun erişimi reddetmesini engellemez.
  const session = createAuthSession()
  session.update({ ...auth(), role: 'Administrator' })
  let called = false
  const transport = createAuthorizedRequest(session, 'searchPatients', async (_url, options) => {
    called = true
    assert.equal(new Headers(options.headers).get('Authorization'), 'Bearer ' + token)
    return problem(403, 'ACCESS_DENIED')
  })
  await assert.rejects(createHttpPatientSearchService(transport).search({ mode: 'name', value: 'Synthetic' }, token), { code: 'forbidden' })
  assert.equal(called, true)
  assert.equal(session.getSnapshot().session.role, 'Administrator')
})
