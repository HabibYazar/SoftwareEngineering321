import { useEffect, useState } from 'react'
import { Alert, Button, Card, Icon, TextField } from '../components/ui'
import { filterClinicians, listDepartments } from './filtering'
import { staffMockEnabled } from './service'
import type { Clinician, StaffService } from './types'
import { useClinicians } from './useClinicians'

interface DepartmentSelectorProps {
  departments: string[]
  selected: string | null
  onSelect: (department: string | null) => void
}

// Her seçenek tek tıklamayla filtreyi değiştirir. null bütün departmanları temsil eder.
function DepartmentSelector({ departments, selected, onSelect }: DepartmentSelectorProps) {
  const options = [null, ...departments]
  return <section aria-labelledby="department-heading">
    <h2 id="department-heading">Departments</h2>
    <div className="department-grid" role="group" aria-labelledby="department-heading">
      {options.map(department => <Button key={department === null ? 'all' : 'department:' + department}
        variant="secondary" className="department-button" aria-pressed={selected === department}
        onClick={() => onSelect(department)}>
        <Icon name={department === null ? 'all' : 'department'} />
        <span>{department ?? 'All Departments'}</span>
      </Button>)}
    </div>
  </section>
}

// Kart, API'den doğrulanmış klinisyen adı ve departman bilgisini gösterir.
function DoctorCard({ clinician }: { clinician: Clinician }) {
  return <Card className="doctor-card">
    <span className="doctor-icon"><Icon name="medical" /></span>
    <div>
      <h3>{clinician.displayName}</h3>
      <p className="muted">{clinician.department}</p>
    </div>
  </Card>
}

// Yükleme ve hata aynı alanda duyurulur. Yeniden deneme yalnızca başarısız sorguyu çalıştırır.
function LoadingState() {
  return <p className="result-state muted" role="status">Loading doctors…</p>
}

// Yükleme sırasında hata düğmesi kaldırılır. Odağı ekranda kalan başlığa taşır.
function DirectoryError({ message, retry, headingId }: { message: string; retry?: () => void; headingId: string }) {
  function retryFromHeading() {
    document.getElementById(headingId)?.focus()
    retry?.()
  }

  return <div className="result-state">
    <Alert tone="error">{message}</Alert>
    {retry && <Button variant="secondary" onClick={retryFromHeading}>Try again</Button>}
  </div>
}

interface DoctorResultsProps {
  clinicians: Clinician[]
  department: string | null
  query: string
}

// Boş arama ile departmanda hiç kayıt bulunmaması için farklı mesajlar gösterir.
function DoctorResults({ clinicians, department, query }: DoctorResultsProps) {
  const matches = filterClinicians(clinicians, department, query)
  const emptyMessage = query.trim() ? 'No doctors match your search.' : 'No doctors are available.'
  return <>
    <p className="result-count muted" role="status">
      {matches.length === 0 ? emptyMessage : matches.length + (matches.length === 1 ? ' doctor' : ' doctors')}
    </p>
    {matches.length > 0 && <ul className="doctor-grid">
      {matches.map(clinician => <li key={clinician.id}><DoctorCard clinician={clinician} /></li>)}
    </ul>}
  </>
}

// Filtreli istek ayrı tutulur. Yüklenirken seçili olmayan departmanın kartları görünmez.
function DepartmentResults({ service, department, query }: {
  service: StaffService; department: string; query: string
}) {
  const { state, retry } = useClinicians(service, department)
  if (state.status === 'loading') return <LoadingState />
  if (state.status === 'error') return <DirectoryError message={state.message} retry={state.canRetry ? retry : undefined} headingId="results-heading" />
  return <DoctorResults clinicians={state.clinicians} department={department} query={query} />
}

// Receptionist, tek tıklamayla departmanı seçer ve mevcut sonuçları ada göre arar.
export function DoctorDirectory({ service, refreshVersion }: { service: StaffService; refreshVersion: number }) {
  // Departman API sorgusunu, arama metni ise o sorgunun ekranda görünen sonuçlarını değiştirir.
  const [selection, setSelection] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const { state, retry } = useClinicians(service, null, refreshVersion)
  const departments = state.status === 'success' ? listDepartments(state.clinicians) : []
  const department = selection !== null && departments.includes(selection) ? selection : null

  // Yenileme sırasında seçimi korur. Başarılı yanıt departmanı artık içermiyorsa tümünü gösterir.
  useEffect(() => {
    if (state.status === 'success' && selection !== department) setSelection(department)
  }, [state.status, selection, department])

  return <section className="intro directory" aria-labelledby="page-title">
    <div className="page-heading">
      <h1 id="page-title" tabIndex={-1}>Find a doctor</h1>
      <p className="muted">Browse doctors by department or search by name.</p>
    </div>
    {staffMockEnabled && <Alert tone="warning">Demo directory. All doctor names are synthetic.</Alert>}
    {state.status === 'loading' && <LoadingState />}
    {state.status === 'error' && <DirectoryError message={state.message} retry={state.canRetry ? retry : undefined} headingId="page-title" />}
    {state.status === 'success' && <>
      <DepartmentSelector departments={departments} selected={department} onSelect={setSelection} />
      <section className="directory-results" aria-labelledby="results-heading">
        <div className="results-heading">
          <h2 id="results-heading" tabIndex={-1}>{department ?? 'All doctors'}</h2>
          <TextField label="Search by name" type="search" value={query}
            onChange={event => setQuery(event.target.value)} />
        </div>
        {department === null
          ? <DoctorResults clinicians={state.clinicians} department={null} query={query} />
          // Anahtar değişince önceki departmanın sonuçları ve isteği kaldırılır.
          : <DepartmentResults key={department} service={service} department={department} query={query} />}
      </section>
    </>}
  </section>
}
