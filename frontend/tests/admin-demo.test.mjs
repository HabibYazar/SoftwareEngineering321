import test from 'node:test'
import assert from 'node:assert/strict'
import { createMockAuditService } from '../src/admin/audit/mockAuditService.ts'
import { emptyAuditFilter, filterAuditEvents } from '../src/admin/audit/model.ts'
import { createMockStaffManagementService } from '../src/admin/staff/mockStaffManagementService.ts'
import { filterDemoStaff } from '../src/admin/staff/model.ts'
import { auditLogsPath, canAccessRoute, homePath, listWorkflowRoutes, resolvePath, staffManagementPath } from '../src/auth/routing.ts'
import { roles } from '../src/auth/types.ts'

test('only Administrator has direct access and navigation entries for admin demos', () => {
  for (const path of [auditLogsPath, staffManagementPath]) {
    assert.equal(resolvePath(path, null), '/login')
    for (const role of roles) {
      const allowed = role === 'Administrator'
      assert.equal(canAccessRoute(path, role), allowed)
      assert.equal(listWorkflowRoutes(role).some(item => item.path === path), allowed)
      assert.equal(resolvePath(path, role), allowed ? path : homePath(role))
    }
  }
})

test('synthetic audit filters actor, action, inclusive UTC dates and sorting', async () => {
  const service = createMockAuditService()
  const events = await service.list()
  const range = { ...emptyAuditFilter, actor: ' RECEPTION ', from: '2026-11-19', to: '2026-11-19' }
  const results = filterAuditEvents(events, range, true)
  assert.equal(results.length, 2)
  assert.equal(results[0].action, 'Edit patient')
  assert.equal(filterAuditEvents(events, range, false)[0].action, 'Register patient')
  assert.equal(filterAuditEvents(events, { ...range, action: 'Edit patient' }, true).length, 1)
  assert.deepEqual(filterAuditEvents(events, { ...range, actor: 'missing' }, true), [])
})

test('demo staff creates, edits, filters, deactivates, reactivates and requests reset without external state', async () => {
  const service = createMockStaffManagementService()
  const person = { username: ' Synthetic.demo ', displayName: 'Synthetic Worker', role: 'Nurse' }
  await service.save(person)
  const [created] = filterDemoStaff((await service.list()).staff, ' SYNTHETIC ', 'Nurse')
  assert.equal(created.username, 'synthetic.demo')
  await assert.rejects(service.save(person), /already exists/)
  await service.save({ ...person, displayName: 'Edited Name', role: 'Physician' }, created.id)
  await service.setActive(created.id, false)
  await assert.rejects(service.requestPasswordReset(created.id), /Activate/)
  const [edited] = filterDemoStaff((await service.list()).staff, 'edited', 'Physician')
  assert.equal(edited.active, false)
  await service.setActive(created.id, true)
  assert.equal(await service.requestPasswordReset(created.id), undefined)
  assert.equal((await createMockStaffManagementService().list()).staff.some(item => item.id === created.id), false)
})

test('the signed-in admin and last active admin cannot be deactivated or demoted', async () => {
  const service = createMockStaffManagementService()
  const { staff } = await service.list()
  const owner = staff.find(person => person.username === 'admin.demo')
  const backup = staff.find(person => person.username === 'backup-admin.demo')
  await assert.rejects(service.setActive(owner.id, false), /signed-in/)
  await assert.rejects(service.save({ ...owner, role: 'Nurse' }, owner.id), /signed-in/)
  await service.setActive(backup.id, false)
  await assert.rejects(service.setActive(owner.id, false), /At least one active/)
  await assert.rejects(service.save({ ...owner, role: 'Physician' }, owner.id), /At least one active/)
})

test('demo permission and department assignment edits cannot affect actual permissions or call HTTP', async () => {
  const service = createMockStaffManagementService()
  const { staff, departments } = await service.list()
  const doctor = staff.find(person => person.role === 'Physician')
  const dept = departments.find(item => item.name === 'Emergency')
  const fetch = globalThis.fetch
  globalThis.fetch = () => { throw new Error('Unexpected HTTP request') }
  try {
    await service.setPermission('Physician', 'manageAccounts', true)
    await service.setAssignment(doctor.id, dept.id, true)
    const data = await service.list()
    assert.ok(data.rolePermissions.Physician.includes('manageAccounts'))
    assert.ok(data.staff.find(item => item.id === doctor.id).departmentIds.includes(dept.id))
    assert.equal(canAccessRoute(staffManagementPath, 'Physician'), false)
    await service.setAssignment(doctor.id, dept.id, false)
    await assert.rejects(service.setAssignment(staff[0].id, dept.id, true), /Physician/i)
  } finally { globalThis.fetch = fetch }
})
