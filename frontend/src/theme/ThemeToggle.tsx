import { useEffect, useState } from 'react'
import { Button, Icon } from '../components/ui'
import { applyTheme, readThemePreference, saveThemePreference, systemThemeQuery, type Theme } from './preference'

// Tercih yokken işletim sistemini izler. İki düğme etkin temayı aria-pressed ile belirtir.
export function ThemeToggle() {
  const [preference, setPreference] = useState(readThemePreference)
  const [theme, setTheme] = useState<Theme>(() => document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light')

  useEffect(() => {
    if (preference !== null) return
    const media = systemThemeQuery()

    function syncSystemTheme() {
      const next = media.matches ? 'dark' : 'light'
      applyTheme(next)
      setTheme(next)
    }

    syncSystemTheme()
    media.addEventListener('change', syncSystemTheme)
    return () => media.removeEventListener('change', syncSystemTheme)
  }, [preference])

  // Seçim köke hemen uygulanır ve sonraki ziyarette kullanılmak üzere saklanır.
  function chooseTheme(next: Theme) {
    applyTheme(next)
    saveThemePreference(next)
    setPreference(next)
    setTheme(next)
  }

  return <div className="theme-toggle" role="group" aria-label="Color theme">
    <Button variant="secondary" aria-pressed={theme === 'light'} onClick={() => chooseTheme('light')}>
      <Icon name="sun" /> Light
    </Button>
    <Button variant="secondary" aria-pressed={theme === 'dark'} onClick={() => chooseTheme('dark')}>
      <Icon name="moon" /> Dark
    </Button>
  </div>
}
