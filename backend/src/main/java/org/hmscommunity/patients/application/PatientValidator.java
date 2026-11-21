package org.hmscommunity.patients.application;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.hmscommunity.common.web.FieldViolation;
import org.hmscommunity.patients.domain.PatientDetails;
import org.springframework.stereotype.Component;

/**
 * US-06 hasta doğrulama kuralları; US-08 güncellemelerinde de aynen yeniden kullanılır.
 * Tüm hatalar tek seferde, alan düzeyinde döndürülür. Reddedilen değerler hata mesajına eklenmez.
 */
@Component
public class PatientValidator {

    /** Sentetik ulusal kimlik numarası: tam olarak 11 rakam. */
    private static final Pattern NATIONAL_ID = Pattern.compile("^[0-9]{11}$");
    /** Veritabanının kabul ettiği en erken doğum tarihi (V3 migration'ı). */
    static final LocalDate EARLIEST_DATE_OF_BIRTH = LocalDate.of(1900, 1, 1);

    private final Validator validator;

    public PatientValidator(Validator validator) {
        this.validator = validator;
    }

    public static boolean isWellFormedNationalId(String nationalId) {
        return nationalId != null && NATIONAL_ID.matcher(nationalId).matches();
    }

    public List<FieldViolation> validateNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return List.of(new FieldViolation("nationalId", "NotBlank", "must not be blank"));
        }
        if (!isWellFormedNationalId(nationalId)) {
            return List.of(new FieldViolation("nationalId", "Pattern", "must be exactly 11 digits"));
        }
        return List.of();
    }

    public List<FieldViolation> validateDetails(PatientDetails details) {
        List<FieldViolation> violations = new ArrayList<>(validator.validate(details).stream()
            .map(PatientValidator::toFieldViolation)
            .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code))
            .toList());
        // Veritabanı kuralı (field_rules / ck_patients_date_of_birth_min): doğum tarihi 1900-01-01'den önce olamaz.
        if (details.dateOfBirth() != null && details.dateOfBirth().isBefore(EARLIEST_DATE_OF_BIRTH)) {
            violations.add(new FieldViolation("dateOfBirth", "Min", "must not be before " + EARLIEST_DATE_OF_BIRTH));
        }
        // US-06: "Advanced" seviyesindeki hastalar için yakın bilgisi zorunludur (kayıt ve güncellemede).
        if (details.conditionLevel() != null && details.conditionLevel().requiresRelativeInformation()
                && details.relativeInformation() == null) {
            violations.add(new FieldViolation("relativeInformation", "RequiredForAdvancedCondition",
                "is required when conditionLevel is ADVANCED"));
        }
        return violations;
    }

    private static FieldViolation toFieldViolation(ConstraintViolation<PatientDetails> violation) {
        return new FieldViolation(
            violation.getPropertyPath().toString(),
            violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
            violation.getMessage());
    }
}
