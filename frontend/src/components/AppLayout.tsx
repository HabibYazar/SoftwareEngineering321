import type { MouseEvent, ReactNode } from 'react'
import { homePath, roleLabels } from '../auth/routing'
import type { Role } from '../auth/types'
import { ThemeToggle } from '../theme/ThemeToggle'
import { NavigationControls } from './NavigationControls'
import { Sidebar } from './Sidebar'
import { Icon } from './ui'

interface AppLayoutProps {
  role: Role | null
  route: string
  busy: boolean
  onSignOut: () => void
  onNavigate: (path: string) => void
  navigation: Parameters<typeof NavigationControls>[0]
  children: ReactNode
}

export function AppLayout({ children, navigation, onNavigate, ...sidebar }: AppLayoutProps) {
  function handleLinkClick(event: MouseEvent<HTMLDivElement>) {
    if (event.defaultPrevented || event.button !== 0 || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return
    if (!(event.target instanceof Element)) return
    const link = event.target.closest<HTMLAnchorElement>('a[href^="#/"]')
    if (!link || (link.target && link.target !== '_self') || link.hasAttribute('download')) return

    event.preventDefault()
    onNavigate(link.hash.slice(1))
  }

  const { role, route } = sidebar
  const workspace = role && homePath(role)
  const showHistory = role || navigation.canGoBack || navigation.canGoForward || navigation.onRefresh

  return <div className={`shell${role ? '' : ' shell--guest'}`} onClick={handleLinkClick}>
    <a className="skip-link" href="#main-content" onClick={event => {
      event.preventDefault()
      document.getElementById('main-content')?.focus()
    }}>Skip to main content</a>
    <Sidebar {...sidebar} />
    <header className="workspace-bar">
      {role && <div className="workspace-context">
        {route === workspace ? <><Icon name="home" /> <strong>Home</strong></> : <a href={'#' + workspace}><Icon name="home" /> Home</a>}
        {route !== workspace && <span aria-hidden="true">/</span>}
        {route !== workspace && <strong>{roleLabels[role]} workspace</strong>}
      </div>}
      <div className="workspace-tools">
        {showHistory && <NavigationControls {...navigation} />}
        <ThemeToggle />
      </div>
    </header>
    <main className="main-content" id="main-content" tabIndex={-1}>
      <div className="workspace-content">{children}</div>
    </main>
  </div>
}
