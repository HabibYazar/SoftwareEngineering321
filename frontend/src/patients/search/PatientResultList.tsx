import { hasPermission } from '../../auth/permissions'
import { patientEditPath } from '../../auth/routing'
import type { Role } from '../../auth/types'
import { Card, Icon } from '../../components/ui'
import type { PatientSummary } from '../recentPatients'

type SearchRecord = PatientSummary & { nationalId?: string }

// Arama ve geçici listeler aynı kartı kullanır. Kimlik numarası yalnızca arama sonucu onu içeriyorsa gösterilir.
export function PatientResultList({ patients, role }: { patients: SearchRecord[]; role: Role | null }) {
  return <ul className="grid patient-result-list">
    {patients.map(patient => <li key={patient.id}>
      <Card className="patient-result-card">
        <div className="patient-result-heading">
          <span className="doctor-icon"><Icon name="patient" /></span>
          <div>
            <h3>{patient.firstName} {patient.lastName}</h3>
            <p className="muted">Condition level: {patient.conditionLevel.toLowerCase()}</p>
          </div>
        </div>
        {patient.nationalId && <p className="muted">National ID <span>{patient.nationalId}</span></p>}
        {hasPermission(role, 'editPatients') && <a className="button button--tertiary"
          href={'#' + patientEditPath(patient.id)}
          aria-label={'Edit profile: ' + patient.firstName + ' ' + patient.lastName}>Edit profile <Icon name="arrow" /></a>}
      </Card>
    </li>)}
  </ul>
}
