import { useEffect, useLayoutEffect, useRef, useState, type FormEvent } from 'react'
import { useAuth } from '../../auth/AuthProvider'
import { patientSearchPath } from '../../auth/routing'
import { Alert, Button, Card } from '../../components/ui'
import { PatientDetailFields, PatientRelativeFields } from '../PatientDetailFields'
import type { Patient } from '../types'
import { useFieldFeedback } from '../useFieldFeedback'
import { useUnsavedPatientForm } from '../useUnsavedPatientForm'
import type { PatientDetailsDraft, PatientDetailsField } from '../validation'
import { draftFromPatient, isProfileDirty, PatientProfileError, validateProfile, type PatientProfileService } from './model'
import { createProfileSaver } from './saveRunner'
import { patientProfileMockEnabled } from './service'
import './profile.css'

interface ProfileProps {
  service: PatientProfileService
  onDirtyChange: (dirty: boolean) => void
  onSaved: (patient: Patient) => void
}

type LoadState = { status: 'loading' } | { status: 'ready'; patient: Patient } | { status: 'error'; error: PatientProfileError }
type Feedback = { tone: 'info' | 'error'; message: string }

function SearchLink({ children }: { children: string }) {
  return <a href={'#' + patientSearchPath} className="button button--secondary">{children}</a>
}

// Yalnızca URL'de seçilen kayıt yüklenir. Hatalı adresler servis çağrısı yapmaz.
export function PatientProfilePage({ patientId, ...props }: ProfileProps & { patientId: string | null }) {
  const { service } = props
  const { getAccessToken } = useAuth()
  const [record, setRecord] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!patientId) return
    const controller = new AbortController()
    service.load(patientId, getAccessToken(), controller.signal).then(patient => {
      if (!controller.signal.aborted) setRecord({ status: 'ready', patient })
    }).catch((failure: unknown) => {
      if (controller.signal.aborted) return
      const error = failure instanceof PatientProfileError ? failure : new PatientProfileError('server')
      setRecord({ status: 'error', error })
    })
    return () => controller.abort()
  }, [attempt, getAccessToken, patientId, service])

  const error = patientId === null ? new PatientProfileError('missing') : record.status === 'error' ? record.error : null
  function retry() {
    document.getElementById('page-title')?.focus()
    setRecord({ status: 'loading' })
    setAttempt(current => current + 1)
  }

  return <section className="intro registration patient-profile" aria-labelledby="page-title">
    <div className="page-heading">
      <h1 id="page-title" tabIndex={-1}>Edit patient profile</h1>
      {record.status === 'ready' && <p className="muted">{record.patient.firstName} {record.patient.lastName}</p>}
    </div>
    {patientProfileMockEnabled && <Alert tone="warning">Demo profile editing is active. Records are synthetic and temporary.</Alert>}
    {!service.canUpdate && <Alert tone="warning">{new PatientProfileError('unavailable').message}</Alert>}
    <Card className={record.status === 'ready' && !error ? 'form-surface' : undefined}>
      {error ? <div className="profile-actions">
        <Alert tone="error">{error.message}</Alert>
        <div className="profile-buttons">
          {(error.code === 'network' || error.code === 'server') && <Button onClick={retry}>Try again</Button>}
          <SearchLink>Back to search</SearchLink>
        </div>
      </div> : record.status === 'loading' ? <p className="resource-loading" role="status">Loading patient profile…</p>
        : record.status === 'ready' && <ProfileForm {...props} initial={record.patient} />}
    </Card>
  </section>
}

// Kaydedilmiş veri ve taslak ayrı tutulur. Başarısız kayıtta kullanıcı girdileri korunur.
function ProfileForm({ initial, service, onDirtyChange, onSaved }: ProfileProps & { initial: Patient }) {
  const { getAccessToken } = useAuth()
  const [patient, setPatient] = useState(initial)
  const [draft, setDraft] = useState(() => draftFromPatient(initial))
  const [includeRelative, setIncludeRelative] = useState(Boolean(initial.relativeInformation))
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [saving, setSaving] = useState(false)
  const [saver] = useState(() => createProfileSaver(service))
  const form = useRef<HTMLFormElement>(null)
  const notice = useRef<HTMLDivElement>(null)
  const focusAfterRender = useRef<'field' | 'notice' | null>(null)
  const validation = validateProfile(draft, includeRelative)
  const fieldsFeedback = useFieldFeedback<PatientDetailsField>('errors' in validation ? validation.errors : {})
  const { errors } = fieldsFeedback
  const changed = isProfileDirty(patient, draft, includeRelative)
  const advanced = draft.conditionLevel === 'ADVANCED'
  const hasRelative = Boolean(patient.relativeInformation)
  const disabled = saving || !service.canUpdate
  useUnsavedPatientForm(service.canUpdate && (saving || changed), onDirtyChange)
  useEffect(() => () => saver.cancel(), [saver])

  useLayoutEffect(() => {
    if (saving || !focusAfterRender.current) return
    const target = focusAfterRender.current === 'field'
      ? form.current?.querySelector<HTMLElement>('[aria-invalid="true"]') : notice.current
    focusAfterRender.current = null
    target?.focus()
  }, [saving, errors, feedback])

  // Advanced seçilince saklanan yakın bilgileri yeniden kullanılır ve grup zorunlu olur.
  function update<Field extends PatientDetailsField>(field: Field, value: PatientDetailsDraft[Field]) {
    if (field === 'conditionLevel' && value === 'ADVANCED') setIncludeRelative(true)
    setDraft(current => ({ ...current, [field]: value }))
    fieldsFeedback.clearField(field)
    setFeedback(null)
  }

  // Başarı yalnızca servis doğru hastayı döndürdüğünde gösterilir ve arama yeniden çalıştırılır.
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!service.canUpdate || !changed || saver.pending()) return
    setFeedback(null)
    fieldsFeedback.submit()
    if ('errors' in validation) {
      focusAfterRender.current = 'field'
      return
    }

    setSaving(true)
    const saved = await saver.save(patient, validation.payload, getAccessToken())
    if (!saved) return
    setSaving(false)
    focusAfterRender.current = 'notice'
    if ('error' in saved) {
      // 400 alan hataları görünürse, global hata yerine ilk hatalı kontrole odaklanırız.
      if (saved.fieldErrors) {
        fieldsFeedback.setServer(saved.fieldErrors)
        focusAfterRender.current = 'field'
      }
      setFeedback({ tone: 'error', message: saved.error })
      return
    }
    fieldsFeedback.reset()
    setPatient(saved.patient)
    setDraft(draftFromPatient(saved.patient))
    setIncludeRelative(Boolean(saved.patient.relativeInformation))
    setFeedback({ tone: 'info', message: 'Patient profile saved.' })
    onSaved(saved.patient)
  }

  const fields = { prefix: 'profile', draft, errors, disabled, onChange: update, onBlur: fieldsFeedback.touch }
  return <>
    {feedback && <div ref={notice} tabIndex={-1} className="registration-feedback">
      <Alert tone={feedback.tone}>{feedback.message}</Alert>
    </div>}
    <form ref={form} className="registration-form" autoComplete="off" noValidate onSubmit={submit} aria-busy={saving}>
      <PatientDetailFields {...fields} identity={<dl className="profile-identity">
        <dt>T.C. Kimlik No (read only)</dt>
        <dd>{patient.nationalId}</dd>
      </dl>} />
      {/* Mevcut yakın yalnızca açıkça kaldırma seçilirse güncellemeden çıkarılır. */}
      {!advanced && service.canUpdate && <label className="registration-checkbox">
        <input type="checkbox" disabled={saving} checked={hasRelative ? !includeRelative : includeRelative}
          onChange={event => {
            setIncludeRelative(hasRelative ? !event.target.checked : event.target.checked)
            setFeedback(null)
          }} />
        {hasRelative ? 'Remove relative contact' : 'Add relative contact (optional)'}
      </label>}
      {!advanced && hasRelative && !includeRelative && <p className="muted">Saving will remove this relative contact.</p>}
      {(advanced || includeRelative) && <PatientRelativeFields {...fields} advanced={advanced} />}
      <div className="registration-actions profile-buttons">
        <Button type="submit" disabled={disabled || !changed} loading={saving}>{saving ? 'Saving…' : 'Save changes'}</Button>
        <SearchLink>{feedback?.tone === 'info' || !service.canUpdate ? 'Back to search' : 'Cancel'}</SearchLink>
        <span className="sr-only" role="status">{saving ? 'Saving patient profile.' : ''}</span>
      </div>
    </form>
  </>
}
