package org.hmscommunity.security.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

/**
 * Veritabanı yapılandırılmadığında ({@code HMS_DB_URL} yok) kullanılan geçici uygulama.
 *
 * <p>Olayları ayrılmış {@code hms.audit} logger'ına yazar; ayrıntı değerleri değil yalnızca anahtarları
 * loglanır. Kalıcılık ve değiştirilemezlik gereksinimlerini karşılamaz. Pratikte devreye girmez: veritabanı
 * olmadan giriş yapılamadığı için görüntülenecek kayıt da yoktur. {@code JdbcAuditEventStore} etkin
 * olduğunda {@link Fallback} sayesinde kullanılmaz.
 */
@Component
@Fallback
class LoggingAuditEventStore implements AuditEventStore {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("hms.audit");

    @Override
    public void append(AuditEvent event) {
        AUDIT_LOG.info("audit id={} at={} action={} resourceType={} resourceId={} actorId={} actorRole={} "
                + "detailKeys={}",
            event.id(), event.occurredAt(), event.action(), event.resourceType(), event.resourceId(),
            event.actor().accountId(), event.actor().role(), event.details().keySet().stream().sorted().toList());
    }
}
