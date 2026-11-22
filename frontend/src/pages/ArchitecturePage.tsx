import { useEffect, useState } from 'react'
import { Alert, Card, Icon } from '../components/ui'
type Status = 'checking' | 'online' | 'offline'
const modules = [
  'Patient Management', 'Appointment Management', 'Doctor & Staff Management',
  'Outpatient Management', 'Electronic Medical Records', 'Laboratory Management',
  'Inpatient & Bed Management', 'Authentication / Authorization / Audit',
]
// Ortak Refresh düğmesi yalnızca sağlık isteğini yineler. Önceki istek iptal edilir.
export function ArchitecturePage({ refreshVersion }: { refreshVersion: number }) {
  const [check, setCheck] = useState<{ version: number; status: Status } | null>(null)
  const status = check?.version === refreshVersion ? check.status : 'checking'

  useEffect(() => {
    const controller = new AbortController()

    function finish(status: Status) {
      if (!controller.signal.aborted) setCheck({ version: refreshVersion, status })
    }

    fetch('/api/v1/health', { cache: 'no-store', signal: controller.signal })
      .then(response => response.ok ? response.json() : Promise.reject(new Error('Unavailable')))
      .then(data => finish(data?.status === 'UP' ? 'online' : 'offline'))
      .catch(() => finish('offline'))
    return () => controller.abort()
  }, [refreshVersion])
  return <>
    <section className="intro" aria-labelledby="page-title">
      <div className="page-heading">
        <h1 id="page-title" tabIndex={-1}>Architecture foundation</h1>
        <p className="muted">Modular monolith · Spring Boot · React · PostgreSQL · Docker Compose</p>
      </div>
      <Alert tone="warning"><strong>Educational prototype only.</strong> Some clinical workflows are available only as demonstrations. Do not use real patient data or deploy to a hospital.</Alert>
      <p className="status" role="status"><span aria-hidden="true" className={`dot ${status}`} />Backend: {status === 'checking' ? 'Checking…' : status === 'online' ? 'Health endpoint online' : 'Unavailable'}</p>
    </section>
    <section aria-labelledby="modules-title"><h2 id="modules-title">Planned Community modules</h2>
      {/* Modül kartları mimari kapsamı gösterir. Ekranların erişilebilirliği rol menüsünde belirlenir. */}
      <div className="grid">{modules.map(name => <Card key={name} className="architecture-tile">
        <span className="doctor-icon"><Icon name="department" /></span>
        <h3>{name}</h3>
      </Card>)}</div>
    </section>
    <footer>Sprint 1 deliverables: proposal · vision &amp; boundary · OpenAPI · RACI · Git-Flow · backlog</footer>
  </>
}
