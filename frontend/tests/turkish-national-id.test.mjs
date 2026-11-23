import test from 'node:test'
import assert from 'node:assert/strict'
import { visibleFieldErrors } from '../src/patients/fieldFeedback.ts'
import { createHttpRegistrationService } from '../src/patients/httpRegistrationService.ts'
import { createMockPatientStore } from '../src/patients/mockPatientStore.ts'
import { draftFromPatient, validateProfile } from '../src/patients/profile/model.ts'
import { finishRegistration } from '../src/patients/registrationAttempt.ts'
import { validatePatientQuery } from '../src/patients/search/model.ts'
import { emptyRegistration, isPatient, validateRegistration, validateTurkishNationalId } from '../src/patients/validation.ts'
import { syntheticTurkishNationalId } from './fixtures/turkishNationalId.mjs'

const valid = syntheticTurkishNationalId('100000061')
const draft = {
  ...emptyRegistration, firstName: 'Synthetic', lastName: 'Checksum', nationalId: valid,
  dateOfBirth: '2000-02-29', gender: 'Male', phone: '05000000000', address: 'Synthetic address', conditionLevel: 'MILD',
}

test('check digits accept deterministic synthetic inputs and normalize a negative weighted sum', () => {
  // Negatif örnekte ağırlıklı toplam 7 - 36 = -29, son iki hane 1 ve 8 olur.
  const negativeWeight = syntheticTurkishNationalId('190909090')
  assert.equal(negativeWeight.slice(-2), '18')
  for (const nationalId of [valid, negativeWeight]) {
    assert.equal(validateTurkishNationalId(nationalId), undefined)
    assert.equal(validateRegistration({ ...draft, nationalId }, false).payload.nationalId, nationalId)
  }
})

test('national ID rejects wrong lengths, letters, whitespace, signs and non-ASCII numerals', () => {
  for (const value of [
    '', valid.slice(1), valid + '0', 'A' + valid.slice(1), valid.slice(0, 5) + ' ' + valid.slice(6),
    ' ' + valid, valid + ' ', valid + '\n', '+' + valid.slice(1), '-' + valid.slice(1), '١' + valid.slice(1),
  ]) assert.match(validateTurkishNationalId(value), /exactly 11 digits/)
})

test('the first digit, tenth check digit and eleventh check digit each have a specific error', () => {
  assert.match(validateTurkishNationalId('0' + valid.slice(1)), /first digit.*zero/)
  const differentTenth = (Number(valid[9]) + 1) % 10
  assert.match(validateTurkishNationalId(valid.slice(0, 9) + differentTenth + valid[10]), /10th digit/)
  const differentEleventh = (Number(valid[10]) + 1) % 10
  assert.match(validateTurkishNationalId(valid.slice(0, 10) + differentEleventh), /11th digit/)
})

test('blur exposes a checksum error, correction clears it and no request is needed', () => {
  const invalid = validateRegistration({ ...draft, nationalId: '0' + valid.slice(1) }, false)
  const touched = new Set(['nationalId'])
  assert.deepEqual(visibleFieldErrors(invalid.errors, {}, new Set(), false), {})
  assert.match(visibleFieldErrors(invalid.errors, {}, touched, false).nationalId, /first digit/)
  const corrected = validateRegistration(draft, false)
  assert.ok(corrected.payload)
  assert.deepEqual(visibleFieldErrors(corrected.errors ?? {}, {}, touched, false), {})
})

test('a bad checksum never reaches POST and duplicate 409 still preserves a valid registration draft', async () => {
  const calls = []
  const service = createHttpRegistrationService(async (url, init) => {
    calls.push([url, init.method, JSON.parse(init.body).nationalId])
    return new Response(null, { status: 409 })
  })
  const entry = { draft, includeRelative: false, registered: null }
  const signal = new AbortController().signal
  const { payload } = validateRegistration(draft, false)
  const invalidPayload = { ...payload, nationalId: valid.slice(0, 10) + ((Number(valid[10]) + 1) % 10) }
  const rejected = await finishRegistration(service, entry, invalidPayload, 'synthetic-token', signal)
  assert.match(rejected.error.fieldErrors.nationalId, /11th digit/)
  assert.deepEqual(calls, [])

  const duplicate = await finishRegistration(service, entry, payload, 'synthetic-token', signal)
  assert.equal(duplicate.entry, entry)
  assert.equal(duplicate.error.code, 'duplicate')
  assert.match(duplicate.error.message, /already registered/)
  assert.deepEqual(calls, [['/api/v1/patients', 'POST', valid]])
})

test('legacy synthetic IDs still load, stay immutable and can be found by exact search', () => {
  const legacy = createMockPatientStore().list()[0]
  assert.match(validateTurkishNationalId(legacy.nationalId), /first digit/)
  assert.ok(isPatient(legacy))
  assert.equal(validatePatientQuery('nationalId', legacy.nationalId).query.value, legacy.nationalId)
  const { payload } = validateProfile({ ...draftFromPatient(legacy), lastName: 'Updated' }, false)
  assert.equal(payload.lastName, 'Updated')
  assert.equal(Object.hasOwn(payload, 'nationalId'), false)
})
