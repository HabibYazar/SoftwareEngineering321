package org.hmscommunity.patients.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hmscommunity.common.web.FieldViolation;
import org.hmscommunity.common.web.RequestValidationException;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.patients.domain.PatientDetails;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.audit.AuditResourceType;
import org.hmscommunity.security.audit.AuditService;
import org.springframework.stereotype.Service;

/**
 * Hasta yönetimi iş kuralları.
 * Yetkilendirme (US-02) güvenlik filtre zincirinde, denetim (US-03) bu serviste uygulanır.
 */
@Service
public class PatientService {

    private final PatientRepository patients;
    private final PatientValidator validator;
    private final AuditService audit;

    public PatientService(PatientRepository patients, PatientValidator validator, AuditService audit) {
        this.patients = patients;
        this.validator = validator;
        this.audit = audit;
    }

    /**
     * US-06 (#179): yeni hasta kaydı. Ulusal kimlik numarası benzersiz olmalıdır; benzersizlik hem
     * burada hem de (eşzamanlı istekler için) kalıcılık katmanındaki UNIQUE kısıtı ile korunur.
     */
    public Patient register(String nationalId, PatientDetails details, AuthenticatedStaff actor) {
        List<FieldViolation> violations = new ArrayList<>(validator.validateNationalId(nationalId));
        violations.addAll(validator.validateDetails(details));
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
        if (patients.findByNationalId(nationalId).isPresent()) {
            throw new DuplicateNationalIdException();
        }
        Patient registered = patients.insert(Patient.create(UUID.randomUUID(), nationalId, details));
        audit.created(actor, AuditResourceType.PATIENT, registered.id().toString());
        return registered;
    }
}
