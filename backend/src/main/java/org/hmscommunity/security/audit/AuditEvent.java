package org.hmscommunity.security.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;

/**
 * Değiştirilemez (immutable) tek bir denetim kaydı.
 *
 * <p>İçerik kuralları: parola, parola özeti, ham veya özetlenmiş token ve tıbbi içerik ASLA yer almaz.
 * {@code details} yalnızca sabit, makine tarafından okunabilir kodlar içerir (serbest metin içermez).
 *
 * @param actor işlemi yapan personel; kimliği doğrulanamamış başarısız girişlerde {@code null}
 * @param resourceId ilgili kaydın kimliği; bilinmiyorsa {@code null}
 */
public record AuditEvent(
    UUID id,
    Instant occurredAt,
    AuditAction action,
    AuthenticatedStaff actor,
    AuditResourceType resourceType,
    String resourceId,
    List<FieldChange> changes,
    Map<String, String> details) {

    public AuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resourceType, "resourceType");
        changes = List.copyOf(changes);
        details = Map.copyOf(details);
    }
}
