import { useRef, useState, type KeyboardEvent } from 'react'
import { homePath, listWorkflowRoutes, roleLabels } from '../auth/routing'
import type { Role } from '../auth/types'
import { ThemeToggle } from '../theme/ThemeToggle'
import { Button, Icon } from './ui'

interface SidebarProps {
  role: Role | null
  route: string
  busy: boolean
  onSignOut: () => void
}

// Masaüstünde sürekli görünür. Dar ekranda aynı dikey menü açılıp kapatılabilir.
export function Sidebar({ role, route, busy, onSignOut }: SidebarProps) {
  const [expanded, setExpanded] = useState(false)
  const menuButton = useRef<HTMLButtonElement>(null)
  const workspace = role ? homePath(role) : '/login'
  const workflows = listWorkflowRoutes(role)

  // Dar ekranda menüyü kapatınca odak gizlenen bir bağlantıda kalmaz.
  function closeMenu() {
    setExpanded(false)
    if (expanded && menuButton.current?.getClientRects().length) menuButton.current.focus()
  }

  function handleMenuKeys(event: KeyboardEvent) {
    if (event.key === 'Escape' && expanded && menuButton.current?.getClientRects().length) {
      event.preventDefault()
      closeMenu()
    }
  }

  return <aside className="sidebar" onKeyDown={handleMenuKeys}>
    <div className="sidebar-heading">
      <a className="brand" href={'#' + workspace} onClick={closeMenu}>
        <span className="mark" aria-hidden="true">H+</span>
        <span className="brand-name">HMS <strong>Community</strong></span>
      </a>
      <button ref={menuButton} type="button" className="menu-toggle button button--secondary" aria-expanded={expanded}
        aria-controls="sidebar-menu" onClick={() => setExpanded(previous => !previous)}>
        <Icon name={expanded ? 'close' : 'menu'} /> Menu
      </button>
    </div>
    <div id="sidebar-menu" className="sidebar-menu" data-expanded={expanded}>
      <nav className="sidebar-nav" aria-label="Main navigation">
        <a href={'#' + workspace} aria-current={route === workspace ? 'page' : undefined} onClick={closeMenu}>
          <Icon name={role ? 'home' : 'signIn'} /> {role ? 'Home' : 'Sign in'}
        </a>
        {/* Bölüm başlığı yalnızca oturumun erişebildiği iş akışları varsa görünür. */}
        {workflows.length > 0 && <span className="menu-caption">Workflows</span>}
        {workflows.map(({ path, label, icon }) =>
          <a key={path} href={'#' + path} aria-current={route === path ? 'page' : undefined} onClick={closeMenu}>
            <Icon name={icon} /> {label}
          </a>)}
        <a className="reference-link" href="#/foundation" aria-current={route === '/foundation' ? 'page' : undefined} onClick={closeMenu}>
          <Icon name="archive" /> Architecture
        </a>
      </nav>
      <div className="sidebar-settings">
        <ThemeToggle />
        {role && <div className="sidebar-session">
          <div className="sidebar-role"><span className="role-mark" aria-hidden="true">{roleLabels[role][0]}</span><strong>{roleLabels[role]}</strong></div>
          <Button variant="tertiary" disabled={busy} onClick={() => {
            closeMenu()
            onSignOut()
          }}>
            <Icon name="signOut" /> Sign out
          </Button>
        </div>}
      </div>
    </div>
  </aside>
}
