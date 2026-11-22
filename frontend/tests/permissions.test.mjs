import assert from 'node:assert/strict'
import test from 'node:test'
import { createAuthorizedRequest } from '../src/auth/authorizedRequest.ts'
import { hasPermission } from '../src/auth/permissions.ts'
import { canAccessRoute, homePath, listWorkflowRoutes, resolvePath } from '../src/auth/routing.ts'
import { createAuthSession } from '../src/auth/session.ts'
import { roles } from '../src/auth/types.ts'
import { createAppHistory } from '../src/navigation/history.ts'
import { createHttpPatientSearchService } from '../src/patients/search/httpSearchService.ts'
import { createHttpStaffService } from '../src/staff/httpStaffService.ts'

const doctor = '/doctors'
const register = '/patients/register'
const search = '/patients/search'
const adminRoutes = ['/admin/audit-logs', '/admin/staff']
const workflows = [doctor, register, search, ...adminRoutes]
const permissions = ['viewDoctors', 'registerPatients', 'searchPatients', 'viewAuditLogs', 'manageDemoStaff']
const demo = role => ({ role, tokenType: 'Bearer', accessToken: 'synthetic-test-token', expiresAt: new Date(Date.now() + 60_000).toISOString() })

// Beklentiler rota kataloğundan türetilmez. İstemeden genişleyen izinler bu testte görünür.
for (const role of roles) {
  test(role + ' only sees and opens documented workflows and its own home', () => {
    const expected = { Receptionist: [doctor, register, search], Administrator: [register, search, ...adminRoutes] }[role] ?? []
    assert.deepEqual(listWorkflowRoutes(role).map(route => route.path), expected)
    for (const [index, path] of workflows.entries()) {
      const allowed = expected.includes(path)
      assert.equal(hasPermission(role, permissions[index]), allowed)
      assert.equal(canAccessRoute(path, role), allowed)
      assert.equal(resolvePath(path, role), allowed ? path : homePath(role))
    }
    for (const otherRole of roles) {
      assert.equal(canAccessRoute(homePath(otherRole), role), otherRole === role)
    }
    assert.equal(resolvePath('/admin', role), homePath(role))
    assert.equal(resolvePath('/unknown', role), homePath(role))
    assert.equal(resolvePath('/foundation', role), '/foundation')
  })
}

test('unknown roles and permissions, and direct links before login, default to denial', () => {
  for (const role of [null, 'Unknown']) {
    assert.deepEqual(listWorkflowRoutes(role), [])
    for (const path of [...workflows, homePath('Receptionist')]) {
      assert.equal(canAccessRoute(path, role), false)
      assert.equal(resolvePath(path, role), '/login')
    }
  }
  assert.equal(hasPermission('Administrator', 'manageAccounts'), false)
  assert.equal(hasPermission('Receptionist', 'toString'), false)
})

// Basit geçmiş taklidi, eski korumalı kayıtlar geri geldiğinde gerçek yönlendirmeyi sınar.
test('Back, Forward, Home and reload re-check the current session', () => {
  let index = 0
  const entries = [{ path: doctor, state: undefined }]
  const browser = {
    read: () => entries[index],
    replace: (path, state) => { entries[index] = { path, state } },
    push: (path, state) => { entries.splice(++index, Infinity, { path, state }) },
    go: delta => { index += delta },
  }
  const nav = createAppHistory(browser)
  const role = 'Receptionist'
  assert.equal(nav.sync(null).path, '/login')
  assert.equal(nav.sync(role).path, homePath(role))
  nav.navigate(register)
  nav.navigate(search)
  assert.equal(nav.navigate(homePath(role)).path, homePath(role))
  assert.equal(nav.move(-1).path, homePath(role))
  assert.equal(nav.sync(role).path, search)
  nav.move(1)
  assert.equal(nav.sync(role).path, homePath(role))

  // Yeni belge belirteç devralmaz. Yerel çıkış sonrasında eski geçmiş de erişim vermez.
  nav.move(-1)
  assert.equal(nav.sync(role).path, search)
  assert.equal(createAppHistory(browser).sync(null).path, '/login')
  const logout = nav.sync(null)
  assert.deepEqual(logout, { path: '/login', canGoBack: false, canGoForward: false })
  browser.go(-1)
  assert.equal(nav.sync(null).path, '/login')
  browser.go(1)
  assert.equal(nav.sync(null).path, '/login')
})

test('a protected transport blocks missing sessions, other roles and stale Bearer values locally', async () => {
  const session = createAuthSession()
  let calls = 0
  const request = createAuthorizedRequest(session, 'searchPatients', async () => {
    calls++
    return new Response(null, { status: 200 })
  })
  assert.equal((await request('/api/v1/patients')).status, 401)
  for (const role of roles.filter(role => role !== 'Receptionist' && role !== 'Administrator')) {
    session.update(demo(role))
    assert.equal((await request('/api/v1/patients')).status, 403)
  }
  session.update(demo('Receptionist'))
  assert.equal((await request('/api/v1/patients', { headers: { Authorization: 'Bearer previous-demo' } })).status, 401)
  assert.equal(calls, 0)
  assert.equal(session.getSnapshot().session.role, 'Receptionist')
})

test('a current 401 expires the session while a 403 keeps the session and uses safe feedback', async () => {
  const session = createAuthSession()
  session.update(demo('Receptionist'))
  const notices = []
  const unsubscribe = session.subscribe(() => notices.push(session.getSnapshot().notice))
  const headers = []
  const responses = [403, 401]
  const request = createAuthorizedRequest(session, 'searchPatients', async (_url, init) => {
    headers.push(init.headers.get('Authorization'))
    return new Response('synthetic server details', { status: responses.shift() })
  })
  const client = createHttpPatientSearchService(request)
  const query = { mode: 'name', value: 'Demo' }
  await assert.rejects(client.search(query, session.getAccessToken()), { code: 'forbidden' })
  assert.equal(session.getSnapshot().session.role, 'Receptionist')
  assert.deepEqual(notices, [])
  await assert.rejects(client.search(query, session.getAccessToken()), { code: 'unauthorized' })
  assert.equal(session.getAccessToken(), null)
  assert.deepEqual(notices, ['Your session has expired. Sign in again.'])
  assert.deepEqual(headers, ['Bearer synthetic-test-token', 'Bearer synthetic-test-token'])
  unsubscribe()
  session.update(demo('Receptionist'))
  assert.equal(notices.length, 1)
})

test('a stale or canceled 401 cannot expire a later active session', async () => {
  const session = createAuthSession()
  const staff = demo('Receptionist')
  session.update(staff)
  let respond
  const request = createAuthorizedRequest(session, 'viewDoctors', () => new Promise(resolve => { respond = resolve }))
  const stale = request('/api/v1/staff')
  session.update(null)
  session.update(staff)
  respond(new Response(null, { status: 401 }))
  await stale
  assert.equal(session.getSnapshot().session, staff)

  const controller = new AbortController()
  const canceled = request('/api/v1/staff', { signal: controller.signal })
  controller.abort()
  respond(new Response(null, { status: 401 }))
  await canceled
  assert.equal(session.getSnapshot().session, staff)
})

test('directory receives Bearer through the protected transport and maps HTTP access errors', async () => {
  const session = createAuthSession()
  session.update(demo('Receptionist'))
  let status = 403
  const request = createAuthorizedRequest(session, 'viewDoctors', async (url, init) => {
    assert.equal(url, '/api/v1/staff?department=Cardiology')
    assert.equal(init.headers.get('Authorization'), 'Bearer synthetic-test-token')
    return new Response(null, { status })
  })
  const client = createHttpStaffService(request)
  await assert.rejects(client.listClinicians('Cardiology'), { code: 'access' })
  assert.equal(session.getSnapshot().session.role, 'Receptionist')
  status = 401
  await assert.rejects(client.listClinicians('Cardiology'), { code: 'access' })
  assert.equal(session.getSnapshot().session, null)
})