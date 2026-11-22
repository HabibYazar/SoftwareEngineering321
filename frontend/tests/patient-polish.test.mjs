import test from 'node:test'
import assert from 'node:assert/strict'
import { visibleFieldErrors } from '../src/patients/fieldFeedback.ts'
import { displayStoredTurkishMobile, editTurkishMobile, genderOptions, normalizeTurkishMobile } from '../src/patients/formOptions.ts'
import { createHttpRegistrationService } from '../src/patients/httpRegistrationService.ts'
import { createMockPatientStore } from '../src/patients/mockPatientStore.ts'
import { createHttpPatientProfileService } from '../src/patients/profile/httpProfileService.ts'
import { draftFromPatient, validateProfile } from '../src/patients/profile/model.ts'
import { finishRegistration } from '../src/patients/registrationAttempt.ts'
import { RegistrationError } from '../src/patients/types.ts'
import { appendRecentPatient, updateRecentPatient } from '../src/patients/recentPatients.ts'
import { createHttpPatientSearchService } from '../src/patients/search/httpSearchService.ts'
import { createMockPatientSearchService } from '../src/patients/search/mockSearchService.ts'
import { emptyRegistration, validateRegistration } from '../src/patients/validation.ts'
import { syntheticTurkishNationalId } from './fixtures/turkishNationalId.mjs'

const draft = {
  ...emptyRegistration, firstName: 'Synthetic', lastName: 'Preview', nationalId: syntheticTurkishNationalId('100000061'),
  dateOfBirth: '2000-02-29', gender: 'Male', phone: '05000000000', address: 'Synthetic address', conditionLevel: 'MILD',
}

test('blur reveals the touched error, corrections clear it, and submission reveals all errors', () => {
  const incomplete = { nationalId: 'Bad ID', phone: 'Bad phone' }
  assert.deepEqual(visibleFieldErrors(incomplete, {}, new Set(), false), {})
  assert.deepEqual(visibleFieldErrors(incomplete, {}, new Set(['nationalId']), false), { nationalId: 'Bad ID' })
  assert.deepEqual(visibleFieldErrors({ phone: 'Bad phone' }, {}, new Set(['nationalId']), false), {})
  assert.deepEqual(visibleFieldErrors(incomplete, {}, new Set(), true), incomplete)
  assert.deepEqual(visibleFieldErrors({}, { nationalId: 'Duplicate' }, new Set(), false), { nationalId: 'Duplicate' })
})

test('registration and profile accept only the shared supported gender choices', () => {
  const patient = createMockPatientStore().list()[0]
  for (const option of genderOptions) {
    assert.equal(validateRegistration({ ...draft, gender: option.value }, false).payload.gender, option.value)
    const profile = draftFromPatient({ ...patient, gender: option.value })
    assert.equal(validateProfile({ ...profile, address: 'Updated synthetic address' }, false).payload.gender, option.value)
  }
  assert.match(validateRegistration({ ...draft, gender: 'Mechanic' }, false).errors.gender, /supported/)
  const loaded = { ...createMockPatientStore().list()[0], gender: 'Historical value' }
  const editing = draftFromPatient(loaded)
  assert.equal(editing.gender, 'Historical value')
  assert.match(validateProfile(editing, false).errors.gender, /supported/)
  assert.equal(validateProfile({ ...editing, gender: 'Other' }, false).payload.gender, 'Other')
})

const advanced = {
  ...draft, conditionLevel: 'ADVANCED', relativeFullName: 'Synthetic Contact', relativeRelationship: 'Sibling',
}

test('registration and profile apply the same Turkish mobile rules to both phone fields', () => {
  const accepted = ['05000000000', '0500 000 00 00']
  const rejected = [
    '4000000000', '5000000000', '+905000000000', '1506 434 26 32', '0500000000', '050000000000', '+904000000000',
    '+495000000000', '500A000000', '+90 (500 000-00-00',
    '5' + '0'.repeat(30), '5' + ' '.repeat(33) + '000000000',
  ]

  // Aynı sentetik örnekler hem hasta hem yakın alanında ve her iki formda kullanılır.
  for (const phone of accepted) {
    assert.equal(normalizeTurkishMobile(phone), '+905000000000', phone)
    for (const validate of [validateRegistration, validateProfile]) {
      const { payload } = validate({ ...advanced, phone, relativePhone: phone }, false)
      assert.equal(payload.phone, '+905000000000', phone)
      assert.equal(payload.relativeInformation.phone, '+905000000000', phone)
    }
  }
  for (const phone of rejected) {
    assert.equal(normalizeTurkishMobile(phone), null, phone)
    for (const validate of [validateRegistration, validateProfile]) {
      assert.ok(validate({ ...advanced, phone, relativePhone: accepted[0] }, false).errors.phone, phone)
      assert.ok(validate({ ...advanced, phone: accepted[0], relativePhone: phone }, false).errors.relativePhone, phone)
    }
  }
  assert.equal(validateRegistration(draft, false).payload.relativeInformation, undefined)
  assert.equal(validateProfile(draft, false).payload.relativeInformation, null)
})

test('POST and PUT serialize normalized patient and relative phones with the existing ID contract', async () => {
  const id = '00000000-0000-4000-8000-000000000061'
  const requests = []
  const transport = async (url, { method, body }) => {
    const sent = JSON.parse(body)
    requests.push({ url, method, sent })
    return Response.json({ ...sent, id, nationalId: draft.nationalId }, { status: method === 'POST' ? 201 : 200 })
  }
  const { payload: registration } = validateRegistration({ ...advanced, relativePhone: '0500 000 00 01' }, false)
  const registered = await createHttpRegistrationService(transport).register(registration, 'synthetic-test')
  const { payload: profile } = validateProfile({
    ...draftFromPatient(registered), phone: '05000000000', relativePhone: '05000000001',
  }, true)
  await createHttpPatientProfileService(transport).update(id, profile, 'synthetic-test')

  assert.deepEqual(requests.map(({ url, method }) => [method, url]), [
    ['POST', '/api/v1/patients'], ['PUT', '/api/v1/patients/' + id],
  ])
  for (const { sent } of requests) {
    assert.equal(sent.phone, '+905000000000')
    assert.equal(sent.relativeInformation.phone, '+905000000001')
    assert.equal(sent.gender, registered.gender)
  }
  // Yeni POST kimliği sağlama kurallarına uyar. Profil PUT isteği değişmez kimliği taşımaz.
  assert.equal(requests[0].sent.nationalId, draft.nationalId)
  assert.equal(Object.hasOwn(requests[1].sent, 'nationalId'), false)
})

test('phone typing adds spaces and keeps the caret at the edited digit', () => {
  assert.deepEqual(editTurkishMobile('0506', '05064', 5), { value: '0506 4', caret: 6 })
  assert.deepEqual(editTurkishMobile('0506 434 26 32', '0506 494 26 32', 7), { value: '0506 494 26 32', caret: 7 })
  assert.deepEqual(editTurkishMobile('0506 434', '0506434', 4, 'deleteContentBackward'), { value: '0504 34', caret: 3 })
  assert.deepEqual(editTurkishMobile('0506 434', '0506434', 4, 'deleteContentForward'), { value: '0506 34', caret: 4 })
})

test('local phone paste replaces the selection and cannot silently truncate extra digits', () => {
  assert.deepEqual(editTurkishMobile('', '05064342632', 11), { value: '0506 434 26 32', caret: 14 })
  assert.deepEqual(editTurkishMobile('0506 434 26 32', '0506 999 26 32', 8), { value: '0506 999 26 32', caret: 8 })
  assert.match(editTurkishMobile('0506 434 26 32', '050643426321', 12).error, /at most 11/)
  assert.match(editTurkishMobile('', '0506434263x', 11).error, /starting with 05/)
})

test('stored patient and relative phones open locally without rewriting unrelated legacy numbers', () => {
  const patient = { ...createMockPatientStore().list()[0], phone: '+905064342632',
    relativeInformation: { fullName: 'Synthetic Contact', relationship: 'Sibling', phone: '+905321234567' } }
  const profile = draftFromPatient(patient)
  assert.equal(profile.phone, '0506 434 26 32')
  assert.equal(profile.relativePhone, '0532 123 45 67')
  assert.equal(validateProfile(profile, true).payload.relativeInformation.phone, '+905321234567')
  assert.equal(displayStoredTurkishMobile('0212 000 00 00'), '0212 000 00 00')
  assert.equal(displayStoredTurkishMobile('+44 7700 900000'), '+44 7700 900000')
})

test('touched phone errors clear when the local number is corrected', () => {
  const errors = validateRegistration({ ...draft, phone: '0506 434 26' }, false).errors
  assert.deepEqual(visibleFieldErrors(errors, {}, new Set(), false), {})
  assert.match(visibleFieldErrors(errors, {}, new Set(['phone']), false).phone, /11 digits/)
  const corrected = validateRegistration({ ...draft, phone: '0506 434 26 32' }, false)
  assert.deepEqual(visibleFieldErrors(corrected.errors ?? {}, {}, new Set(['phone']), false), {})
})

test('invalid national IDs and invalid calendar boundaries remain field-level errors', () => {
  for (const nationalId of ['123', 'a0000000061']) assert.ok(validateRegistration({ ...draft, nationalId }, false).errors.nationalId)
  for (const dateOfBirth of ['1899-12-31', '2100-02-29', '9999-12-31']) {
    assert.ok(validateRegistration({ ...draft, dateOfBirth }, false).errors.dateOfBirth)
  }
  assert.equal(validateRegistration({ ...draft, dateOfBirth: '1900-01-01' }, false).payload.dateOfBirth, '1900-01-01')
  const now = new Date()
  const today = [now.getFullYear(), now.getMonth() + 1, now.getDate()].map(n => String(n).padStart(2, '0')).join('-')
  assert.equal(validateRegistration({ ...draft, dateOfBirth: today }, false).payload.dateOfBirth, today)
})

test('a registered mock patient appears immediately and the recent summary stores no ID number or contact data', () => {
  const store = createMockPatientStore()
  const patient = store.register(validateRegistration(draft, false).payload)
  const demo = createMockPatientSearchService(store, 0)
  assert.ok(demo.listDemoPatients().some(record => record.id === patient.id))
  const recent = appendRecentPatient([], patient)
  assert.deepEqual(Object.keys(recent[0]).sort(), ['conditionLevel', 'firstName', 'id', 'lastName'])
  assert.deepEqual(appendRecentPatient(recent, patient), recent)
  assert.equal(updateRecentPatient(recent, { ...patient, lastName: 'Edited' })[0].lastName, 'Edited')
  assert.deepEqual(updateRecentPatient([], patient), [])
})

test('a rejected registration retains the entire draft and only confirmed registration resets it', async () => {
  const entry = { draft, includeRelative: false, registered: null }
  const payload = validateRegistration(draft, false).payload
  const controller = new AbortController()
  const failed = await finishRegistration({ async register() { throw new RegistrationError('duplicate') } },
    entry, payload, 'synthetic-test', controller.signal)
  assert.equal(failed.entry, entry)
  assert.equal(failed.error.code, 'duplicate')
  const confirmed = { ...payload, id: '00000000-0000-4000-8000-000000000066' }
  const successful = await finishRegistration({ async register() { return confirmed } },
    entry, payload, 'synthetic-test', controller.signal)
  assert.equal(successful.patient, confirmed)
  assert.deepEqual(successful.entry, { draft: emptyRegistration, includeRelative: false, registered: confirmed })
  controller.abort()
  assert.equal(await finishRegistration({ async register() { return confirmed } },
    entry, payload, 'synthetic-test', controller.signal), null)
})

test('real patient search exposes no unfiltered listing method or request', async () => {
  const calls = []
  const http = createHttpPatientSearchService(async url => { calls.push(url); return Response.json([]) })
  assert.equal(http.listDemoPatients, undefined)
  assert.deepEqual(calls, [])
  await http.search({ mode: 'name', value: 'Synthetic' }, 'synthetic-test')
  assert.deepEqual(calls, ['/api/v1/patients?name=Synthetic'])
})
