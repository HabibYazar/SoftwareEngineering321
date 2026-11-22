package org.hmscommunity.patients.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hmscommunity.common.IntegrationPendingException;
import org.hmscommunity.patients.application.PatientRepository;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.security.AuthenticatedStaff;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

/**
 * Veritabanı yapılandırılmadığında ({@code HMS_DB_URL} yok) kullanılan fail-closed uygulama.
 * Veri saklamaz ve uydurmaz; her çağrı HTTP 503 ile sonuçlanır. {@link JdbcPatientRepository} etkin
 * olduğunda {@link Fallback} sayesinde kullanılmaz.
 */
@Component
@Fallback
class UnavailablePatientRepository implements PatientRepository {

    private static final String INTEGRATION = "patient records (#177, #196)";

    @Override
    public Patient insert(Patient patient, AuthenticatedStaff actor) {
        throw new IntegrationPendingException(INTEGRATION);
    }

    @Override
    public Optional<Patient> findById(UUID id) {
        throw new IntegrationPendingException(INTEGRATION);
    }

    @Override
    public Optional<Patient> findByNationalId(String nationalId) {
        throw new IntegrationPendingException(INTEGRATION);
    }

    @Override
    public List<Patient> searchByName(String nameFragment) {
        throw new IntegrationPendingException(INTEGRATION);
    }

    @Override
    public Patient update(Patient patient, AuthenticatedStaff actor) {
        throw new IntegrationPendingException(INTEGRATION);
    }
}
