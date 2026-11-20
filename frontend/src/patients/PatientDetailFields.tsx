import type { ReactNode } from 'react'
import { TextField } from '../components/ui'
import { fieldLimits, type PatientDetailsDraft, type PatientDetailsErrors, type PatientDetailsField } from './validation'
import './registration.css'

interface FieldsProps {
  prefix: string
  draft: PatientDetailsDraft
  errors: PatientDetailsErrors
  disabled: boolean
  onChange<Field extends PatientDetailsField>(field: Field, value: PatientDetailsDraft[Field]): void
}

type TextInput = Exclude<PatientDetailsField, 'conditionLevel' | 'address'>

// Ortak girişler, kayıt ve düzenlemede aynı sınırları, etiketleri ve hata bağlantılarını kullanır.
function patientField({ prefix, draft, errors, onChange }: FieldsProps, field: TextInput, label: string, type = 'text') {
  return <TextField id={prefix + '-' + field} name={field} label={label}
    type={type} maxLength={field in fieldLimits ? fieldLimits[field as keyof typeof fieldLimits] : undefined}
    required value={draft[field]} error={errors[field]}
    onChange={event => onChange(field, event.target.value)} />
}

// Kimlik alanını çağıran ekran sağlar. Kayıt düzenlenebilir giriş, profil ise salt okunur metin gösterir.
export function PatientDetailFields(props: FieldsProps & { identity: ReactNode }) {
  const { prefix, draft, errors, disabled, onChange, identity } = props
  const addressId = prefix + '-address'
  const conditionId = prefix + '-condition'
  return <fieldset disabled={disabled}>
    <legend>Patient details</legend>
    <div className="registration-fields">
      {patientField(props, 'firstName', 'First name')}
      {patientField(props, 'lastName', 'Last name')}
      {identity}
      {patientField(props, 'dateOfBirth', 'Date of birth', 'date')}
      {patientField(props, 'gender', 'Gender')}
      {patientField(props, 'phone', 'Phone number', 'tel')}
      <div className="field registration-wide">
        <label htmlFor={addressId}>Address</label>
        <textarea id={addressId} name="address" className="text-field" rows={2}
          maxLength={fieldLimits.address} required value={draft.address}
          aria-invalid={Boolean(errors.address)} aria-describedby={errors.address ? addressId + '-error' : undefined}
          onChange={event => onChange('address', event.target.value)} />
        {errors.address && <span id={addressId + '-error'} className="field-error">{errors.address}</span>}
      </div>
      <div className="field">
        <label htmlFor={conditionId}>Condition level</label>
        <select id={conditionId} name="conditionLevel" className="text-field" required value={draft.conditionLevel}
          aria-invalid={Boolean(errors.conditionLevel)} aria-describedby={errors.conditionLevel ? conditionId + '-error' : undefined}
          onChange={event => onChange('conditionLevel', event.target.value as PatientDetailsDraft['conditionLevel'])}>
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
      {patientField(props, 'relativePhone', 'Relative’s phone number', 'tel')}
    </div>
  </fieldset>
}