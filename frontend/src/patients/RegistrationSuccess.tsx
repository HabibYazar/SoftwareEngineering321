import type { Ref } from 'react'
import { patientEditPath, patientSearchPath } from '../auth/routing'
import { Alert, Button, Card, Icon } from '../components/ui'
import type { Patient } from './types'

// Kimlik, adres ve telefon kartta gösterilmez. Düzenleme yolu doğrudan POST'un döndürdüğü UUID'yi kullanır.
export function RegistrationSuccess({ patient, canEdit, noticeRef, onRegisterAnother }: {
  patient: Patient; canEdit: boolean; noticeRef?: Ref<HTMLDivElement>; onRegisterAnother: () => void
}) {
  return <Card className="registration-success">
    <div ref={noticeRef} tabIndex={-1}>
      <Alert>Patient registered successfully.</Alert>
      <div className="registration-summary">
        <span className="doctor-icon"><Icon name="check" /></span>
        <div><h2>{patient.firstName} {patient.lastName}</h2>
          <p className="muted">Condition level: {patient.conditionLevel.toLowerCase()}</p></div>
      </div>
    </div>
    <div className="registration-actions">
      {canEdit && <a className="button button--primary" href={'#' + patientEditPath(patient.id)}>Edit Profile</a>}
      <Button variant="secondary" onClick={onRegisterAnother}>Register Another Patient</Button>
      <a className="button button--secondary" href={'#' + patientSearchPath}>Search Patients</a>
    </div>
  </Card>
}
