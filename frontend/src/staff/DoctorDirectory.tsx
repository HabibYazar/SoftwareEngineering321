import { useCallback, useEffect, useState } from 'react'
import { useResource } from '../components/useResource'
import { Alert, Button, Card, Icon, TextField } from '../components/ui'
import { DepartmentError, directoryDepartmentNames } from './departments'
import { filterClinicians } from './filtering'
import type { DirectoryServices } from './service'
import { StaffError, type Clinician } from './types'
import { useClinicians } from './useClinicians'

function DepartmentSelector({ departments, selected, onSelect }: {
  departments: string[]; selected: string | null; onSelect: (department: string | null) => void
}) {
  // Ada dayalı filtre, rastgele önizleme kimlikleri yenilendiğinde de korunur.
  return <div className="department-grid" role="group" aria-labelledby="department-heading">
    {[null, ...departments].map(department => <Button key={department === null ? 'all' : 'department:' + department}
      variant="secondary" className="department-button" aria-pressed={selected === department}
      onClick={() => onSelect(department)}>
      <Icon name={department === null ? 'all' : 'department'} />
      <span>{department ?? 'All Departments'}</span>
    </Button>)}
  </div>
}

function DirectoryFailure({ message, retry }: { message: string; retry?: () => void }) {
  return <div className="result-state">
    <Alert tone="error">{message}</Alert>
    {retry && <Button variant="secondary" onClick={retry}>Try again</Button>}
  </div>
}

function DoctorResults({ clinicians, department, query }: {
  clinicians: Clinician[]; department: string | null; query: string
}) {
  const matches = filterClinicians(clinicians, department, query)
  const emptyMessage = query.trim() ? 'No doctors match your search.'
    : department ? 'No doctors are assigned to this department.' : 'No doctors are available.'
  return <>
    <p className="result-count muted" data-empty={matches.length === 0} role="status">
      {matches.length === 0 ? emptyMessage : matches.length + (matches.length === 1 ? ' doctor' : ' doctors')}
    </p>
    {matches.length > 0 && <ul className="doctor-grid">
      {matches.map(clinician => <li key={clinician.id}>
        <Card className="doctor-card">
          <span className="doctor-icon"><Icon name="medical" /></span>
          <div><h3>{clinician.displayName}</h3><p className="muted">{clinician.department}</p></div>
        </Card>
      </li>)}
    </ul>}
  </>
}

// Departman ve doktor istekleri birbirini engellemez. Başarılı doktor verisiyle sınırlı filtreleme yapılabilir.
export function DoctorDirectory({ directory, refreshVersion }: { directory: DirectoryServices; refreshVersion: number }) {
  const [selection, setSelection] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const { doctors, departments, modes } = directory
  const loadDepartments = useCallback((signal: AbortSignal) => departments!.list(signal), [departments])
  const departmentRequest = useResource(departments ? loadDepartments : null, refreshVersion)
  const all = useClinicians(doctors, null, refreshVersion)
  const { state: departmentState } = departmentRequest
  const canonical = departmentState.status === 'success' ? departmentState.data : undefined
  const names = directoryDepartmentNames(canonical, all.state.status === 'success' ? all.state.data : [])
  const membershipKnown = Boolean(canonical) || (departmentState.status !== 'loading' && all.state.status === 'success')
  const selected = membershipKnown && selection !== null && !names.includes(selection) ? null : selection

  useEffect(() => {
    if (selection !== selected) setSelection(selected)
  }, [selection, selected])

  // Seçim ve Refresh değişince önceki tamamlanmış sonuç, yeni sorgunun sonucu olarak gösterilmez.
  const loadSelected = useCallback((signal: AbortSignal) => doctors.listClinicians(selected, signal), [doctors, selected])
  const filtered = useResource(selected ? loadSelected : null, refreshVersion)
  const results = selected ? filtered : all
  const doctorError = results.state.status === 'error' ? results.state.error : null
  const departmentError = departmentState.status === 'error' ? departmentState.error : null
  const canRetryDoctors = !(doctorError instanceof StaffError && ['access', 'unavailable'].includes(doctorError.code))
  const canRetryDepartments = !(departmentError instanceof DepartmentError && ['access', 'unavailable'].includes(departmentError.code))

  return <section className="intro directory" aria-labelledby="page-title">
    <div className="page-heading">
      <h1 id="page-title" tabIndex={-1}>Find a doctor</h1>
      <p className="muted">Browse doctors by department or search by name.</p>
    </div>
    {modes.staff === 'mock' && <Alert tone="warning">Demo directory. All doctor names are synthetic.</Alert>}
    {(modes.staff === 'http' || modes.departments) && <Alert tone="warning">
      Development HTTP preview. Endpoint availability depends on the local backend.
      {modes.departments === 'pr251' && ' The temporary PR #251 response adapter is enabled.'}
    </Alert>}
    <Card className="directory-filter">
      <section aria-labelledby="department-heading">
        <h2 id="department-heading">Departments</h2>
        {departmentState.status === 'loading' && <p className="muted resource-loading" role="status">Loading departments…</p>}
        {departmentState.status === 'error' && <DirectoryFailure
          message={departmentError instanceof DepartmentError ? departmentError.message : 'Unable to load departments.'}
          retry={canRetryDepartments ? departmentRequest.retry : undefined} />}
        {canonical && canonical.length === 0 && <p className="muted" role="status">No departments are available.</p>}
        {departmentState.status === 'error' && names.length > 0 && <p className="muted">Showing departments from doctor results.</p>}
        <DepartmentSelector departments={names} selected={selected} onSelect={setSelection} />
      </section>
    </Card>
    <section className="directory-results" aria-labelledby="results-heading">
      <div className="results-heading">
        <h2 id="results-heading" tabIndex={-1}>{selected ?? 'All doctors'}</h2>
        <TextField label="Search by name" type="search" value={query}
          onChange={event => setQuery(event.target.value)} />
      </div>
      {results.state.status === 'loading' && <p className="result-state muted resource-loading" role="status">Loading doctors…</p>}
      {results.state.status === 'error' && <DirectoryFailure
        message={doctorError instanceof StaffError ? doctorError.message : 'Unable to load doctors.'}
        retry={canRetryDoctors ? results.retry : undefined} />}
      {results.state.status === 'success' && <DoctorResults clinicians={results.state.data} department={selected} query={query} />}
    </section>
  </section>
}
