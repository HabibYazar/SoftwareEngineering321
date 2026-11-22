import { listWorkflowRoutes, roleLabels } from '../auth/routing'
import type { Role } from '../auth/types'
import { Card, Icon } from '../components/ui'

// Kartlar, yan menüyle aynı izinli iş akışlarını kullanır.
export function WorkspacePage({ role }: { role: Role }) {
  const workflows = listWorkflowRoutes(role)

  return <section className="intro" aria-labelledby="page-title">
    <div className="page-heading">
      <h1 id="page-title" tabIndex={-1}>{roleLabels[role]} home</h1>
    </div>
    {workflows.length ? <div className="grid workspace-grid">
      {workflows.map(({ path, icon, heading, description, label }) => <Card className="workspace-action" key={path}>
        <span className="doctor-icon"><Icon name={icon} /></span>
        <h2>{heading}</h2>
        <p className="muted">{description}</p>
        <a className="button button--primary" href={'#' + path}>{label} <Icon name="arrow" /></a>
      </Card>)}
    </div> : <Card className="workspace-action">
      <h2>You are signed in</h2>
      <p className="muted">Welcome to your {roleLabels[role].toLowerCase()} workspace.</p>
    </Card>}
  </section>
}