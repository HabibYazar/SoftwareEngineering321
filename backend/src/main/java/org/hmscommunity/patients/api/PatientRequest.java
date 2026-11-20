package org.hmscommunity.patients.api;

import java.time.LocalDate;
import org.hmscommunity.patients.domain.ConditionLevel;
import org.hmscommunity.patients.domain.PatientDetails;
import org.hmscommunity.patients.domain.RelativeInformation;

/**
 * Hasta kayıt (OpenAPI {@code NewPatient}) ve güncelleme ({@code PatientUpdate}) istek gövdesi.
 * Doğrulama, kayıt ve güncellemede aynı kuralların uygulanması için servis katmanında yapılır;
 * burada yalnızca baştaki/sondaki boşluklar temizlenir.
 */
public record PatientRequest(
    String firstName,
    String lastName,
    String nationalId,
    LocalDate dateOfBirth,
    String gender,
    String phone,
    String address,
    ConditionLevel conditionLevel,
    RelativeInformation relativeInformation) {

    String normalizedNationalId() {
        return trim(nationalId);
    }

    PatientDetails toDetails() {
        RelativeInformation relative = relativeInformation == null ? null : new RelativeInformation(
            trim(relativeInformation.fullName()), trim(relativeInformation.relationship()),
            trim(relativeInformation.phone()));
        return new PatientDetails(trim(firstName), trim(lastName), dateOfBirth, trim(gender), trim(phone),
            trim(address), conditionLevel, relative);
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
