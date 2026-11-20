import { homePath, resolvePath } from '../auth/routing.ts'
import type { Role } from '../auth/types.ts'

type Direction = -1 | 1

interface EntryMarker {
  hmsScope: string
  index: number
}

// Tarayıcı işlemleri bu arayüzden geçer. Testler aynı mantığı gerçek sekme açmadan çalıştırır.
interface BrowserHistory {
  read(): { path: string; state: unknown }
  replace(path: string, marker: EntryMarker): void
  push(path: string, marker: EntryMarker): void
  go(delta: number): void
}

export interface NavigationSnapshot {
  path: string
  canGoBack: boolean
  canGoForward: boolean
}

// Yalnızca bu belgenin aynı oturumda eklediği art arda kayıtlar, uygulama düğmeleriyle gezilebilir.
export function createAppHistory(browser: BrowserHistory, confirmLeave = () => true) {
  let scope = ''
  let role: Role | null = null
  let paths: string[] = []
  let index = 0
  let traversing = false
  let restoring = false

  function ownedIndex({ path, state }: ReturnType<BrowserHistory['read']>) {
    if (typeof state !== 'object' || state === null) return -1
    const marker = state as Partial<EntryMarker>
    return marker.hmsScope === scope && typeof marker.index === 'number'
      && Number.isInteger(marker.index) && paths[marker.index] === path
      ? marker.index : -1
  }

  function canMove(direction: Direction) {
    const target = paths[index + direction]
    return !traversing && target !== undefined && resolvePath(target, role) === target
  }

  function snapshot(): NavigationSnapshot {
    return { path: paths[index], canGoBack: canMove(-1), canGoForward: canMove(1) }
  }

  // Bilinmeyen bir kayıtta yeni bir başlangıç işaretlenir. Eski kayda doğru tahmini bir adım atılmaz.
  function start(path: string) {
    scope = Array.from(crypto.getRandomValues(new Uint32Array(4))).join('-')
    paths = [path]
    index = 0
    traversing = false
    restoring = false
    browser.replace(path, { hmsScope: scope, index })
    return snapshot()
  }

  // Tarayıcı geri/ileri olayları ve rol değişimleri aynı rota kontrolünden geçer.
  function sync(nextRole: Role | null): NavigationSnapshot {
    const current = browser.read()
    if (!scope) {
      role = nextRole
      return start(resolvePath(current.path, role))
    }
    if (nextRole !== role) {
      role = nextRole
      return start(role ? homePath(role) : '/login')
    }

    const path = resolvePath(current.path, role)
    const position = ownedIndex(current)
    if (restoring && position !== index) return snapshot()

    const alreadyConfirmed = traversing
    restoring = false
    traversing = false
    // Yerel tarayıcı hareketi reddedilirse formu kaldırmadan bilinen konumu geri yükler.
    if (!alreadyConfirmed && path !== paths[index] && !confirmLeave()) {
      if (position === -1) return start(paths[index])
      restoring = true
      traversing = true
      browser.go(index - position)
      return snapshot()
    }
    if (position === -1 || current.path !== path) return start(path)
    index = position
    return snapshot()
  }

  // Yeni bir bağlantı, tarayıcının doğal davranışıyla aynı şekilde ileri kayıtlarını keser.
  function navigate(path: string) {
    if (traversing) return snapshot()
    if (!scope || ownedIndex(browser.read()) !== index) sync(role)
    if (resolvePath(path, role) !== path || paths[index] === path || !confirmLeave()) return snapshot()

    const nextIndex = index + 1
    browser.push(path, { hmsScope: scope, index: nextIndex })
    paths = [...paths.slice(0, nextIndex), path]
    index = nextIndex
    return snapshot()
  }

  // Geçiş tamamlanana kadar ikinci bir adım kuyruğa alınmaz. Tamamlanınca popstate konumu günceller.
  function move(direction: Direction) {
    if (traversing) return snapshot()
    if (!scope || ownedIndex(browser.read()) !== index) return sync(role)
    if (canMove(direction) && confirmLeave()) {
      traversing = true
      browser.go(direction)
    }
    return snapshot()
  }

  return { sync, navigate, move }
}
