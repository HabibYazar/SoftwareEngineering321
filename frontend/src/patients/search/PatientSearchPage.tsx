import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useAuth } from '../../auth/AuthProvider'
import { Alert, Button, Card, TextField } from '../../components/ui'
import type { PatientSummary } from '../recentPatients'
import { PatientResultList } from './PatientResultList'
import { patientNameLimit, validatePatientQuery, type PatientQuery, type PatientSearchService, type SearchMode } from './model'
import { createSearchRunner, type SearchState } from './searchRunner'
import { patientSearchMockEnabled } from './service'
import './search.css'

interface PatientSearchPageProps {
  service: PatientSearchService
  refreshVersion: number
  onRefreshReady: (ready: boolean) => void
  active: boolean
  recentPatients: PatientSummary[]
}

const searchModes: { value: SearchMode; label: string }[] = [
  { value: 'nationalId', label: 'National ID' }, { value: 'name', label: 'Name' },
]

// Profil düzenlenirken arama bellekte kalır. İş akışından ayrılınca ölçütler ve sonuçlar kaldırılır.
export function PatientSearchPage({ service, refreshVersion, onRefreshReady, active, recentPatients }: PatientSearchPageProps) {
  const { getAccessToken, role } = useAuth()
  const [mode, setMode] = useState<SearchMode>('nationalId')
  const [input, setInput] = useState('')
  const [inputError, setInputError] = useState<string>()
  const [state, setState] = useState<SearchState>({ status: 'idle' })
  const [runner] = useState(() => createSearchRunner(service, setState))
  const lastQuery = useRef<PatientQuery | null>(null)
  const seenRefresh = useRef(refreshVersion)
  const form = useRef<HTMLFormElement>(null)

  useEffect(() => () => {
    runner.cancel()
    onRefreshReady(false)
  }, [runner, onRefreshReady])

  // Ortak Refresh, yalnızca son gönderilen ve ölçütü değiştirilmemiş sorguyu yineler.
  const repeatSearch = useCallback(() => {
    if (lastQuery.current) void runner.run(lastQuery.current, getAccessToken())
  }, [runner, getAccessToken])

  useEffect(() => {
    // Kaydetme sonrasında aynı ölçütü arama yeniden görünür olduğunda çalıştırırız.
    if (!active || seenRefresh.current === refreshVersion) return
    seenRefresh.current = refreshVersion
    repeatSearch()
  }, [active, refreshVersion, repeatSearch])

  function focusQuery() {
    form.current?.querySelector<HTMLInputElement>('#patient-search-query')?.focus()
  }

  // Ölçüt düzenlendiğinde önceki hasta kartları hemen kaldırılır ve istek iptal edilir.
  function clearResults() {
    runner.cancel()
    lastQuery.current = null
    setInputError(undefined)
    setState({ status: 'idle' })
    onRefreshReady(false)
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (state.status === 'loading') return
    clearResults()
    const result = validatePatientQuery(mode, input)
    if ('error' in result) {
      setInputError(result.error)
      focusQuery()
      return
    }

    lastQuery.current = result.query
    onRefreshReady(true)
    repeatSearch()
  }

  const headingId = active ? 'page-title' : 'patient-search-title'
  const demoPatients = service.listDemoPatients?.()
  const queryLabel = mode === 'nationalId' ? 'Synthetic national ID' : 'Patient name'
  const statusText = state.status === 'idle' ? 'Search to see matching patients.'
    : state.status === 'loading' ? 'Searching patients…'
    : state.status === 'success'
      ? state.patients.length === 0 ? 'No patient found'
        : state.patients.length === 1 ? '1 patient found' : state.patients.length + ' patients found'
      : ''

  return <section className="intro patient-search" aria-labelledby={headingId}>
    <div className="page-heading">
      <h1 id={headingId} tabIndex={-1}>Search patients</h1>
      <p className="muted">Search by exact synthetic national ID or part of a patient’s name.</p>
    </div>
    {patientSearchMockEnabled && <Alert tone="warning">Demo patient search is active. Results are synthetic.</Alert>}
    <Card>
      <form ref={form} className="patient-search-form" autoComplete="off" noValidate onSubmit={submit}>
        <fieldset>
          <legend>Search by</legend>
          <div className="patient-search-modes">
            {searchModes.map(option => <label key={option.value}>
              <input type="radio" name="patient-search-mode" value={option.value} checked={mode === option.value}
                onChange={() => {
                  setMode(option.value)
                  setInput('')
                  clearResults()
                }} />
              {option.label}
            </label>)}
          </div>
        </fieldset>
        <TextField id="patient-search-query" name="patient-search-query" label={queryLabel}
          inputMode={mode === 'nationalId' ? 'numeric' : undefined}
          maxLength={mode === 'nationalId' ? 11 : patientNameLimit}
          required value={input} error={inputError}
          onChange={event => {
            setInput(event.target.value)
            clearResults()
          }} />
        <div className="patient-search-actions">
          <Button type="submit" disabled={state.status === 'loading'}>
            {state.status === 'loading' ? 'Searching…' : 'Search'}
          </Button>
          <Button variant="secondary" disabled={input === '' && !inputError && state.status === 'idle'} onClick={() => {
            setInput('')
            clearResults()
            focusQuery()
          }}>Clear</Button>
        </div>
      </form>
    </Card>
    <section className="patient-search-results" aria-labelledby="patient-results-heading">
      <h2 id="patient-results-heading">Search results</h2>
      <p className="muted" role="status">{statusText}</p>
      {state.status === 'error' && <>
        <Alert tone="error">{state.message}</Alert>
        {state.canRetry && <Button variant="secondary" onClick={() => {
          focusQuery()
          repeatSearch()
        }}>Try again</Button>}
      </>}
      {state.status === 'success' && state.patients.length > 0 && <PatientResultList patients={state.patients} role={role} />}
    </section>
    {/* Gerçek arama ölçüt ister. Oturum özeti ve sentetik liste bu uç noktayı kullanmaz. */}
    {recentPatients.length > 0 && <section className="patient-search-results" aria-labelledby="recent-patients">
      <h2 id="recent-patients">Recently registered in this session</h2>
      <PatientResultList patients={recentPatients} role={role} />
    </section>}
    <section className="patient-search-results" aria-labelledby="patient-list-heading">
      <h2 id="patient-list-heading">{demoPatients ? 'Demo patient list' : 'Patient list'}</h2>
      {demoPatients ? demoPatients.length > 0
        ? <PatientResultList patients={demoPatients} role={role} />
        : <p className="muted">No synthetic patients are available.</p>
        : <Alert>Full patient listing requires backend support.</Alert>}
    </section>
  </section>
}
