import { useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react'

const icons = {
  home: '⌂', medical: '✚', archive: '▤', signIn: '↪', signOut: '↩',
  sun: '☼', moon: '☾', menu: '☰', close: '×', arrow: '→', department: '◈', all: '⊞', patient: '✎',
}

// İkonlar görünür etiketleri destekler. Ekran okuyucu metin etiketini tek kez okur.
export function Icon({ name }: { name: keyof typeof icons }) {
  return <span className="icon" aria-hidden="true">{icons[name]}</span>
}

// Ortak düğme biçimi, klavye davranışını tarayıcının yerel button öğesine bırakır.
export function Button({ className = '', variant = 'primary', type = 'button', ...props }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' }) {
  return <button {...props} type={type} className={`button button--${variant} ${className}`} />
}

// Üretilen kimlik, alanı etiketi ve varsa hata mesajıyla ilişkilendirir.
export function TextField({ label, error, id, className = '', ...props }:
  InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }) {
  const generatedId = useId()
  const fieldId = id ?? generatedId
  const describedBy = [props['aria-describedby'], error ? `${fieldId}-error` : ''].filter(Boolean).join(' ')
  return <div className="field">
    <label htmlFor={fieldId}>{label}</label>
    <input {...props} id={fieldId} className={`text-field ${className}`}
      aria-invalid={error ? true : undefined} aria-describedby={describedBy || undefined} />
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
