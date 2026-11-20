package org.hmscommunity.security.audit;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.springframework.stereotype.Service;

/**
 * Modüllerin denetim olayı üretmek için kullandığı tek giriş noktası (audit facade, US-03 / #145).
 *
 * <p>Her olaya benzersiz bir kimlik ve sunucu saatinden alınan zaman damgası atanır. Olaylar yalnızca
 * gerçekten gerçekleşen işlemler için, işlem tamamlandıktan sonra üretilir; geçmişe dönük veya
 * varsayımsal kayıt üretilmez. Depoya yazılamazsa istisna çağırana iletilir.
 */
@Service
public class AuditService {

    /** Başarısız giriş nedeni; hesabın varlığını yalnızca denetim kaydında (istemciye değil) belirtir. */
    public enum LoginFailureReason { BAD_CREDENTIALS, UNKNOWN_ACCOUNT }

    /** Hasta/EMR kaydının hangi yolla görüntülendiği. */
    public enum AccessChannel { RECORD, SEARCH_RESULT }

    private final AuditEventStore store;
    private final Clock clock;

    public AuditService(AuditEventStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public void loginSucceeded(AuthenticatedStaff staff) {
        append(AuditAction.LOGIN_SUCCEEDED, staff, AuditResourceType.STAFF_ACCOUNT, staff.staffId().toString(),
            List.of(), Map.of());
    }

    /**
     * Başarısız giriş. Kimliği doğrulanmadığı için aktör yoktur. Girilen kullanıcı adı, yanlışlıkla
     * kullanıcı adı alanına yazılmış bir parola olabileceği için kaydedilmez; yalnızca eşleşen bir
     * hesap varsa onun kimliği kaydedilir.
     */
    public void loginFailed(UUID matchedAccountId, LoginFailureReason reason) {
        append(AuditAction.LOGIN_FAILED, null, AuditResourceType.STAFF_ACCOUNT,
            matchedAccountId != null ? matchedAccountId.toString() : null,
            List.of(), Map.of("reason", reason.name()));
    }

    public void logout(AuthenticatedStaff staff) {
        append(AuditAction.LOGOUT, staff, AuditResourceType.STAFF_ACCOUNT, staff.staffId().toString(),
            List.of(), Map.of());
    }

    public void created(AuthenticatedStaff actor, AuditResourceType type, String resourceId) {
        append(AuditAction.CREATE, requireActor(actor), type, resourceId, List.of(), Map.of());
    }

    /**
     * Güncelleme olayı. {@code changes} mutlaka {@link AuditChangeFilter} ile üretilmiş olmalıdır;
     * böylece yalnızca izin verilen alanların değerleri kaydedilir.
     */
    public void updated(AuthenticatedStaff actor, AuditResourceType type, String resourceId,
            List<FieldChange> changes) {
        append(AuditAction.UPDATE, requireActor(actor), type, resourceId, changes, Map.of());
    }

    public void deleted(AuthenticatedStaff actor, AuditResourceType type, String resourceId) {
        append(AuditAction.DELETE, requireActor(actor), type, resourceId, List.of(), Map.of());
    }

    public void cancelled(AuthenticatedStaff actor, AuditResourceType type, String resourceId) {
        append(AuditAction.CANCEL, requireActor(actor), type, resourceId, List.of(), Map.of());
    }

    /** Hasta kaydı görüntüleme (US-03). Kaydın içeriği değil, yalnızca kimliği kaydedilir. */
    public void patientRecordViewed(AuthenticatedStaff actor, UUID patientId, AccessChannel channel) {
        append(AuditAction.VIEW, requireActor(actor), AuditResourceType.PATIENT, patientId.toString(),
            List.of(), Map.of("accessChannel", channel.name()));
    }

    /**
     * Elektronik tıbbi kayıt (EMR) görüntüleme (US-03). Tıbbi içerik kaydedilmez; yalnızca kayıt ve
     * hasta kimliği tutulur. EMR uç noktaları (US-17.x/US-18, Sprint 3) bu metodu çağırmalıdır.
     */
    public void medicalRecordViewed(AuthenticatedStaff actor, String recordId, UUID patientId) {
        append(AuditAction.VIEW, requireActor(actor), AuditResourceType.MEDICAL_RECORD, recordId,
            List.of(), Map.of("patientId", patientId.toString(), "accessChannel", AccessChannel.RECORD.name()));
    }

    private void append(AuditAction action, AuthenticatedStaff actor, AuditResourceType type, String resourceId,
            List<FieldChange> changes, Map<String, String> details) {
        store.append(new AuditEvent(UUID.randomUUID(), clock.instant(), action, actor, type, resourceId,
            changes, details));
    }

    private static AuthenticatedStaff requireActor(AuthenticatedStaff actor) {
        return Objects.requireNonNull(actor, "Audited business operations require an authenticated actor");
    }
}
