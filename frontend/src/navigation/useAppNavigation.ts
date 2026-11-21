import { useLayoutEffect, useState } from 'react'
import { resolvePath } from '../auth/routing'
import type { Role } from '../auth/types'
import { createAppHistory, type NavigationSnapshot } from './history'

// Hash adresleri korunur. History API'ye yalnızca dahili rota ve konum işareti yazılır.
export function useAppNavigation(role: Role | null, confirmLeave?: () => boolean) {
  const [history] = useState(() => createAppHistory({
    read: () => ({ path: window.location.hash.slice(1), state: window.history.state }),
    replace: (path, marker) => window.history.replaceState(marker, '', '#' + path),
    push: (path, marker) => window.history.pushState(marker, '', '#' + path),
    go: delta => window.history.go(delta),
  }, confirmLeave))
  const [position, setPosition] = useState<NavigationSnapshot>(() => ({
    path: resolvePath(window.location.hash.slice(1), role),
    canGoBack: false,
    canGoForward: false,
  }))

  useLayoutEffect(() => {
    // Yetki değişimi ve geçmiş dönüşü, sayfa çizilmeden önce rotayı tekrar doğrular.
    const sync = () => setPosition(history.sync(role))
    sync()
    const events = ['popstate', 'hashchange', 'pageshow'] as const
    events.forEach(event => window.addEventListener(event, sync))
    return () => events.forEach(event => window.removeEventListener(event, sync))
  }, [history, role])

  return {
    ...position,
    path: resolvePath(position.path, role),
    navigate: (path: string) => setPosition(history.navigate(path)),
    back: () => setPosition(history.move(-1)),
    forward: () => setPosition(history.move(1)),
  }
}
