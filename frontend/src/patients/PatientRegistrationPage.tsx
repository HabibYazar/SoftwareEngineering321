import { useEffect, useLayoutEffect, useRef, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthProvider'
import { Alert, Button, Card, TextField } from '../components/ui'
import { PatientDetailFields, PatientRelativeFields } from './PatientDetailFields'
import { registrationMockEnabled } from './service'
import { RegistrationError, type PatientRegistrationService } from './types'
import { useUnsavedPatientForm } from './useUnsavedPatientForm'
import { emptyRegistration, validateRegistration, type RegistrationDraft, type RegistrationErrors, type RegistrationField } from './validation'

interface PatientRegistrationPageProps {
  service: PatientRegistrationService
  onDirtyChange: (dirty: boolean) => void
}

type Feedback = { tone: 'info' | 'error'; message: string }

// Resepsiyon görevlisi için form durumu yalnızca bu ekran açıkken bellekte tutulur.
export function PatientRegistrationPage({ service, onDirtyChange }: PatientRegistrationPageProps) {
  const { getAccessToken } = useAuth()
  const [draft, setDraft] = useState(emptyRegistration)
  const [includeRelative, setIncludeRelative] = useState(false)
  const [errors, setErrors] = useState<RegistrationErrors>({})
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [saving, setSaving] = useState(false)
  const inFlight = useRef<AbortController | null>(null)
  const form = useRef<HTMLFormElement>(null)
  const notice = useRef<HTMLDivElement>(null)
  const focusAfterRender = useRef<'invalid' | 'notice' | null>(null)

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
      : notice.current
    focusAfterRender.current = null
    target?.focus()
  }, [errors, feedback, saving])

  function update<Field extends RegistrationField>(field: Field, value: RegistrationDraft[Field]) {
    if (field === 'conditionLevel' && advanced && value !== 'ADVANCED'
      && [draft.relativeFullName, draft.relativeRelationship, draft.relativePhone].some(part => part !== '')) {
      setIncludeRelative(true)
    }
    setDraft(current => ({ ...current, [field]: value }))
    setErrors(current => ({ ...current, [field]: undefined }))
    setFeedback(null)
  }

  // Önce istemci doğrulaması yapılır. Ref, ikinci tıklamanın aynı POST'u başlatmasını engeller.
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (inFlight.current) return
    setFeedback(null)
    const validation = validateRegistration(draft, includeRelative)
    if ('errors' in validation) {
      focusAfterRender.current = 'invalid'
      setErrors(validation.errors)
      return
    }

    setErrors({})
    const controller = new AbortController()
    inFlight.current = controller
    setSaving(true)
    try {
      await service.register(validation.payload, getAccessToken(), controller.signal)
      if (controller.signal.aborted) return
      setDraft(emptyRegistration)
      setIncludeRelative(false)
      focusAfterRender.current = 'notice'
      setFeedback({ tone: 'info', message: 'Patient registered successfully.' })
    } catch (error: unknown) {
      if (controller.signal.aborted) return
      if (error instanceof RegistrationError && error.code === 'duplicate') {
        focusAfterRender.current = 'invalid'
        setErrors({ nationalId: error.message })
      } else {
        // Resmî Problem.errors içindeki bilinen alanlar, mevcut form odak mekanizmasını kullanır.
        const serverFields = error instanceof RegistrationError ? error.fieldErrors : {}
        setErrors(serverFields)
        focusAfterRender.current = Object.keys(serverFields).length ? 'invalid' : 'notice'
        setFeedback({
          tone: 'error',
          message: error instanceof RegistrationError ? error.message : 'Registration could not be confirmed.',
        })
      }
    } finally {
      inFlight.current = null
      if (!controller.signal.aborted) setSaving(false)
    }
  }

  const fields = { prefix: 'registration', draft, errors, onChange: update, disabled: saving }
  return <section className="intro registration" aria-labelledby="page-title">
    <div className="page-heading">
      <h1 id="page-title" tabIndex={-1}>Register a patient</h1>
      <p className="muted">Enter the patient’s details using synthetic data only.</p>
    </div>
    {registrationMockEnabled && <Alert tone="warning">Demo registration is active. Records are synthetic and temporary.</Alert>}
    <Card>
      {feedback && <div ref={notice} tabIndex={-1} className="registration-feedback">
        <Alert tone={feedback.tone}>{feedback.message}</Alert>
      </div>}
      <form ref={form} className="registration-form" autoComplete="off" onSubmit={submit} noValidate aria-busy={saving}>
        {/* Kimlik numarası yalnızca yeni hasta kaydında düzenlenebilir. */}
        <PatientDetailFields {...fields} identity={<TextField id="registration-nationalId"
          name="nationalId" label="Synthetic national ID" maxLength={11} inputMode="numeric"
          required value={draft.nationalId} error={errors.nationalId}
          onChange={event => update('nationalId', event.target.value)} />} />
        {!advanced && <label className="registration-checkbox">
          <input type="checkbox" checked={includeRelative} disabled={saving}
            onChange={event => { setIncludeRelative(event.target.checked); setFeedback(null) }} />
          Add relative contact (optional)
        </label>}
        {showRelative && <PatientRelativeFields {...fields} advanced={advanced} />}
        <div className="registration-actions">
          <Button type="submit" disabled={saving}>{saving ? 'Registering…' : 'Register patient'}</Button>
          <span className="sr-only" role="status">{saving ? 'Registration in progress.' : ''}</span>
        </div>
      </form>
    </Card>
  </section>
}