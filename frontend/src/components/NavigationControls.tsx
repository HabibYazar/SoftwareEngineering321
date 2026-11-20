import { Button } from './ui'

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
    <Button variant="secondary" disabled={!canGoBack} onClick={onBack}>Back</Button>
    <Button variant="secondary" disabled={!canGoForward} onClick={onForward}>Forward</Button>
    <Button variant="secondary" disabled={!onRefresh} onClick={onRefresh}>Refresh</Button>
  </nav>
}
