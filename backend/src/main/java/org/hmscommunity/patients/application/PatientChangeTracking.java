package org.hmscommunity.patients.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.patients.domain.RelativeInformation;
import org.hmscommunity.security.audit.AuditChangeFilter;
import org.hmscommunity.security.audit.FieldChange;

/**
 * US-08 güncellemelerinde denetim kaydına yazılacak önceki/sonraki değerlerin açık gizlilik politikası.
 *
 * <p>US-08 kabul kriteri her güncellemenin önceki ve sonraki değerleriyle kaydedilmesini istediği için
 * hastanın düzenlenebilir profil alanları izin listesindedir. Ulusal kimlik numarası değişmediğinden ve
 * birincil tanımlayıcı olduğundan anlık görüntüye hiç alınmaz. Hasta modelinde tıbbi içerik (tanı, not)
 * bulunmaz; eklenirse izin listesine eklenmemelidir (değeri otomatik maskelenir).
 * Not: {@code conditionLevel} değeri de kaydedilir; Product Owner maskelenmesini isterse yalnızca
 * {@link #RECORDABLE_FIELDS} listesinden çıkarılması yeterlidir.
 */
final class PatientChangeTracking {

    static final Set<String> RECORDABLE_FIELDS = Set.of(
        "firstName", "lastName", "dateOfBirth", "gender", "phone", "address", "conditionLevel",
        "relativeInformation.fullName", "relativeInformation.relationship", "relativeInformation.phone");

    private static final AuditChangeFilter FILTER = new AuditChangeFilter(RECORDABLE_FIELDS);

    private PatientChangeTracking() {
    }

    static List<FieldChange> changes(Patient before, Patient after) {
        return FILTER.changes(snapshot(before), snapshot(after));
    }

    static Map<String, String> snapshot(Patient patient) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("firstName", patient.firstName());
        values.put("lastName", patient.lastName());
        values.put("dateOfBirth", patient.dateOfBirth() == null ? null : patient.dateOfBirth().toString());
        values.put("gender", patient.gender());
        values.put("phone", patient.phone());
        values.put("address", patient.address());
        values.put("conditionLevel", patient.conditionLevel() == null ? null : patient.conditionLevel().name());
        RelativeInformation relative = patient.relativeInformation();
        values.put("relativeInformation.fullName", relative == null ? null : relative.fullName());
        values.put("relativeInformation.relationship", relative == null ? null : relative.relationship());
        values.put("relativeInformation.phone", relative == null ? null : relative.phone());
        return values;
    }
}
