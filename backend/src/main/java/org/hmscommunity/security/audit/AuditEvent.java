package org.hmscommunity.security.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;

/**
 * Değiştirilemez (immutable) tek bir denetim olayı.
 *
 * <p>İçerik kuralları: parola, parola özeti, ham veya özetlenmiş token ve tıbbi içerik ASLA yer almaz.
 * {@code details} yalnızca sabit, makine tarafından okunabilir kodlar içerir (serbest metin içermez).
 * Kalıcı depo kendi kimliğini ve zaman damgasını atar; buradaki {@code id} ve {@code occurredAt}
 * yalnızca uygulama içi izleme içindir.
 *
 * @param actor işlemi yapan personel (hesap ve oturum veritabanı bağlamına aktarılır)
 * @param resourceId ilgili kaydın kimliği
 */
public record AuditEvent(
    UUID id,
    Instant occurredAt,
    AuditAction action,
    AuthenticatedStaff actor,
    AuditResourceType resourceType,
    UUID resourceId,
    Map<String, String> details) {

    public AuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        details = Map.copyOf(details);
    }
}
