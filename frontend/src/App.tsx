import { useCallback, useEffect, useRef, useState } from 'react'
import { AuditLogsPage } from './admin/audit/AuditLogsPage'
import { StaffManagementPage } from './admin/staff/StaffManagementPage'
import type { AuditService } from './admin/audit/model'
import type { StaffManagementService } from './admin/staff/model'
import { useAuth } from './auth/AuthProvider'
import { LoginPage } from './auth/LoginPage'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { auditLogsPath, staffManagementPath, doctorsPath, homePath, patientSearchPath, readPatientEditPath, registrationPath, roleLabels, workflowRoutes } from './auth/routing'
import { AppLayout } from './components/AppLayout'
import { useAppNavigation } from './navigation/useAppNavigation'
import { ArchitecturePage } from './pages/ArchitecturePage'
import { WorkspacePage } from './pages/WorkspacePage'
import { PatientRegistrationPage } from './patients/PatientRegistrationPage'
import { PatientProfilePage } from './patients/profile/PatientProfilePage'
import type { PatientProfileService } from './patients/profile/model'
import { PatientSearchPage } from './patients/search/PatientSearchPage'
import type { PatientSearchService } from './patients/search/model'
import { appendRecentPatient, updateRecentPatient, type PatientSummary } from './patients/recentPatients'
import type { Patient, PatientRegistrationService } from './patients/types'
import { DoctorDirectory } from './staff/DoctorDirectory'
import type { DirectoryServices } from './staff/service'

const leaveMessages = {
  registration: 'Leave patient registration? An unsaved form will be lost. If a registration is in progress, its outcome may be unknown.',
  profile: 'Leave patient editing? Unsaved changes will be lost. If a save is in progress, its outcome may be unknown.',
}

// Geçmiş ve yenileme ortak yerleşimde paylaşılır. Korunan ekranların izni rota tanımından gelir.
function App({ audit, staffManagement, directory, registrationService, patientSearchService, patientProfileService }: {
  audit: AuditService | null
  staffManagement: StaffManagementService | null
  directory: DirectoryServices
  registrationService: PatientRegistrationService
  patientSearchService: PatientSearchService
  patientProfileService: PatientProfileService
}) {
  const { role, sessionMarker, busy, logout } = useAuth()
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
  const [recent, setRecent] = useState<{ session: object; patients: PatientSummary[] } | null>(null)
  const recentPatients = recent?.session === sessionMarker ? recent.patients : []

  // Önceki girişe ait özetler yeni girişte gösterilmez ve bellekten kaldırılır.
  useEffect(() => setRecent(null), [sessionMarker])

  function recordRegistration(patient: Patient) {
    if (!sessionMarker) return
    setRecent(previous => ({ session: sessionMarker, patients: appendRecentPatient(
      previous?.session === sessionMarker ? previous.patients : [], patient,
    ) }))
  }

  function recordUpdate(patient: Patient) {
    setRecent(previous => previous && previous.session === sessionMarker
      ? { ...previous, patients: updateRecentPatient(previous.patients, patient) } : previous)
    refresh()
  }

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
    onRefresh: route === doctorsPath || route === auditLogsPath || route === staffManagementPath || route === '/foundation' || (route === patientSearchPath && searchRefreshReady)
      ? refresh : undefined,
  }

  function signOut() {
    if (confirmLeave()) void logout()
  }

  return <AppLayout role={role} route={route} busy={busy} onSignOut={signOut}
    onNavigate={navigate} navigation={navigation}>
    {route === '/foundation' && <ArchitecturePage refreshVersion={refreshVersion} />}
    {route === '/login' && <LoginPage />}
    {/* Bu ekranların demo durumu, merkezi rota yetkisini değiştirmez. */}
    {route === auditLogsPath && <ProtectedRoute path={auditLogsPath}>
      <AuditLogsPage service={audit} refreshVersion={refreshVersion} />
    </ProtectedRoute>}
    {route === staffManagementPath && <ProtectedRoute path={staffManagementPath}>
      <StaffManagementPage service={staffManagement} refreshVersion={refreshVersion} />
    </ProtectedRoute>}
    {route === doctorsPath && <ProtectedRoute path={doctorsPath}>
      <DoctorDirectory directory={directory} refreshVersion={refreshVersion} />
    </ProtectedRoute>}
    {route === registrationPath && <ProtectedRoute path={registrationPath}>
      <PatientRegistrationPage service={registrationService} onDirtyChange={setRegistrationDirty} onRegistered={recordRegistration} />
    </ProtectedRoute>}
    {/* Düzenlemeden dönünce ölçütler korunur. İş akışından veya oturumdan çıkınca arama kaldırılır. */}
    {(route === patientSearchPath || patientId !== undefined) && <ProtectedRoute path={patientSearchPath}>
      <div hidden={route !== patientSearchPath}>
        <PatientSearchPage service={patientSearchService} refreshVersion={refreshVersion}
          onRefreshReady={setSearchRefreshReady} active={route === patientSearchPath} recentPatients={recentPatients} />
      </div>
    </ProtectedRoute>}
    {patientId !== undefined && <ProtectedRoute path={route}>
      <PatientProfilePage key={route} patientId={patientId} service={patientProfileService}
        onDirtyChange={setProfileDirty} onSaved={recordUpdate} />
    </ProtectedRoute>}
    {role && route === homePath(role) && <WorkspacePage role={role} />}
  </AppLayout>
}

export default App