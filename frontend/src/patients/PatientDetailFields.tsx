import { useState, type ReactNode } from 'react'
import { TextField } from '../components/ui'
import { editTurkishMobile, genderOptions, isSupportedGender } from './formOptions'
import { earliestPatientBirthDate, fieldLimits, type PatientDetailsDraft, type PatientDetailsErrors, type PatientDetailsField } from './validation'
import './registration.css'

interface FieldsProps {
  prefix: string
  draft: PatientDetailsDraft
  errors: PatientDetailsErrors
  disabled: boolean
  onBlur(field: PatientDetailsField): void
  onChange<Field extends PatientDetailsField>(field: Field, value: PatientDetailsDraft[Field]): void
}

type PhoneInput = 'phone' | 'relativePhone'
type TextInput = Exclude<PatientDetailsField, 'conditionLevel' | 'address' | 'gender' | PhoneInput>

// İki form aynı telefon girişini ve erişilebilir hata sunumunu paylaşır.
function PatientPhoneField({ prefix, draft, errors, onChange, onBlur, field, label }:
  FieldsProps & { field: PhoneInput; label: string }) {
  const [inputError, setInputError] = useState<string>()
  const previous = draft[field]

  function applyEdit(input: HTMLInputElement, raw: string, caret: number, inputType?: string) {
    const edit = editTurkishMobile(previous, raw, caret, inputType)
    if ('error' in edit) {
      // Geçersiz yapıştırma mevcut değer ve seçimle kalır. Fazla hane kesilmez.
      if (input.value !== previous) {
        const position = Math.max(0, caret - Math.max(0, raw.length - previous.length))
        input.value = previous
        input.setSelectionRange(position, position)
      }
      setInputError(edit.error)
      return
    }
    if (input.value !== edit.value) input.value = edit.value
    if (input.selectionStart !== edit.caret || input.selectionEnd !== edit.caret) {
      input.setSelectionRange(edit.caret, edit.caret)
    }
    setInputError(undefined)
    onChange(field, edit.value)
  }

  return <TextField id={prefix + '-' + field} name={field} label={label}
    type="tel" inputMode="tel" placeholder="0500 000 00 00" required
    value={previous} error={inputError ?? errors[field]} onBlur={() => onBlur(field)}
    onPaste={event => {
      event.preventDefault()
      const input = event.currentTarget
      const start = input.selectionStart ?? previous.length
      const end = input.selectionEnd ?? start
      const pasted = event.clipboardData.getData('text')
      applyEdit(input, previous.slice(0, start) + pasted + previous.slice(end), start + pasted.length)
    }}
    onChange={event => {
      const input = event.currentTarget
      applyEdit(input, input.value, input.selectionStart ?? input.value.length, (event.nativeEvent as InputEvent).inputType)
    }} />
}

// Ortak girişler, kayıt ve düzenlemede aynı sınırları, etiketleri ve hata bağlantılarını kullanır.
function patientField({ prefix, draft, errors, onChange, onBlur }: FieldsProps, field: TextInput, label: string, type = 'text') {
  return <TextField id={prefix + '-' + field} name={field} label={label}
    type={type} maxLength={field in fieldLimits ? fieldLimits[field as keyof typeof fieldLimits] : undefined}
    required value={draft[field]} error={errors[field]} onBlur={() => onBlur(field)}
    min={type === 'date' ? earliestPatientBirthDate : undefined}
    onChange={event => onChange(field, event.target.value)} />
}

// Kimlik alanını çağıran ekran sağlar. Kayıt düzenlenebilir giriş, profil ise salt okunur metin gösterir.
export function PatientDetailFields(props: FieldsProps & { identity: ReactNode }) {
  const { prefix, draft, errors, disabled, onChange, onBlur, identity } = props
  const addressId = prefix + '-address'
  const conditionId = prefix + '-condition'
  const genderId = prefix + '-gender'
  return <fieldset disabled={disabled}>
    <legend>Patient details</legend>
    <div className="registration-fields">
      {patientField(props, 'firstName', 'First name')}
      {patientField(props, 'lastName', 'Last name')}
      {identity}
      {patientField(props, 'dateOfBirth', 'Date of birth', 'date')}
      {/* Eski sunucu değerini gösteririz. Geçerli seçenek, ancak kullanıcı seçerse taslağa yazılır. */}
      <div className="field">
        <label htmlFor={genderId}>Gender</label>
        <select id={genderId} name="gender" className="text-field" required value={draft.gender}
          aria-invalid={errors.gender ? true : undefined} aria-describedby={errors.gender ? genderId + '-error' : undefined}
          onBlur={() => onBlur('gender')} onChange={event => onChange('gender', event.target.value)}>
          <option value="">Choose a gender option</option>
          {draft.gender && !isSupportedGender(draft.gender) && <option value={draft.gender} disabled>
            Current value: {draft.gender} (choose a supported option)
          </option>}
          {genderOptions.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
        {errors.gender && <span id={genderId + '-error'} className="field-error">{errors.gender}</span>}
      </div>
      <PatientPhoneField {...props} field="phone" label="Phone number" />
      <div className="field registration-wide">
        <label htmlFor={addressId}>Address</label>
        <textarea id={addressId} name="address" className="text-field" rows={2}
          maxLength={fieldLimits.address} required value={draft.address}
          aria-invalid={Boolean(errors.address)} aria-describedby={errors.address ? addressId + '-error' : undefined}
          onBlur={() => onBlur('address')} onChange={event => onChange('address', event.target.value)} />
        {errors.address && <span id={addressId + '-error'} className="field-error">{errors.address}</span>}
      </div>
      <div className="field">
        <label htmlFor={conditionId}>Condition level</label>
        <select id={conditionId} name="conditionLevel" className="text-field" required value={draft.conditionLevel}
          aria-invalid={Boolean(errors.conditionLevel)} aria-describedby={errors.conditionLevel ? conditionId + '-error' : undefined}
          onBlur={() => onBlur('conditionLevel')} onChange={event => onChange('conditionLevel', event.target.value as PatientDetailsDraft['conditionLevel'])}>
          <option value="">Choose a condition level</option>
          <option value="MILD">Mild</option>
          <option value="MODERATE">Moderate</option>
          <option value="ADVANCED">Advanced</option>
        </select>
        {errors.conditionLevel && <span id={conditionId + '-error'} className="field-error">{errors.conditionLevel}</span>}
      </div>
    </div>
  </fieldset>
}

// Yakın bilgileri gösterildiğinde üç alan da birlikte doldurulur.
export function PatientRelativeFields(props: FieldsProps & { advanced: boolean }) {
  return <fieldset disabled={props.disabled}>
    <legend>Relative contact</legend>
    <p className="muted registration-relative-note">{props.advanced
      ? 'Required for the Advanced condition level.'
      : 'Complete all three fields to include this contact.'}</p>
    <div className="registration-fields">
      {patientField(props, 'relativeFullName', 'Relative’s full name')}
      {patientField(props, 'relativeRelationship', 'Relationship')}
      <PatientPhoneField {...props} field="relativePhone" label="Relative’s phone number" />
    </div>
  </fieldset>
}