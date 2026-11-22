import { useRef, useState, type FormEvent } from 'react'
import { Alert, Button, Card, Icon, TextField } from '../components/ui'
import { useAuth } from './AuthProvider'
import { AuthError } from './types'
import { mockEnabled } from './service'

// Giriş ve çıkış bildirimleri, ortak temayı kullanan aynı form kartında gösterilir.
export function LoginPage() {
  const { login, busy, notice } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  // Görünürlük yalnızca alanın tipini değiştirir. Form ve sunucu hataları ayrı tutulur.
  const [visible, setVisible] = useState(false)
  const [errors, setErrors] = useState<{ username?: string; password?: string }>({})
  const [error, setError] = useState('')
  const formRef = useRef<HTMLFormElement>(null)

  // İlk geçersiz alana odaklanır; şifre, isteğin sonucu ne olursa olsun formdan temizlenir.
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    const nextErrors = {
      username: username.trim() ? undefined : 'Enter your username.',
      password: password ? undefined : 'Enter your password.',
    }
    setErrors(nextErrors)
    setError('')
    if (nextErrors.username || nextErrors.password) {
      formRef.current?.querySelector<HTMLInputElement>(nextErrors.username ? '#username' : '#password')?.focus()
      return
    }
    try { await login({ username: username.trim(), password }) }
    catch (cause) { setError(cause instanceof AuthError ? cause.message : 'Unable to sign in. Please try again.') }
    finally { setPassword('') }
  }

  return <section className="login-layout" aria-labelledby="page-title">
    {/* Marka paneli CSS ile çizilir. Görsel desen formun klavye akışına girmez. */}
    <div className="login-brand-panel" aria-hidden="true">
      <span className="login-brand-symbol"><Icon name="medical" /></span>
      <span className="login-brand-title">Hospital<br />management</span>
    </div>
    <Card className="login-card">
      <header className="login-heading">
        <h1 id="page-title" tabIndex={-1}>Staff sign in</h1>
        <p className="muted">Access your workspace with your staff account.</p>
      </header>
      {notice && <Alert>{notice}</Alert>}
      <form ref={formRef} onSubmit={submit} noValidate aria-busy={busy}>
        <TextField id="username" label="Username" name="username" autoComplete="username" required
          value={username} disabled={busy} error={errors.username}
          onChange={event => { setUsername(event.target.value); setErrors(previous => ({ ...previous, username: undefined })); setError('') }} />
        <TextField id="password" label="Password" name="password" type={visible ? 'text' : 'password'}
          autoComplete="current-password" required value={password} disabled={busy} error={errors.password}
          onChange={event => { setPassword(event.target.value); setErrors(previous => ({ ...previous, password: undefined })); setError('') }} />
        <Button variant="tertiary" aria-controls="password" aria-pressed={visible} disabled={busy}
          className="password-toggle" onClick={() => setVisible(value => !value)}>
          {visible ? 'Hide password' : 'Show password'}
        </Button>
        {error && <Alert tone="error">{error}</Alert>}
        <Button type="submit" disabled={busy} loading={busy} className="full-width">{busy ? 'Please wait…' : 'Sign in'}</Button>
        <span className="sr-only" role="status">{busy ? 'Request in progress.' : ''}</span>
      </form>
      {mockEnabled
        ? <Alert tone="warning">Demo mode. Use a synthetic account listed in the frontend README.</Alert>
        : <p className="muted login-note">Use a synthetic account for this educational prototype.</p>}
    </Card>
  </section>
}
