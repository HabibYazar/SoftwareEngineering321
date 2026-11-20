package org.hmscommunity.security.audit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Önceki/sonraki değer takibi için açık (explicit) gizlilik filtresi.
 *
 * <p>Kurallar:
 * <ol>
 *   <li>Yalnızca değeri değişen alanlar kaydedilir.</li>
 *   <li>Değer, yalnızca alan açıkça izin listesinde ({@code recordableFields}) ise kaydedilir;
 *       listede olmayan alanların değişmiş olduğu bilgisi korunur ancak değerleri maskelenir.</li>
 *   <li>Parola, token, gizli anahtar vb. çağrıştıran alan adları izin listesine hiç eklenemez ve
 *       her durumda maskelenir.</li>
 * </ol>
 */
public final class AuditChangeFilter {

    public static final String REDACTED = "[REDACTED]";

    private static final Pattern SECRET_FIELD =
        Pattern.compile("(?i).*(password|passwd|secret|token|credential|authorization|hash|apikey|api_key).*");

    private final Set<String> recordableFields;

    public AuditChangeFilter(Set<String> recordableFields) {
        for (String field : recordableFields) {
            if (isSecret(field)) {
                throw new IllegalArgumentException("Secret field must never be recorded in audit values: " + field);
            }
        }
        this.recordableFields = Set.copyOf(recordableFields);
    }

    public List<FieldChange> changes(Map<String, String> before, Map<String, String> after) {
        Set<String> fields = new LinkedHashSet<>(before.keySet());
        fields.addAll(after.keySet());
        List<FieldChange> changes = new ArrayList<>();
        for (String field : fields) {
            String oldValue = before.get(field);
            String newValue = after.get(field);
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            if (recordableFields.contains(field) && !isSecret(field)) {
                changes.add(new FieldChange(field, oldValue, newValue));
            } else {
                changes.add(new FieldChange(field, redact(oldValue), redact(newValue)));
            }
        }
        return List.copyOf(changes);
    }

    private static String redact(String value) {
        return value == null ? null : REDACTED;
    }

    private static boolean isSecret(String field) {
        return SECRET_FIELD.matcher(field).matches();
    }
}
