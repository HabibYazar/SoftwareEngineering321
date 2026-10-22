import { useEffect, useState } from 'react'

type Status = 'checking' | 'online' | 'offline'
const modules = [
  'Patient Management', 'Appointment Management', 'Doctor & Staff Management',
  'Outpatient Management', 'Electronic Medical Records', 'Laboratory Management',
  'Inpatient & Bed Management', 'Authentication / Authorization / Audit'
]

function App() {
  const [status, setStatus] = useState<Status>('checking')
  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/v1/health', { signal: controller.signal })
      .then(r => r.ok ? r.json() : Promise.reject(new Error('Unavailable')))
      .then(data => setStatus(data.status === 'UP' ? 'online' : 'offline'))
      .catch(() => { if (!controller.signal.aborted) setStatus('offline') })
    return () => controller.abort()
  }, [])
  return <main className="shell">
    <header><span className="mark">H+</span><span>HMS <strong>Community</strong></span><span className="course">SENG 321 · Sprint 1 scaffold</span></header>
    <section className="intro">
      <p className="eyebrow">OPEN-SOURCE HOSPITAL SYSTEM</p>
      <h1>Architecture foundation</h1>
      <p className="muted">Modular monolith · Spring Boot · React · PostgreSQL · Docker Compose</p>
      <div className="notice"><strong>Educational prototype only.</strong> All clinical modules are design placeholders. Do not use real patient data or deploy to a hospital.</div>
      <p className="status"><span className={`dot ${status}`} /> Backend: {status === 'checking' ? 'Checking…' : status === 'online' ? 'Health endpoint online' : 'Unavailable'}</p>
    </section>
    <section><h2>Planned Community modules</h2><div className="grid">{modules.map((name,i) => <article key={name}><small>{String(i+1).padStart(2,'0')}</small><h3>{name}</h3><p>Architecture defined · feature not implemented</p></article>)}</div></section>
    <footer>Sprint 1 deliverables: proposal · vision & boundary · OpenAPI · RACI · Git-Flow · backlog</footer>
  </main>
}
export default App
