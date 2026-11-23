package org.hmscommunity.patients.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Hastanın ulusal kimlik numarası dışındaki tüm (güncellenebilir) bilgileri.
 * Kayıt (US-06) ve güncelleme (US-08) aynı doğrulama kurallarını bu tip üzerinden paylaşır.
 */
public record PatientDetails(
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotNull @PastOrPresent LocalDate dateOfBirth,
    @NotBlank @Size(max = 40) @Pattern(regexp = "Male|Female") String gender,
    @NotBlank @Size(max = 32) @Pattern(regexp = PHONE_PATTERN) String phone,
    @NotBlank @Size(max = 500) String address,
    @NotNull ConditionLevel conditionLevel,
    @Valid RelativeInformation relativeInformation) {

    /**
     * Telefon biçimi: isteğe bağlı "+" ile başlar, rakamla başlayıp rakamla biter; arada rakam, boşluk,
     * parantez ve tire bulunabilir (ör. {@code 0532 000 00 00}, {@code +90 (532) 000-00-00}).
     */
    public static final String PHONE_PATTERN = "^\\+?[0-9][0-9 ()-]{5,29}[0-9]$";
}
