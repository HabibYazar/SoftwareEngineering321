package org.hmscommunity.patients.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Hasta yakını iletişim bilgisi. Verildiğinde üç alanın tamamı zorunludur
 * (OpenAPI {@code RelativeInformation}: fullName, relationship, phone).
 */
public record RelativeInformation(
    @NotBlank @Size(max = 150) String fullName,
    @NotBlank @Size(max = 80) String relationship,
    @NotBlank @Size(max = 32) @Pattern(regexp = PatientDetails.PHONE_PATTERN) String phone) {
}
