import { Button, Icon } from './ui'

interface NavigationControlsProps {
  canGoBack: boolean
  canGoForward: boolean
  onBack: () => void
  onForward: () => void
  onRefresh?: () => void
}

// Sayfa eylemleri doğal düğme kullanır. Devre dışı eylemler tıklama veya klavyeyle çalışmaz.
export function NavigationControls({ canGoBack, canGoForward, onBack, onForward, onRefresh }: NavigationControlsProps) {
  return <nav className="page-navigation" aria-label="Page navigation">
    <Button variant="tertiary" disabled={!canGoBack} onClick={onBack}><Icon name="back" /> Back</Button>
    <Button variant="tertiary" disabled={!canGoForward} onClick={onForward}><Icon name="arrow" /> Forward</Button>
    <Button variant="tertiary" disabled={!onRefresh} onClick={onRefresh}><Icon name="refresh" /> Refresh</Button>
  </nav>
}
