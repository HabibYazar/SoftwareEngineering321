import { useEffect, useLayoutEffect, useRef, useState, type FormEvent } from 'react'
import { hasPermission } from '../auth/permissions'
import { useAuth } from '../auth/AuthProvider'
import { Alert, Button, Card, TextField } from '../components/ui'
import { PatientDetailFields, PatientRelativeFields } from './PatientDetailFields'
import { finishRegistration, type RegistrationEntry } from './registrationAttempt'
import { RegistrationSuccess } from './RegistrationSuccess'
import { registrationMockEnabled } from './service'
import { RegistrationError, type Patient, type PatientRegistrationService } from './types'
import { useFieldFeedback } from './useFieldFeedback'
import { useUnsavedPatientForm } from './useUnsavedPatientForm'
import { emptyRegistration, validateRegistration, type RegistrationDraft, type RegistrationField } from './validation'

interface PatientRegistrationPageProps {
  service: PatientRegistrationService
  onDirtyChange: (dirty: boolean) => void
  onRegistered: (patient: Patient) => void
}

type Feedback = { tone: 'info' | 'error'; message: string }

// Resepsiyon görevlisi için form durumu yalnızca bu ekran açıkken bellekte tutulur.
export function PatientRegistrationPage({ service, onDirtyChange, onRegistered }: PatientRegistrationPageProps) {
  const { getAccessToken, role } = useAuth()
  const [entry, setEntry] = useState<RegistrationEntry>({
    draft: emptyRegistration, includeRelative: false, registered: null,
  })
  const { draft, includeRelative, registered } = entry
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [saving, setSaving] = useState(false)
  const inFlight = useRef<AbortController | null>(null)
  const form = useRef<HTMLFormElement>(null)
  const notice = useRef<HTMLDivElement>(null)
  const focusAfterRender = useRef<'invalid' | 'notice' | 'first' | null>(null)

  const validation = validateRegistration(draft, includeRelative)
  const fieldsFeedback = useFieldFeedback<RegistrationField>('errors' in validation ? validation.errors : {})
  const { errors } = fieldsFeedback
  const advanced = draft.conditionLevel === 'ADVANCED'
  const showRelative = advanced || includeRelative
  const dirty = saving || includeRelative || Object.values(draft).some(value => value !== '')
  useUnsavedPatientForm(dirty, onDirtyChange)

  useEffect(() => () => inFlight.current?.abort(), [])

  useLayoutEffect(() => {
    // Sunucu hatasından sonra, devre dışı alanlar yeniden etkinleşince odağı taşır.
    if (saving || !focusAfterRender.current) return
    const target = focusAfterRender.current === 'invalid'
      ? form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')
      : focusAfterRender.current === 'first' ? form.current?.querySelector<HTMLInputElement>('input') : notice.current
    focusAfterRender.current = null
    target?.focus()
  }, [errors, feedback, registered, saving])

  function update<Field extends RegistrationField>(field: Field, value: RegistrationDraft[Field]) {
    const keepContact = field === 'conditionLevel' && advanced && value !== 'ADVANCED'
      && [draft.relativeFullName, draft.relativeRelationship, draft.relativePhone].some(part => part !== '')
    setEntry(current => ({
      ...current, draft: { ...current.draft, [field]: value }, includeRelative: keepContact || current.includeRelative,
    }))
    fieldsFeedback.clearField(field)
    setFeedback(null)
  }

  // Önce istemci doğrulaması yapılır. Ref, ikinci tıklamanın aynı POST'u başlatmasını engeller.
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (inFlight.current) return
    setFeedback(null)
    fieldsFeedback.submit()
    if ('errors' in validation) {
      focusAfterRender.current = 'invalid'
      return
    }

    const controller = new AbortController()
    inFlight.current = controller
    setSaving(true)
    const result = await finishRegistration(service, entry, validation.payload, getAccessToken(), controller.signal)
    inFlight.current = null
    if (!result) return
    setSaving(false)
    setEntry(result.entry)
    if ('patient' in result) {
      // Başarı kartı ve oturum listesi yalnızca doğrulanmış POST yanıtından beslenir.
      fieldsFeedback.reset()
      focusAfterRender.current = 'notice'
      onRegistered(result.patient)
      return
    }

    const { error } = result
    if (error instanceof RegistrationError && error.code === 'duplicate') {
      focusAfterRender.current = 'invalid'
      fieldsFeedback.setServer({ nationalId: error.message })
    } else {
      // Resmî Problem.errors içindeki bilinen alanlar, mevcut form odak mekanizmasını kullanır.
      const serverFields = error instanceof RegistrationError ? error.fieldErrors : {}
      fieldsFeedback.setServer(serverFields)
      focusAfterRender.current = Object.keys(serverFields).length ? 'invalid' : 'notice'
      setFeedback({
        tone: 'error',
        message: error instanceof RegistrationError ? error.message : 'Registration could not be confirmed.',
      })
    }
  }

  const fields = { prefix: 'registration', draft, errors, onChange: update, onBlur: fieldsFeedback.touch, disabled: saving }
  return <section className="intro registration" aria-labelledby="page-title">
    <div className="page-heading">
      <h1 id="page-title" tabIndex={-1}>Register a patient</h1>
      <p className="muted">Enter the patient’s details using synthetic data only.</p>
    </div>
    {registrationMockEnabled && <Alert tone="warning">Demo registration is active. Records are synthetic and temporary.</Alert>}
    {registered ? <RegistrationSuccess patient={registered} canEdit={hasPermission(role, 'editPatients')}
      noticeRef={notice} onRegisterAnother={() => { focusAfterRender.current = 'first'; setEntry(current => ({ ...current, registered: null })) }} /> : <Card className="form-surface">
      {feedback && <div ref={notice} tabIndex={-1} className="registration-feedback">
        <Alert tone={feedback.tone}>{feedback.message}</Alert>
      </div>}
      <form ref={form} className="registration-form" autoComplete="off" onSubmit={submit} noValidate aria-busy={saving}>
        {/* Uzun yapıştırmaları kesmeyiz. Kullanıcı, tüm girdiyi görüp hane hatasını düzeltebilir. */}
        <PatientDetailFields {...fields} identity={<TextField id="registration-nationalId"
          name="nationalId" label="Synthetic national ID" inputMode="numeric"
          required value={draft.nationalId} error={errors.nationalId} onBlur={() => fieldsFeedback.touch('nationalId')}
          onChange={event => update('nationalId', event.target.value)} />} />
        {!advanced && <label className="registration-checkbox">
          <input type="checkbox" checked={includeRelative} disabled={saving}
            onChange={event => { const checked = event.target.checked; setEntry(current => ({ ...current, includeRelative: checked })); setFeedback(null) }} />
          Add relative contact (optional)
        </label>}
        {showRelative && <PatientRelativeFields {...fields} advanced={advanced} />}
        <div className="registration-actions">
          <Button type="submit" disabled={saving} loading={saving}>{saving ? 'Registering…' : 'Register patient'}</Button>
          <span className="sr-only" role="status">{saving ? 'Registration in progress.' : ''}</span>
        </div>
      </form>
    </Card>}
  </section>
}
