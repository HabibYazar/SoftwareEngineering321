import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { createAdminServices } from './admin/service'
import { AuthProvider } from './auth/AuthProvider'
import { createAuthorizedRequest } from './auth/authorizedRequest'
import { createAuthSession } from './auth/session'
import { createAuthService } from './auth/service'
import { createPatientProfileService } from './patients/profile/service'
import { createPatientSearchService } from './patients/search/service'
import { createRegistrationService } from './patients/service'
import { createDirectoryServices } from './staff/service'
import { initializeTheme } from './theme/preference'
import './style.css'
import './staff/directory.css'
import './admin/admin.css'

// Oturum, klinisyen, kayıt ve arama servisleri bağımsız seçilir. Tema ilk React çiziminden önce uygulanır.
initializeTheme()

// Gerçek iş servisleri, giriş ve çıkışla aynı oturumu ve ilgili iş akışının iznini kullanır.
const session = createAuthSession()
Promise.all([
  createAuthService(),
  createAdminServices(),
  createDirectoryServices(createAuthorizedRequest(session, 'viewDoctors')),
  createRegistrationService(createAuthorizedRequest(session, 'registerPatients')),
  createPatientSearchService(createAuthorizedRequest(session, 'searchPatients')),
  createPatientProfileService(createAuthorizedRequest(session, 'editPatients')),
]).then(([authService, adminServices, directory, registrationService, patientSearchService, patientProfileService]) => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <AuthProvider service={authService} session={session}>
        <App {...adminServices} directory={directory} registrationService={registrationService} patientSearchService={patientSearchService}
          patientProfileService={patientProfileService} />
      </AuthProvider>
    </StrictMode>,
  )
}).catch(() => {
  document.getElementById('root')!.textContent = 'Unable to start the application. Please reload the page.'
})
