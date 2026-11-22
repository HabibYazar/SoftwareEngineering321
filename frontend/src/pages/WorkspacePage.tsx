import { listWorkflowRoutes, roleLabels } from '../auth/routing'
import type { Role } from '../auth/types'
import { Card, Icon } from '../components/ui'

// Öne çıkan eylem ve diğer kartlar aynı izinli rota listesinden gelir.
export function WorkspacePage({ role }: { role: Role }) {
  const [first, ...otherWorkflows] = listWorkflowRoutes(role)

  return <section className="intro workspace" aria-labelledby="page-title">
    <header className="page-heading workspace-heading">
      <span className="feature-icon"><Icon name="home" /></span>
      <h1 id="page-title" tabIndex={-1}>{roleLabels[role]} home</h1>
    </header>
    {/* Bento ızgarası yalnızca sunumu düzenler. İzinli eylemler ve bağlantıları aynı kalır. */}
    {first && <div className="workspace-bento" data-supporting={otherWorkflows.length > 0}>
      <div className="workspace-feature">
        <div className="feature-summary">
          <span className="feature-icon"><Icon name={first.icon} /></span>
          <div><h2>{first.heading}</h2><p>{first.description}</p></div>
        </div>
        <a className="button button--primary" href={'#' + first.path}>{first.label} <Icon name="arrow" /></a>
      </div>
      {otherWorkflows.length > 0 && <section className="workspace-actions" aria-labelledby="workflow-title">
        <h2 id="workflow-title">More workflows</h2>
        <div className="grid workspace-grid">
          {otherWorkflows.map(({ path, icon, heading, description, label }) => <a className="workspace-link" key={path} href={'#' + path} aria-label={label}>
            <span className="doctor-icon"><Icon name={icon} /></span>
            <h3>{heading}</h3>
            <p className="muted">{description}</p>
            <span className="workspace-arrow"><Icon name="arrow" /></span>
          </a>)}
        </div>
      </section>}
    </div>}
    {!first && <Card className="workspace-empty">
      <span className="doctor-icon"><Icon name="check" /></span>
      <div><h2>You are signed in</h2><p className="muted">Welcome to your {roleLabels[role].toLowerCase()} workspace.</p></div>
    </Card>}
  </section>
}
