import { roleLabels } from '../../auth/routing'
import { roles, type Role } from '../../auth/types'
import { Card } from '../../components/ui'
import { demoPermissions, type DemoPermission, type StaffSnapshot } from './model'

interface SettingsProps {
  data: StaffSnapshot
  busy: boolean
  setPermission: (role: Role, permission: DemoPermission, enabled: boolean) => void
  setAssignment: (id: string, departmentId: string, assigned: boolean) => void
}

// Matris bir yönetim simülasyonudur. Uygulamanın gerçek rota izinleri bu bileşenden okunmaz.
export function StaffSettings({ data, busy, setPermission, setAssignment }: SettingsProps) {
  const permissions = Object.entries(demoPermissions) as [DemoPermission, string][]
  const physicians = data.staff.filter(person => person.role === 'Physician')

  return <>
    <Card>
      <h2 id="demo-permission-heading">Demo role-permission matrix</h2>
      <p className="muted">Changes here affect this preview only.</p>
      {/* Tek kontrol kümesi masaüstünde tablo, mobilde rol kartları olarak sunulur. */}
      <div className="permission-region" role="region" aria-labelledby="demo-permission-heading" tabIndex={0}>
        <table className="data-table permission-table" role="table">
          <thead role="rowgroup"><tr role="row"><th scope="col" role="columnheader">Role</th>{permissions.map(([key, label]) => <th scope="col" role="columnheader" key={key}>{label}</th>)}</tr></thead>
          <tbody role="rowgroup">{roles.map(role => <tr key={role} role="row">
            <th scope="row" role="rowheader">{roleLabels[role]}</th>
            {permissions.map(([permission, label]) => <td key={permission} role="cell">
              <label className="permission-control">
                <input type="checkbox" aria-label={roleLabels[role] + ': ' + label} disabled={busy}
                  checked={data.rolePermissions[role].includes(permission)}
                  onChange={event => setPermission(role, permission, event.target.checked)} />
                <span aria-hidden="true">{label}</span>
              </label>
            </td>)}
          </tr>)}</tbody>
        </table>
      </div>
    </Card>
    <Card>
      <h2>Demo departments and doctor assignments</h2>
      <ul className="department-names">{data.departments.map(department => <li className="status-chip" key={department.id}>{department.name}</li>)}</ul>
      {physicians.length ? <div className="assignment-grid">{physicians.map(physician => <fieldset key={physician.id} disabled={busy}>
        <legend>{physician.displayName}</legend>
        {data.departments.map(department => <label key={department.id} className="registration-checkbox">
          <input type="checkbox" checked={physician.departmentIds.includes(department.id)}
            onChange={event => setAssignment(physician.id, department.id, event.target.checked)} />
          {department.name}
        </label>)}
      </fieldset>)}</div> : <p className="muted">No demo doctors to assign.</p>}
    </Card>
  </>
}
