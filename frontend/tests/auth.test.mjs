import test from 'node:test'
import assert from 'node:assert/strict'
import { createHttpAuthService } from '../src/auth/httpAuthService.ts'
import { createMockAuthService } from '../src/auth/mockAuthService.ts'
import { roles } from '../src/auth/types.ts'
import { resolvePath, homePath } from '../src/auth/routing.ts'

const credentials = { username: 'synthetic.demo', password: 'test-only' }
const session = { accessToken: 'test-only-token', tokenType: 'Bearer', role: 'Physician', expiresAt: new Date(Date.now() + 60_000).toISOString() }

test('HTTP login follows the contract and logout sends Bearer authorization', async () => {
  const calls = []
  const service = createHttpAuthService(async (url, options) => {
    calls.push({ url, options })
    return url.endsWith('/login') ? Response.json(session) : new Response(null, { status: 204 })
  })
  assert.deepEqual(await service.login(credentials), session)
  await service.logout(session.accessToken)
  assert.equal(calls[0].url, '/api/v1/auth/login')
  assert.equal(calls[0].options.method, 'POST')
  assert.deepEqual(JSON.parse(calls[0].options.body), credentials)
  assert.equal(calls[0].options.headers['Content-Type'], 'application/json')
  assert.equal(calls[0].options.cache, 'no-store')
  assert.equal(calls[0].options.credentials, 'omit')
  assert.equal(calls[1].url, '/api/v1/auth/logout')
  assert.equal(calls[1].options.headers.Authorization, `Bearer ${session.accessToken}`)
})

for (const [status, code] of [[400, 'request'], [401, 'invalid'], [403, 'forbidden'], [500, 'server']]) {
  test(`login handles HTTP ${status} without exposing server details`, async () => {
    const service = createHttpAuthService(async () => new Response('sensitive server detail', { status }))
    await assert.rejects(service.login(credentials), error => error.code === code && !error.message.includes('sensitive'))
  })
}

for (const data of [{ ...session, role: 'Unknown' }, { ...session, tokenType: 'Basic' }, { ...session, accessToken: '' }, {}]) {
  test(`rejects malformed login response: ${JSON.stringify(Object.keys(data))}`, async () => {
    const service = createHttpAuthService(async () => Response.json(data))
    await assert.rejects(service.login(credentials), { code: 'response' })
  })
}

test('invalid JSON is handled safely', async () => {
  const service = createHttpAuthService(async () => new Response('<html>not JSON</html>'))
  await assert.rejects(service.login(credentials), { code: 'response' })
})

test('network failure is safe for both login and logout', async () => {
  const service = createHttpAuthService(async () => { throw new Error('private transport details') })
  await assert.rejects(service.login(credentials), { code: 'network' })
  await assert.rejects(service.logout(session.accessToken), { code: 'network' })
})

test('logout accepts an already-invalid session and rejects server failure', async () => {
  await createHttpAuthService(async () => new Response(null, { status: 401 })).logout('expired')
  await assert.rejects(createHttpAuthService(async () => new Response(null, { status: 500 })).logout('demo'), { code: 'server' })
})

test('synthetic accounts cover all roles and can sign out', async () => {
  const service = createMockAuthService(0)
  const usernames = ['admin.demo', 'reception.demo', 'doctor.demo', 'nurse.demo', 'care.demo', 'lab.demo', 'cleaning.demo']
  for (const [index, username] of usernames.entries()) {
    const result = await service.login({ username, password: 'DemoOnly!321' })
    assert.equal(result.role, roles[index])
    assert.equal(result.tokenType, 'Bearer')
    await service.logout(result.accessToken)
  }
})

test('mock rejects invalid credentials and inherited object keys', async () => {
  const service = createMockAuthService(0)
  for (const [username, password] of [['admin.demo', 'wrong'], ['unknown', 'DemoOnly!321'], ['toString', 'DemoOnly!321']]) {
    await assert.rejects(service.login({ username, password }), { code: 'invalid' })
  }
})

test('route guard denies signed-out access and routes every role to its own home', () => {
  assert.equal(resolvePath('/foundation', null), '/foundation')
  for (const role of roles) {
    assert.equal(resolvePath(homePath(role), null), '/login')
    assert.equal(resolvePath('/login', role), homePath(role))
    assert.equal(resolvePath('/home/SomeoneElse', role), homePath(role))
    assert.equal(resolvePath('/foundation', role), '/foundation')
  }
})
