package org.hmscommunity.security;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * User_Story_V3 ve OpenAPI sözleşmesinde tanımlı yedi personel rolü.
 * JSON gösterimi sözleşmedeki {@code LoginResponse.role} enum değerleriyle birebir aynıdır.
 */
public enum StaffRole {
    ADMINISTRATOR("Administrator"),
    RECEPTIONIST("Receptionist"),
    PHYSICIAN("Physician"),
    NURSE("Nurse"),
    PATIENT_CARE_ASSISTANT("PatientCareAssistant"),
    LAB_TECHNICIAN("LabTechnician"),
    CLEANING_STAFF("CleaningStaff");

    private final String apiName;

    StaffRole(String apiName) {
        this.apiName = apiName;
    }

    @JsonValue
    public String apiName() {
        return apiName;
    }

    /** Spring Security yetki (authority) adı, örn. {@code ROLE_RECEPTIONIST}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
