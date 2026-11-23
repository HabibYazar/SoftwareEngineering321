import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import react from '@vitejs/plugin-react'
import { createServer } from 'vite'
import { emptyRegistration } from '../src/patients/validation.ts'
import { syntheticTurkishNationalId } from './fixtures/turkishNationalId.mjs'

// TSX bileşenleri kurulu Vite dönüştürücüsüyle yüklenir. HTTP dinleyicisi, proxy ve HMR açılmaz.
const vite = await createServer({
  configFile: false, envFile: false, plugins: [react()],
  server: { middlewareMode: true, hmr: false, ws: false, watch: null },
  optimizeDeps: { noDiscovery: true, include: [] },
})
after(() => vite.close())

test('patient selectors and invalid controls retain associated labels and descriptions', async () => {
  const { PatientDetailFields } = await vite.ssrLoadModule('/src/patients/PatientDetailFields.tsx')
  const markup = renderToStaticMarkup(createElement(PatientDetailFields, {
    prefix: 'profile', draft: { ...emptyRegistration, gender: 'Historical value' },
    errors: { phone: 'Invalid synthetic number' }, disabled: false, onBlur() {}, onChange() {},
  }))
  assert.match(markup, /<label for="profile-gender">Gender<\/label>/)
  assert.match(markup, /<select id="profile-gender"/)
  assert.match(markup, /<option\b(?=[^>]*\bdisabled="")(?=[^>]*\bselected="")(?=[^>]*\bvalue="Historical value")[^>]*>Current value: Historical value \(choose a supported option\)/)
  assert.match(markup, /aria-invalid="true" aria-describedby="profile-phone-hint profile-phone-error"/)
  assert.match(markup, /id="profile-phone-error"/)
  assert.match(markup, /inputMode="tel"/i)
})

test('registration and profile render the same four English gender options', async () => {
  const { PatientDetailFields } = await vite.ssrLoadModule('/src/patients/PatientDetailFields.tsx')
  for (const [prefix, gender] of [['registration', ''], ['profile', 'Female']]) {
    const markup = renderToStaticMarkup(createElement(PatientDetailFields, {
      prefix, draft: { ...emptyRegistration, gender }, errors: {},
      disabled: false, onBlur() {}, onChange() {},
    }))
    const select = markup.match(new RegExp('<select id="' + prefix + '-gender"[^>]*>(.*?)</select>'))
    assert.ok(select)
    const choices = Array.from(select[1].matchAll(/<option\b([^>]*)>([^<]*)<\/option>/g), ([, attrs, label]) => ({
      value: attrs.match(/\bvalue="([^"]*)"/)?.[1], label, selected: attrs.includes('selected=""'),
    })).filter(option => option.value !== '')
    assert.deepEqual(choices, [
      { value: 'Male', label: 'Male', selected: false },
      { value: 'Female', label: 'Female', selected: gender === 'Female' },
    ])
  }
})

test('registration success links directly to the returned UUID and excludes private detail fields', async () => {
  const { RegistrationSuccess } = await vite.ssrLoadModule('/src/patients/RegistrationSuccess.tsx')
  const id = '00000000-0000-4000-8000-0000000000bc'
  const patient = { ...emptyRegistration, id, firstName: 'Synthetic', lastName: 'New', conditionLevel: 'MILD',
    nationalId: syntheticTurkishNationalId('100000022'), phone: '+905000000000', address: 'Synthetic private address' }
  const markup = renderToStaticMarkup(createElement(RegistrationSuccess, { patient, canEdit: true, onRegisterAnother() {} }))
  assert.match(markup, new RegExp('href="#/patients/edit/' + id + '"'))
  assert.match(markup, /href="#\/patients\/search"/)
  assert.match(markup, /Register Another Patient/)
  assert.match(markup, /Synthetic New/)
  assert.ok(!markup.includes(patient.nationalId))
  assert.doesNotMatch(markup, /Synthetic private address|5000000000/)
  const denied = renderToStaticMarkup(createElement(RegistrationSuccess, { patient, canEdit: false, onRegisterAnother() {} }))
  assert.doesNotMatch(denied, /Edit Profile/)
})
