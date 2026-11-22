import { useCallback, useRef, useState } from 'react'
import { roleLabels } from '../../auth/routing'
import { roles, type Role } from '../../auth/types'
import { useResource } from '../../components/useResource'
import { Alert, Button, Card, TextField } from '../../components/ui'
import { filterDemoStaff, type DemoStaff, type StaffManagementService } from './model'
import { StaffForm } from './StaffForm'
import { StaffSettings } from './StaffSettings'

type Feedback = { message: string; tone: 'info' | 'error' }

// Bu sayfanın servis seçimi gerçek hesap yönetimi için ağ isteği oluşturamaz.
export function StaffManagementPage({ service, refreshVersion }: {
  service: StaffManagementService | null; refreshVersion: number
}) {
  const load = useCallback((signal: AbortSignal) => service!.list(signal), [service])
  const { state, retry } = useResource(service ? load : null, refreshVersion)
  const [query, setQuery] = useState('')
  const [role, setRole] = useState<Role | ''>('')
  const [form, setForm] = useState<DemoStaff | null | undefined>(undefined)
  const [feedback, setFeedback] = useState<Feedback>()
  const [busy, setBusy] = useState(false)
  const inFlight = useRef(false)
  const data = state.status === 'success' ? state.data : null
  const people = data ? filterDemoStaff(data.staff, query, role) : []

  // Başarısız yazmada form açık kalır. Başarılı yazma yalnızca sentetik listeyi yeniden yükler.
  async function operate(action: () => Promise<void>, success: string, closeForm = false) {
    if (inFlight.current) return
    inFlight.current = true
    setBusy(true)
    try {
      await action()
      if (closeForm) setForm(undefined)
      setFeedback({ tone: 'info', message: success })
      retry()
    } catch (error) {
      setFeedback({ tone: 'error', message: error instanceof Error ? error.message : 'Unable to complete this demo operation.' })
    } finally {
      inFlight.current = false
      setBusy(false)
    }
  }

  function openForm(record: DemoStaff | null) {
    setFeedback(undefined)
    setForm(record)
  }

  return <section className="intro admin-page" aria-labelledby="page-title">
    <div className="page-heading">
      <h1 id="page-title" tabIndex={-1}>Staff management</h1>
      <p className="muted">Manage synthetic accounts, roles and doctor assignments.</p>
    </div>
    {!service ? <Alert>Staff management is unavailable. This release has no approved staff management API.</Alert> : <>
      <Alert tone="warning">DEMO MODE — All staff, permission and assignment changes are simulated.</Alert>
      {feedback && <Alert tone={feedback.tone}>{feedback.message}</Alert>}
      {form !== undefined && <StaffForm key={form?.id ?? 'new'} record={form} busy={busy}
        save={input => void operate(() => service.save(input, form?.id), 'Demo staff account saved.', true)}
        cancel={() => { setForm(undefined); setFeedback(undefined) }} />}
      <Card>
        <div className="admin-heading-row">
          <h2 id="staff-table-heading">Demo staff accounts</h2>
          <Button disabled={busy} onClick={() => openForm(null)}>Add demo staff</Button>
        </div>
        <div className="admin-filters staff-filters">
          <TextField label="Search demo staff" type="search" value={query} onChange={event => setQuery(event.target.value)} />
          <div className="field">
            <label htmlFor="demo-staff-filter">Filter by role</label>
            <select className="text-field" id="demo-staff-filter" value={role}
              onChange={event => setRole(event.target.value as Role | '')}>
              <option value="">All roles</option>
              {roles.map(item => <option key={item} value={item}>{roleLabels[item]}</option>)}
            </select>
          </div>
        </div>
        {state.status === 'loading' && <p role="status">Loading demo staff…</p>}
        {state.status === 'error' && <div className="admin-feedback">
          <Alert tone="error">Unable to load demo staff.</Alert><Button onClick={retry}>Try again</Button>
        </div>}
        {state.status === 'success' && (people.length ? <div className="table-scroll" role="region" aria-labelledby="staff-table-heading" tabIndex={0}>
          <table className="data-table">
            <thead><tr><th scope="col">Staff member</th><th scope="col">Role</th><th scope="col">Status</th><th scope="col">Demo actions</th></tr></thead>
            <tbody>{people.map(person => <tr key={person.id}>
              <th scope="row"><strong>{person.displayName}</strong><small className="muted">{person.username}</small></th>
              <td>{roleLabels[person.role]}</td>
              <td><span className="status-chip" data-status={person.active ? 'Active' : 'Inactive'}>{person.active ? 'Active' : 'Inactive'}</span></td>
              <td><div className="admin-actions">
                <Button variant="secondary" disabled={busy} onClick={() => openForm(person)}>Edit</Button>
                <Button variant="secondary" disabled={busy} onClick={() => {
                  if (person.active && !window.confirm('DEMO: Deactivate this staff account?')) return
                  void operate(() => service.setActive(person.id, !person.active), person.active
                    ? 'Demo account deactivated.' : 'Demo account activated.')
                }}>{person.active ? 'Deactivate' : 'Activate'}</Button>
                <Button variant="secondary" disabled={busy || !person.active} onClick={() => {
                  if (window.confirm('DEMO: Request a simulated password reset?')) {
                    void operate(() => service.requestPasswordReset(person.id),
                      'Demo password reset requested. No password or notification was generated.')
                  }
                }}>Request reset</Button>
              </div></td>
            </tr>)}</tbody>
          </table>
        </div> : <p className="muted" role="status">No demo staff match these filters.</p>)}
      </Card>
      {data && <StaffSettings data={data} busy={busy}
        setPermission={(target, permission, enabled) => {
          if (!enabled && !window.confirm('DEMO: Remove this permission from the preview role?')) return
          void operate(() => service.setPermission(target, permission, enabled), 'Demo permission preview updated.')
        }}
        setAssignment={(id, departmentId, assigned) => {
          if (!assigned && !window.confirm('DEMO: Remove this department assignment?')) return
          void operate(() => service.setAssignment(id, departmentId, assigned), 'Demo doctor assignment updated.')
        }} />}
    </>}
  </section>
}
