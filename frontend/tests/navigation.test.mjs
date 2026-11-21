import assert from 'node:assert/strict'
import test from 'node:test'
import { createAppHistory } from '../src/navigation/history.ts'
import { homePath } from '../src/auth/routing.ts'
import { initializeTheme, saveThemePreference } from '../src/theme/preference.ts'

// Sahte tarayıcı, asenkron go isteğini ve ileri geçmişin push sırasında kesilmesini taklit eder.
function makeBrowser(initialPath = '/login') {
  const entries = [{ path: initialPath, state: null }]
  const queued = []
  let cursor = 0

  return {
    entries,
    queued,
    read: () => entries[cursor],
    replace: (path, state) => { entries[cursor] = { path, state } },
    push: (path, state) => { entries.splice(++cursor, Infinity, { path, state }) },
    go: direction => queued.push(direction),
    nativeGo: direction => { cursor += direction },
    flush() { this.nativeGo(queued.shift()) },
  }
}

test('app Back and Forward use adjacent browser entries without adding history', () => {
  const browser = makeBrowser(homePath('Receptionist'))
  const history = createAppHistory(browser)
  history.sync('Receptionist')
  history.navigate('/doctors')
  history.navigate('/foundation')

  assert.deepEqual(history.move(-1), { path: '/foundation', canGoBack: false, canGoForward: false })
  history.move(-1)
  assert.deepEqual(browser.queued, [-1])
  browser.flush()
  assert.deepEqual(history.sync('Receptionist'), { path: '/doctors', canGoBack: true, canGoForward: true })
  history.move(1)
  browser.flush()
  assert.deepEqual(history.sync('Receptionist'), { path: '/foundation', canGoBack: true, canGoForward: false })
  assert.equal(browser.entries.length, 3)
})

test('native Back and Forward synchronize app controls and a new link prunes forward history', () => {
  const browser = makeBrowser(homePath('Receptionist'))
  const history = createAppHistory(browser)
  history.sync('Receptionist')
  history.navigate('/doctors')
  history.navigate('/foundation')

  browser.nativeGo(-1)
  assert.equal(history.sync('Receptionist').canGoForward, true)
  browser.nativeGo(1)
  assert.equal(history.sync('Receptionist').path, '/foundation')
  browser.nativeGo(-1)
  history.sync('Receptionist')

  const home = history.navigate(homePath('Receptionist'))
  assert.deepEqual(home, { path: '/home/Receptionist', canGoBack: true, canGoForward: false })
  assert.deepEqual(browser.entries.map(entry => entry.path), ['/home/Receptionist', '/doctors', '/home/Receptionist'])
  history.navigate(home.path)
  assert.equal(browser.entries.length, 3)
})

test('unknown fragment entries and external URLs never send app controls outside owned history', () => {
  const browser = makeBrowser()
  browser.push('/foundation', null)
  const history = createAppHistory(browser)
  assert.deepEqual(history.sync(null), { path: '/foundation', canGoBack: false, canGoForward: false })
  history.move(-1)
  history.navigate('https://example.invalid/')
  assert.deepEqual(browser.queued, [])
  assert.equal(browser.read().path, '/foundation')

  history.navigate('/login')
  browser.push('/foundation', null)
  assert.deepEqual(history.sync(null), { path: '/foundation', canGoBack: false, canGoForward: false })
  history.move(-1)
  assert.deepEqual(browser.queued, [])
})

test('sign-in lands on the active role home and unauthorized internal links do not add entries', () => {
  const browser = makeBrowser('/doctors')
  const history = createAppHistory(browser)
  assert.equal(history.sync(null).path, '/login')
  assert.equal(history.sync('Physician').path, '/home/Physician')
  history.navigate('/doctors')
  history.navigate(homePath('Receptionist'))
  assert.equal(browser.entries.length, 1)
  assert.equal(browser.read().path, '/home/Physician')
  assert.deepEqual(Object.keys(browser.read().state).sort(), ['hmsScope', 'index'])
})

test('after logout native history never brings an authenticated route back', () => {
  const browser = makeBrowser(homePath('Receptionist'))
  const history = createAppHistory(browser)
  history.sync('Receptionist')
  history.navigate('/doctors')
  history.navigate('/foundation')
  assert.deepEqual(history.sync(null), { path: '/login', canGoBack: false, canGoForward: false })

  browser.nativeGo(-1)
  assert.deepEqual(history.sync(null), { path: '/login', canGoBack: false, canGoForward: false })
  assert.equal(browser.read().path, '/login')
  browser.nativeGo(1)
  assert.equal(history.sync(null).path, '/login')
})

test('a fresh document rechecks an old protected history entry with an empty session', () => {
  const browser = makeBrowser(homePath('Receptionist'))
  const previousDocument = createAppHistory(browser)
  previousDocument.sync('Receptionist')
  previousDocument.navigate('/doctors')
  assert.deepEqual(createAppHistory(browser).sync(null), {
    path: '/login', canGoBack: false, canGoForward: false,
  })
})

test('navigating and booting a fresh page preserve the saved theme without adding auth data to storage', () => {
  const storage = new Map()
  const previousWindow = globalThis.window
  const previousDocument = globalThis.document
  globalThis.window = {
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) },
    matchMedia: () => ({ matches: false }),
  }
  globalThis.document = { documentElement: { dataset: {} } }
  try {
    initializeTheme()
    assert.equal(document.documentElement.dataset.theme, 'light')
    saveThemePreference('dark')
    const browser = makeBrowser(homePath('Receptionist'))
    const history = createAppHistory(browser)
    history.sync('Receptionist')
    history.navigate('/doctors')
    history.sync(null)
    document.documentElement.dataset = {}
    initializeTheme()
    assert.equal(document.documentElement.dataset.theme, 'dark')
    assert.deepEqual([...storage], [['hms.theme', 'dark']])
  } finally {
    globalThis.window = previousWindow
    globalThis.document = previousDocument
  }
})
