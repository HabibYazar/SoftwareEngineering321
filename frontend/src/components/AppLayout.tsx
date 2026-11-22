import type { MouseEvent, ReactNode } from 'react'
import { roleLabels } from '../auth/routing'
import type { Role } from '../auth/types'
import { NavigationControls } from './NavigationControls'
import { Sidebar } from './Sidebar'

interface AppLayoutProps {
  role: Role | null
  route: string
  busy: boolean
  onSignOut: () => void
  onNavigate: (path: string) => void
  navigation: Parameters<typeof NavigationControls>[0]
  children: ReactNode
}

// Giriş, doktor listesi ve rol sayfaları aynı yan menüyü, sayfa eylemlerini ve içerik alanını paylaşır.
export function AppLayout({ children, navigation, onNavigate, ...sidebar }: AppLayoutProps) {
  // Normal rota bağlantıları kontrollü geçmişe alınır. Yeni sekme ve değiştirilmiş tıklamalar tarayıcıya kalır.
  function handleLinkClick(event: MouseEvent<HTMLDivElement>) {
    if (event.defaultPrevented || event.button !== 0 || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return
    if (!(event.target instanceof Element)) return
    const link = event.target.closest<HTMLAnchorElement>('a[href^="#/"]')
    if (!link || (link.target && link.target !== '_self') || link.hasAttribute('download')) return

    event.preventDefault()
    onNavigate(link.hash.slice(1))
  }

  return <div className={`shell${sidebar.role ? '' : ' shell--guest'}`} onClick={handleLinkClick}>
    <a className="skip-link" href="#main-content" onClick={event => {
      // Hash yönlendirmesini değiştirmeden klavye odağını içeriğe taşır.
      event.preventDefault()
      document.getElementById('main-content')?.focus()
    }}>Skip to main content</a>
    <Sidebar {...sidebar} />
    <main className="main-content" id="main-content" tabIndex={-1}>
      {/* Üst araç çubuğu mevcut geçmiş denetimlerini içerikten ayırır. */}
      <div className="workspace-bar">
        {sidebar.role && <span className="workspace-context">Workspace <span aria-hidden="true">/</span> <strong>{roleLabels[sidebar.role]}</strong></span>}
        <NavigationControls {...navigation} />
      </div>
      <div className="workspace-content">{children}</div>
    </main>
  </div>
}
