export type Theme = 'light' | 'dark'

const themeKey = 'hms.theme'

// Tarayıcı depolamayı kapatsa bile tema, sistem tercihiyle çalışmaya devam eder.
export function readThemePreference(): Theme | null {
  try {
    const saved = window.localStorage.getItem(themeKey)
    return saved === 'light' || saved === 'dark' ? saved : null
  } catch {
    return null
  }
}

// Tercih kaydında sadece görsel tema bilgisi tutulur.
export function saveThemePreference(theme: Theme) {
  try {
    window.localStorage.setItem(themeKey, theme)
  } catch {
    // Depolama engellendiğinde seçim açık olan sayfada geçerlidir.
  }
}

// Sistem renk şemasını okuyan medya sorgusu, değişiklikleri de dinlemeye izin verir.
export function systemThemeQuery(): MediaQueryList {
  return window.matchMedia('(prefers-color-scheme: dark)')
}

// Kök öğede yapılan tek değişiklik, CSS değişkenlerini bütün bileşenlerde günceller.
export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
}

// React yüklenmeden önce kaydedilen veya sistemden alınan başlangıç temasını uygular.
export function initializeTheme() {
  applyTheme(readThemePreference() ?? (systemThemeQuery().matches ? 'dark' : 'light'))
}
