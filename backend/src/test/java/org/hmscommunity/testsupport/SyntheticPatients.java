package org.hmscommunity.testsupport;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hmscommunity.patients.domain.ConditionLevel;
import org.hmscommunity.patients.domain.PatientDetails;
import org.hmscommunity.patients.domain.RelativeInformation;

/**
 * Testlerde kullanılan tamamen sentetik hasta verileri.
 * Ulusal kimlik numaraları rastgele seçilmiş 11 haneli değerlerdir; gerçek bir kişiye ait değildir.
 */
public final class SyntheticPatients {

    public static final String NATIONAL_ID = "90000000001";
    public static final String OTHER_NATIONAL_ID = "90000000002";

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private SyntheticPatients() {
    }

    public static RelativeInformation relative() {
        return new RelativeInformation("Deneme Yakın", "Sibling", "0555 000 00 99");
    }

    public static PatientDetails mildDetails() {
        return new PatientDetails("Deneme", "Hastaoğlu", LocalDate.of(1990, 5, 17), "Female", "0555 000 00 01",
            "Sentetik Mah. Test Sok. No:1, Ankara", ConditionLevel.MILD, null);
    }

    public static PatientDetails advancedDetails() {
        return new PatientDetails("İleri", "Sentetikçi", LocalDate.of(1948, 1, 2), "Male", "+90 (555) 000-00-02",
            "Sentetik Mah. Örnek Cad. No:2, İzmir", ConditionLevel.ADVANCED, relative());
    }

    /** OpenAPI {@code NewPatient} biçiminde istek gövdesi; testler alanları değiştirip silebilir. */
    public static Map<String, Object> newPatientJson(String nationalId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Deneme");
        body.put("lastName", "Hastaoğlu");
        body.put("nationalId", nationalId);
        body.put("dateOfBirth", "1990-05-17");
        body.put("gender", "Female");
        body.put("phone", "0555 000 00 01");
        body.put("address", "Sentetik Mah. Test Sok. No:1, Ankara");
        body.put("conditionLevel", "MILD");
        return body;
    }

    /** Paylaşılan test veritabanında çakışmayan rastgele 11 haneli sentetik ulusal kimlik numarası. */
    public static String uniqueNationalId() {
        StringBuilder digits = new StringBuilder("8");
        for (int i = 0; i < 10; i++) {
            digits.append(RANDOM.nextInt(10));
        }
        return digits.toString();
    }

    /**
     * Ad aramalarını yalnızca o testin hastalarıyla sınırlamak için rastgele harf dizisi.
     * Türkçe büyük/küçük harf dönüşümünde değişen "i" harfi bilerek kullanılmaz.
     */
    public static String uniqueToken() {
        String letters = "bcdfghjklmnpqrstvwxz";
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            token.append(letters.charAt(RANDOM.nextInt(letters.length())));
        }
        return token.toString();
    }

    public static Map<String, Object> relativeJson() {
        Map<String, Object> relative = new LinkedHashMap<>();
        relative.put("fullName", "Deneme Yakın");
        relative.put("relationship", "Sibling");
        relative.put("phone", "0555 000 00 99");
        return relative;
    }
}
