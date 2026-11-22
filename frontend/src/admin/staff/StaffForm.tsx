import { useState, type FormEvent } from 'react'
import { roleLabels } from '../../auth/routing'
import { roles, type Role } from '../../auth/types'
import { Button, Card, TextField } from '../../components/ui'
import type { DemoStaff, StaffInput } from './model'

// Her açılışta seçilen hesabın kopyası kullanılır. Kaydetmeden yapılan değişiklikler demo kaydına ulaşmaz.
export function StaffForm({ record, busy, save, cancel }: {
  record: DemoStaff | null; busy: boolean; save: (input: StaffInput) => void; cancel: () => void
}) {
  const [draft, setDraft] = useState<StaffInput>(() => record ?? { username: '', displayName: '', role: 'Receptionist' })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (record && draft.role !== record.role && !window.confirm('DEMO: Change this staff account’s role?')) return
    save(draft)
  }

  return <Card>
    <h2>{record ? 'Edit demo staff account' : 'Add demo staff account'}</h2>
    <form className="admin-filters" onSubmit={submit} autoComplete="off">
      <TextField label="Display name" value={draft.displayName} required maxLength={100} disabled={busy}
        onChange={event => setDraft({ ...draft, displayName: event.target.value })} />
      <TextField label="Username" value={draft.username} required minLength={3} maxLength={40} disabled={busy}
        onChange={event => setDraft({ ...draft, username: event.target.value })} />
      <div className="field">
        <label htmlFor="demo-staff-role">Role</label>
        <select className="text-field" id="demo-staff-role" value={draft.role} disabled={busy}
          onChange={event => setDraft({ ...draft, role: event.target.value as Role })}>
          {roles.map(role => <option key={role} value={role}>{roleLabels[role]}</option>)}
        </select>
      </div>
      <div className="admin-actions">
        <Button type="submit" disabled={busy}>{busy ? 'Saving demo account…' : 'Save demo account'}</Button>
        <Button variant="secondary" disabled={busy} onClick={cancel}>Cancel</Button>
      </div>
    </form>
  </Card>
}
