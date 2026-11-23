import { useCallback, useState, type FormEvent } from 'react'
import { roleLabels } from '../../auth/routing'
import { useResource } from '../../components/useResource'
import { Alert, Button, Card, TextField } from '../../components/ui'
import { auditActions, emptyAuditFilter, filterAuditEvents, type AuditFilter, type AuditService } from './model'

const timestamp = new Intl.DateTimeFormat('en-GB', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC',
})

// Tarih ve filtre taslağı, yalnızca kullanıcı Search düğmesini kullandığında tabloyu değiştirir.
export function AuditLogsPage({ service, refreshVersion }: { service: AuditService | null; refreshVersion: number }) {
  const [draft, setDraft] = useState(emptyAuditFilter)
  const [filter, setFilter] = useState(emptyAuditFilter)
  const [newestFirst, setNewestFirst] = useState(true)
  const [dateError, setDateError] = useState<string>()
  const load = useCallback((signal: AbortSignal) => service!.list(signal), [service])
  const { state, retry } = useResource(service ? load : null, refreshVersion)
  const events = state.status === 'success' ? filterAuditEvents(state.data, filter, newestFirst) : []

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (draft.from && draft.to && draft.from > draft.to) {
      setDateError('The end date must be on or after the start date.')
      document.getElementById('audit-to')?.focus()
      return
    }
    setDateError(undefined)
    setFilter(draft)
  }

  return <section className="intro admin-page" aria-labelledby="page-title">
    <div className="page-heading">
      <h1 id="page-title" tabIndex={-1}>Audit logs</h1>
      <p className="muted">Review activity by actor, action or date.</p>
    </div>
    {service ? <>
      <Alert tone="warning">DEMO MODE — SYNTHETIC AUDIT EVENTS</Alert>
      <Card>
        <form className="admin-filters" onSubmit={apply} noValidate>
          <TextField label="User" type="search" value={draft.actor}
            onChange={event => setDraft({ ...draft, actor: event.target.value })} />
          <div className="field">
            <label htmlFor="audit-action">Action type</label>
            <select className="text-field" id="audit-action" value={draft.action}
              onChange={event => setDraft({ ...draft, action: event.target.value as AuditFilter['action'] })}>
              <option value="">All actions</option>
              {auditActions.map(action => <option key={action}>{action}</option>)}
            </select>
          </div>
          <TextField id="audit-from" label="From date (UTC)" type="date" value={draft.from}
            onChange={event => { setDateError(undefined); setDraft({ ...draft, from: event.target.value }) }} />
          <TextField id="audit-to" label="To date (UTC)" type="date" value={draft.to} error={dateError}
            onChange={event => { setDateError(undefined); setDraft({ ...draft, to: event.target.value }) }} />
          <div className="admin-actions">
            <Button type="submit">Search</Button>
            <Button variant="secondary" onClick={() => {
              setDraft(emptyAuditFilter)
              setFilter(emptyAuditFilter)
              setDateError(undefined)
            }}>Clear filters</Button>
          </div>
        </form>
      </Card>
      <Card>
        <div className="admin-heading-row">
          <h2 id="audit-table-heading">Activity</h2>
          <Button variant="secondary" onClick={() => setNewestFirst(value => !value)}>
            {newestFirst ? 'Show oldest first' : 'Show newest first'}
          </Button>
        </div>
        {state.status === 'loading' && <p className="resource-loading" role="status">Loading audit events…</p>}
        {state.status === 'error' && <div className="admin-feedback">
          <Alert tone="error">Unable to load synthetic audit events.</Alert>
          <Button onClick={retry}>Try again</Button>
        </div>}
        {state.status === 'success' && (events.length ? <>
          <p className="muted" role="status">{events.length} {events.length === 1 ? 'event' : 'events'}</p>
          <div className="table-scroll" role="region" aria-labelledby="audit-table-heading" tabIndex={0}>
            <table className="data-table audit-table">
              <thead><tr>
                <th scope="col" aria-sort={newestFirst ? 'descending' : 'ascending'}>Timestamp (UTC)</th>
                <th scope="col">Actor</th><th scope="col">Role</th><th scope="col">Action</th>
                <th scope="col">Target</th><th scope="col">Status</th>
              </tr></thead>
              <tbody>{events.map(event => <tr key={event.id}>
                <td><time dateTime={event.timestamp}>{timestamp.format(new Date(event.timestamp))}</time></td>
                <td>{event.actor}</td><td>{roleLabels[event.role]}</td><td>{event.action}</td><td>{event.target}</td>
                <td><span className="status-chip" data-status={event.status}>{event.status}</span></td>
              </tr>)}</tbody>
            </table>
          </div>
        </> : <p className="muted" role="status">No audit events match these filters.</p>)}
      </Card>
    </> : <Alert>Audit logs are unavailable. This release has no approved audit listing API.</Alert>}
  </section>
}
