import { useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react'

const icons = {
  home: 'M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z',
  medical: 'M9 3h6v6h6v6h-6v6H9v-6H3V9h6z',
  archive: 'M5 8v13h14V8M3 3h18v5H3zM10 12h4',
  signIn: 'M14 4h6v16h-6M3 12h12m-4-4 4 4-4 4',
  signOut: 'M10 4H4v16h6m11-8H9m8-4 4 4-4 4',
  back: 'm11 5-7 7 7 7M4 12h16',
  refresh: 'M20 11a8 8 0 1 0-2 6m2-12v6h-6',
  check: 'm5 12 4 4L19 6',
  sun: 'M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19m0-14-1.5 1.5m-11 11L5 19M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  moon: 'M20.9 14A9 9 0 0 1 10 3.1 9 9 0 1 0 20.9 14Z',
  menu: 'M4 6h16M4 12h16M4 18h16',
  close: 'm6 6 12 12M18 6 6 18',
  arrow: 'M4 12h16m-7-7 7 7-7 7',
  department: 'M4 21V5h11v16M15 11h5v10M2 21h20M8 9h3m-3 4h3m-3 4h3',
  all: 'M4 4h6v6H4zm10 0h6v6h-6zM4 14h6v6H4zm10 0h6v6h-6z',
  patient: 'M4 21v-2a5 5 0 0 1 5-5h3m9 4h-6m3 3v-6M15 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  staff: 'M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  search: 'm16 16 4 4m-2-10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
}

export function Icon({ name }: { name: keyof typeof icons }) {
  return <svg className="icon" aria-hidden="true" focusable="false" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
    <path d={icons[name]} />
  </svg>
}


// Görsel varyantlar ve bekleme göstergesi yerel button davranışını korur.
export function Button({ className = '', variant = 'primary', type = 'button', loading = false, children, ...props }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'tertiary' | 'danger'; loading?: boolean }) {
  return <button {...props} type={type} className={`button button--${variant} ${className}`}>
    {loading && <span className="button-spinner" aria-hidden="true" />}{children}
  </button>
}

// Üretilen kimlik, alanı etiketi ve varsa hata mesajıyla ilişkilendirir.
export function TextField({ label, hint, error, id, className = '', ...props }:
  InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }) {
  const generatedId = useId()
  const fieldId = id ?? generatedId
  const describedBy = [props['aria-describedby'], hint ? `${fieldId}-hint` : '', error ? `${fieldId}-error` : ''].filter(Boolean).join(' ')
  return <div className="field">
    <label htmlFor={fieldId}>{label}</label>
    <input {...props} id={fieldId} className={`text-field ${className}`}
      aria-invalid={error ? true : undefined} aria-describedby={describedBy || undefined} />
    {hint && <span id={`${fieldId}-hint`} className="field-hint">{hint}</span>}
    {error && <span id={`${fieldId}-error`} className="field-error">{error}</span>}
  </div>
}

// Kart ortak yüzeyi sağlar ve çağıran bileşen kendi içeriğini yerleştirir.
export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`card ${className}`}>{children}</div>
}

// Hatalar hemen duyurulur. Bilgi ve uyarı mesajları konuşmayı kesmeden okunur.
export function Alert({ children, tone = 'info' }: { children: ReactNode; tone?: 'info' | 'error' | 'warning' }) {
  return <div className={`alert alert--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>{children}</div>
}
