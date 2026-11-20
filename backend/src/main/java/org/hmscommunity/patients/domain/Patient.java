package org.hmscommunity.patients.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Kayıtlı hasta (OpenAPI {@code Patient}). Ulusal kimlik numarası kayıttan sonra değiştirilemez;
 * bu nedenle güncelleme yalnızca {@link #withDetails(PatientDetails)} ile ve kimlik korunarak yapılır.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Patient(
    UUID id,
    String firstName,
    String lastName,
    String nationalId,
    LocalDate dateOfBirth,
    String gender,
    String phone,
    String address,
    ConditionLevel conditionLevel,
    RelativeInformation relativeInformation) {

    public Patient {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nationalId, "nationalId");
    }

    public static Patient create(UUID id, String nationalId, PatientDetails details) {
        return new Patient(id, details.firstName(), details.lastName(), nationalId, details.dateOfBirth(),
            details.gender(), details.phone(), details.address(), details.conditionLevel(),
            details.relativeInformation());
    }

    /** Kimliği ve ulusal kimlik numarasını koruyarak diğer tüm alanları değiştirir. */
    public Patient withDetails(PatientDetails details) {
        return create(id, nationalId, details);
    }

    public PatientDetails details() {
        return new PatientDetails(firstName, lastName, dateOfBirth, gender, phone, address, conditionLevel,
            relativeInformation);
    }
}
