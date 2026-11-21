import { useLayoutEffect } from 'react'

// Uygulama geçmişi ile tarayıcı yenilemesi aynı kaydedilmemiş form durumunu izler.
export function useUnsavedPatientForm(dirty: boolean, onDirtyChange: (dirty: boolean) => void) {
  useLayoutEffect(() => {
    onDirtyChange(dirty)
    if (!dirty) return
    function warnBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault()
      event.returnValue = true
    }
    window.addEventListener('beforeunload', warnBeforeUnload)
    return () => {
      onDirtyChange(false)
      window.removeEventListener('beforeunload', warnBeforeUnload)
    }
  }, [dirty, onDirtyChange])
}