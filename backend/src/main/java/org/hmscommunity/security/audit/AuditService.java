package org.hmscommunity.security.audit;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.springframework.stereotype.Service;

/**
 * Modüllerin uygulama düzeyinde denetim olayı üretmek için kullandığı giriş noktası (US-03 / #145).
 *
 * <p>Yalnızca veritabanının kendiliğinden kaydedemediği olaylar burada üretilir: kayıt okuma (görüntüleme).
 * Giriş/çıkış ve veri değişiklikleri (oluşturma, önceki/sonraki değerlerle güncelleme, silme) şemadaki
 * fonksiyon ve tetikleyiciler tarafından aynı işlemde kaydedildiği için burada tekrar edilmez
 * (bkz. {@link AuditAction}). Depoya yazılamazsa istisna çağırana iletilir.
 */
@Service
public class AuditService {

    /** Hasta/EMR kaydının hangi yolla görüntülendiği. */
    public enum AccessChannel { RECORD, SEARCH_RESULT }

    private final AuditEventStore store;
    private final Clock clock;

    public AuditService(AuditEventStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** Hasta kaydı görüntüleme (US-03). Kaydın içeriği değil, yalnızca kimliği kaydedilir. */
    public void patientRecordViewed(AuthenticatedStaff actor, UUID patientId, AccessChannel channel) {
        append(actor, AuditResourceType.PATIENT, patientId, Map.of("accessChannel", channel.name()));
    }

    /**
     * Elektronik tıbbi kayıt (EMR) görüntüleme (US-03). Tıbbi içerik kaydedilmez; yalnızca kayıt ve
     * hasta kimliği tutulur. EMR uç noktaları (US-17.x/US-18, Sprint 3) bu metodu çağırmalıdır.
     */
    public void medicalRecordViewed(AuthenticatedStaff actor, UUID recordId, UUID patientId) {
        append(actor, AuditResourceType.MEDICAL_RECORD, recordId,
            Map.of("patientId", patientId.toString(), "accessChannel", AccessChannel.RECORD.name()));
    }

    private void append(AuthenticatedStaff actor, AuditResourceType type, UUID resourceId,
            Map<String, String> details) {
        Objects.requireNonNull(actor, "Audited operations require an authenticated actor");
        store.append(new AuditEvent(UUID.randomUUID(), clock.instant(), AuditAction.VIEW, actor, type, resourceId,
            details));
    }
}
