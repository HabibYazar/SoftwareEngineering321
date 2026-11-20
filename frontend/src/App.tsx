import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from './auth/AuthProvider'
import { LoginPage } from './auth/LoginPage'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { doctorsPath, homePath, patientSearchPath, readPatientEditPath, registrationPath, roleLabels, workflowRoutes } from './auth/routing'
import { AppLayout } from './components/AppLayout'
import { useAppNavigation } from './navigation/useAppNavigation'
import { ArchitecturePage } from './pages/ArchitecturePage'
import { WorkspacePage } from './pages/WorkspacePage'
import { PatientRegistrationPage } from './patients/PatientRegistrationPage'
import { PatientProfilePage } from './patients/profile/PatientProfilePage'
import type { PatientProfileService } from './patients/profile/model'
import { PatientSearchPage } from './patients/search/PatientSearchPage'
import type { PatientSearchService } from './patients/search/model'
import type { PatientRegistrationService } from './patients/types'
import { DoctorDirectory } from './staff/DoctorDirectory'
import type { StaffService } from './staff/types'

const leaveMessages = {
  registration: 'Leave patient registration? An unsaved form will be lost. If a registration is in progress, its outcome may be unknown.',
  profile: 'Leave patient editing? Unsaved changes will be lost. If a save is in progress, its outcome may be unknown.',
}

// Geçmiş ve yenileme ortak yerleşimde paylaşılır. Korunan ekranların izni rota tanımından gelir.
function App({ staffService, registrationService, patientSearchService, patientProfileService }: {
  staffService: StaffService
  registrationService: PatientRegistrationService
  patientSearchService: PatientSearchService
  patientProfileService: PatientProfileService
}) {
  const { role, busy, logout } = useAuth()
  // Kayıt ve düzenleme aynı ayrılma denetimini kullanır. Yalnızca açık form uyarı gösterebilir.
  const dirtyForm = useRef<keyof typeof leaveMessages | null>(null)
  const setRegistrationDirty = useCallback((dirty: boolean) => { dirtyForm.current = dirty ? 'registration' : null }, [])
  const setProfileDirty = useCallback((dirty: boolean) => { dirtyForm.current = dirty ? 'profile' : null }, [])
  const confirmLeave = useCallback(() => !dirtyForm.current || window.confirm(leaveMessages[dirtyForm.current]), [])
  const { path: route, navigate, back, forward, canGoBack, canGoForward } = useAppNavigation(role, confirmLeave)
  const [refreshVersion, setRefreshVersion] = useState(0)
  const [searchRefreshReady, setSearchRefreshReady] = useState(false)
  const refresh = useCallback(() => setRefreshVersion(previous => previous + 1), [])
  const patientId = readPatientEditPath(route)

  useEffect(() => {
    const title = route === '/foundation' ? 'Architecture foundation'
      : patientId !== undefined ? 'Edit patient profile'
      : workflowRoutes.find(workflow => workflow.path === route)?.label
        ?? (role ? roleLabels[role] + ' home' : 'Staff sign in')
    document.title = title + ' | HMS Community'
    document.getElementById('page-title')?.focus()
  }, [patientId, role, route])

  // Arama ekranı ancak geçerli bir sorgu gönderildiyse ortak Refresh eylemini açar.
  const navigation = {
    canGoBack,
    canGoForward,
    onBack: back,
    onForward: forward,
    onRefresh: route === doctorsPath || route === '/foundation' || (route === patientSearchPath && searchRefreshReady)
      ? refresh : undefined,
  }

  function signOut() {
    if (confirmLeave()) void logout()
  }

  return <AppLayout role={role} route={route} busy={busy} onSignOut={signOut}
    onNavigate={navigate} navigation={navigation}>
    {route === '/foundation' && <ArchitecturePage refreshVersion={refreshVersion} />}
    {route === '/login' && <LoginPage />}
    {route === doctorsPath && <ProtectedRoute path={doctorsPath}>
      <DoctorDirectory service={staffService} refreshVersion={refreshVersion} />
    </ProtectedRoute>}
    {route === registrationPath && <ProtectedRoute path={registrationPath}>
      <PatientRegistrationPage service={registrationService} onDirtyChange={setRegistrationDirty} />
    </ProtectedRoute>}
    {/* Düzenlemeden dönünce ölçütler korunur. İş akışından veya oturumdan çıkınca arama kaldırılır. */}
    {(route === patientSearchPath || patientId !== undefined) && <ProtectedRoute path={patientSearchPath}>
      <div hidden={route !== patientSearchPath}>
        <PatientSearchPage service={patientSearchService} refreshVersion={refreshVersion}
          onRefreshReady={setSearchRefreshReady} active={route === patientSearchPath} />
      </div>
    </ProtectedRoute>}
    {patientId !== undefined && <ProtectedRoute path={route}>
      <PatientProfilePage key={route} patientId={patientId} service={patientProfileService}
        onDirtyChange={setProfileDirty} onSaved={refresh} />
    </ProtectedRoute>}
    {role && route === homePath(role) && <WorkspacePage role={role} />}
  </AppLayout>
}

export default App