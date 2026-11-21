import assert from 'node:assert/strict'
import test from 'node:test'
import { canRegisterPatients, homePath, registrationPath, resolvePath } from '../src/auth/routing.ts'
import { roles } from '../src/auth/types.ts'
import { createAppHistory } from '../src/navigation/history.ts'
import { createHttpRegistrationService } from '../src/patients/httpRegistrationService.ts'
import { createMockRegistrationService } from '../src/patients/mockRegistrationService.ts'
import { emptyRegistration, isPatient, validateRegistration } from '../src/patients/validation.ts'

// Kimlikler ve alanlar yalnızca sentetik test verisidir.
const validDraft = {
  ...emptyRegistration, firstName: 'Demo', lastName: 'Example', nationalId: '00000000042',
  dateOfBirth: '2000-02-29', gender: 'Not disclosed', phone: '0000000000', address: 'Synthetic address',
  conditionLevel: 'MILD',
}
const payload = validateRegistration(validDraft, false).payload
const responsePatient = { ...payload, id: '00000000-0000-4000-8000-000000000042' }

test('registration reports each missing required field', () => {
  assert.deepEqual(Object.keys(validateRegistration(emptyRegistration, false).errors).sort(), [
    'address', 'conditionLevel', 'dateOfBirth', 'firstName', 'gender', 'lastName', 'nationalId', 'phone',
  ])
})

test('national ID requires exactly 11 ASCII digits and keeps leading zeros', () => {
  for (const nationalId of ['12345', '000000000042', '00000000abc', '٠٠٠٠٠٠٠٠٠٤٢']) {
    assert.ok(validateRegistration({ ...validDraft, nationalId }, false).errors.nationalId)
  }
  assert.equal(payload.nationalId, '00000000042')
})

test('registration validates calendar dates, trimmed values and contract lengths', () => {
  assert.ok(validateRegistration({ ...validDraft, dateOfBirth: '2023-02-29' }, false).errors.dateOfBirth)
  assert.ok(validateRegistration({ ...validDraft, address: 'a'.repeat(501), firstName: '  ' }, false).errors.address)
  assert.ok(validateRegistration({ ...validDraft, firstName: '  ' }, false).errors.firstName)
  assert.equal(validateRegistration({ ...validDraft, firstName: ' Demo ' }, false).payload.firstName, 'Demo')
})

test('switching to Advanced makes all relative fields mandatory', () => {
  const advanced = { ...validDraft, conditionLevel: 'ADVANCED' }
  assert.deepEqual(Object.keys(validateRegistration(advanced, false).errors).sort(), [
    'relativeFullName', 'relativePhone', 'relativeRelationship',
  ])
  assert.deepEqual(validateRegistration({
    ...advanced, relativeFullName: 'Demo Contact', relativeRelationship: 'Sibling', relativePhone: '0000000001',
  }, false).payload.relativeInformation, { fullName: 'Demo Contact', relationship: 'Sibling', phone: '0000000001' })
})

test('Mild and Moderate omit optional contacts, or require all fields if one is submitted', () => {
  for (const conditionLevel of ['MILD', 'MODERATE']) {
    const draft = { ...validDraft, conditionLevel, relativeFullName: 'Demo Contact' }
    assert.equal(validateRegistration(draft, false).payload.relativeInformation, undefined)
    assert.ok(validateRegistration(draft, true).errors.relativePhone)
    const complete = { ...draft, relativeRelationship: 'Parent', relativePhone: '0000000001' }
    assert.equal(validateRegistration(complete, true).payload.relativeInformation.relationship, 'Parent')
  }
})

test('HTTP registration posts the exact JSON and Bearer token and accepts a valid 201', async () => {
  const calls = []
  const service = createHttpRegistrationService(async (url, options) => {
    calls.push([url, options])
    return Response.json(responsePatient, { status: 201 })
  })
  assert.deepEqual(await service.register(payload, 'contract-test-token'), responsePatient)
  assert.equal(calls.length, 1)
  const [url, options] = calls[0]
  assert.equal(url, '/api/v1/patients')
  assert.equal(options.method, 'POST')
  assert.equal(options.headers.Authorization, 'Bearer contract-test-token')
  assert.equal(options.headers['Content-Type'], 'application/json')
  assert.equal(options.credentials, 'omit')
  assert.deepEqual(JSON.parse(options.body), payload)
  await assert.rejects(service.register(payload, null), { code: 'unauthorized' })
  assert.equal(calls.length, 1)
})

test('HTTP registration maps documented errors and never exposes a server response body', async () => {
  for (const [status, code] of [[400, 'invalid'], [401, 'unauthorized'], [403, 'forbidden'], [409, 'duplicate'], [500, 'server']]) {
    const service = createHttpRegistrationService(async () => new Response('INTERNAL DETAILS', { status }))
    await assert.rejects(service.register(payload, 'test'), error => error.code === code && !error.message.includes('INTERNAL DETAILS'))
  }
})

test('network failures, cancellation and malformed 201 responses never report success', async () => {
  const offline = createHttpRegistrationService(async () => { throw new Error('Connection failed') })
  await assert.rejects(offline.register(payload, 'test'), { code: 'network' })
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(offline.register(payload, 'test', controller.signal), { name: 'AbortError' })
  const responses = [
    new Response('{', { status: 201 }),
    Response.json({ ...responsePatient, id: 'not-a-uuid' }, { status: 201 }),
    Response.json({ ...responsePatient, nationalId: '00000000043' }, { status: 201 }),
    Response.json({ ...responsePatient, conditionLevel: 'ADVANCED' }, { status: 201 }),
  ]
  for (const response of responses) {
    await assert.rejects(createHttpRegistrationService(async () => response).register(payload, 'test'), { code: 'response' })
  }
})

test('synthetic registration rejects the seeded ID and simultaneous duplicates', async () => {
  const service = createMockRegistrationService(0)
  await assert.rejects(service.register({ ...payload, nationalId: '00000000001' }, 'test'), { code: 'duplicate' })
  const results = await Promise.allSettled([service.register(payload, 'test'), service.register(payload, 'test')])
  assert.equal(results[0].status, 'fulfilled')
  assert.ok(isPatient(results[0].value))
  assert.equal(results[1].status, 'rejected')
  assert.equal(results[1].reason.code, 'duplicate')
})

test('Receptionist and Administrator can navigate directly to patient registration', () => {
  assert.equal(resolvePath(registrationPath, null), '/login')
  for (const role of roles) {
    const allowed = role === 'Receptionist' || role === 'Administrator'
    assert.equal(canRegisterPatients(role), allowed)
    assert.equal(resolvePath(registrationPath, role), allowed ? registrationPath : homePath(role))
  }
})

test('dirty registration can block Home and both app-history directions', () => {
  const browser = makeBrowser(homePath('Receptionist'))
  let accept = true
  const history = createAppHistory(browser, () => accept)
  history.sync('Receptionist')
  history.navigate(registrationPath)
  history.navigate('/doctors')
  browser.traverse(-1)
  history.sync('Receptionist')
  accept = false

  assert.equal(history.navigate(homePath('Receptionist')).path, registrationPath)
  assert.equal(history.move(-1).path, registrationPath)
  assert.equal(history.move(1).path, registrationPath)
  assert.deepEqual(browser.requested, [])
  accept = true
  history.move(1)
  browser.traverse(1)
  assert.equal(history.sync('Receptionist').path, '/doctors')
})

test('canceling native Back restores the dirty registration without a second prompt', () => {
  const browser = makeBrowser(homePath('Receptionist'))
  let prompts = 0
  let accept = true
  const history = createAppHistory(browser, () => { prompts++; return accept })
  history.sync('Receptionist')
  history.navigate(registrationPath)
  accept = false

  browser.traverse(-1)
  assert.equal(history.sync('Receptionist').path, registrationPath)
  assert.deepEqual(browser.requested, [1])
  history.sync('Receptionist')
  browser.traverse(1)
  assert.equal(history.sync('Receptionist').path, registrationPath)
  assert.equal(prompts, 2)

  accept = true
  history.move(-1)
  browser.traverse(-1)
  assert.equal(history.sync('Receptionist').path, homePath('Receptionist'))
  assert.equal(prompts, 3)
})

// Tarayıcı geçmişi mutasyonu ile popstate olayını ayırır; iptal edilmiş hareket ayrıca gözlenebilir.
function makeBrowser(path) {
  const entries = [{ path, state: null }]
  const requested = []
  let index = 0
  return {
    requested,
    read: () => entries[index],
    replace: (path, state) => { entries[index] = { path, state } },
    push: (path, state) => { entries.splice(++index, Infinity, { path, state }) },
    go: delta => requested.push(delta),
    traverse: delta => { index += delta },
  }
}
