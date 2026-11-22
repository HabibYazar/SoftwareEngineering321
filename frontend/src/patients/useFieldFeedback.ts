import { useState } from 'react'
import { visibleFieldErrors } from './fieldFeedback.ts'

type Errors<Field extends string> = Partial<Record<Field, string>>

// Dokunulan alan düzeldikçe hata değişir. Bu durum yalnızca doğrulamayı etkiler ve istek göndermez.
export function useFieldFeedback<Field extends string>(client: Errors<Field>) {
  const [touched, setTouched] = useState<ReadonlySet<Field>>(() => new Set())
  const [submitted, markSubmitted] = useState(false)
  const [server, setServer] = useState<Errors<Field>>({})
  return {
    errors: visibleFieldErrors(client, server, touched, submitted),
    touch(field: Field) {
      setTouched(current => current.has(field) ? current : new Set([...current, field]))
    },
    clearField(field: Field) {
      setServer(current => {
        if (!current[field]) return current
        const next = { ...current }
        delete next[field]
        return next
      })
    },
    submit() {
      markSubmitted(true)
      setServer({})
    },
    setServer,
    reset() {
      setTouched(new Set())
      markSubmitted(false)
      setServer({})
    },
  }
}
