import { listWorkflowRoutes, roleLabels } from '../auth/routing'
import type { Role } from '../auth/types'
import { Card, Icon } from '../components/ui'

export function WorkspacePage({ role }: { role: Role }) {
  const workflows = listWorkflowRoutes(role)
  const description = role === 'Administrator' ? 'Manage patient records, demo staff accounts, and audit logs.'
    : role === 'Receptionist' ? 'Register patients, find records, and look up doctors.'
    : 'You are signed in.'

  return <section className="intro workspace" aria-labelledby="page-title">
    <header className="workspace-hero">
      <div>
        <h1 id="page-title" tabIndex={-1}>{roleLabels[role]} workspace</h1>
        <p>{description}</p>
      </div>
      <span className="hero-mark" aria-hidden="true"><Icon name="medical" /></span>
    </header>

    {workflows.length > 0 ? <section className="workspace-actions" aria-labelledby="workflow-title">
      <h2 id="workflow-title">Your tools</h2>
      <ul className="workspace-grid">
        {workflows.map(({ path, icon, description, label }) => <li key={path}>
          <a className="workspace-link" href={'#' + path} data-icon={icon}>
            <span className="feature-icon"><Icon name={icon} /></span>
            <span className="workspace-arrow"><Icon name="arrow" /></span>
            <h3>{label}</h3>
            <p className="muted">{description}</p>
          </a>
        </li>)}
      </ul>
    </section> : <Card className="workspace-empty">
      <span className="feature-icon"><Icon name="archive" /></span>
      <div>
        <h2>Explore HMS Community</h2>
        <p className="muted">See the planned modules and check the backend connection.</p>
      </div>
      <a className="button button--secondary" href="#/foundation">Architecture <Icon name="arrow" /></a>
    </Card>}
  </section>
}
