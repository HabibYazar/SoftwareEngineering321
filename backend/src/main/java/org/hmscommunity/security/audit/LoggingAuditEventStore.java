package org.hmscommunity.security.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

/**
 * #140 kalıcı denetim deposu sağlanana kadar kullanılan GEÇİCİ uygulama.
 *
 * <p>Olayları ayrılmış {@code hms.audit} logger'ına yazar. Gizlilik gereği alanların önceki/sonraki
 * DEĞERLERİ loglanmaz; yalnızca değişen alan adları yazılır. Bu uygulama kalıcılık ve değiştirilemezlik
 * gereksinimlerini karşılamaz; yalnızca olay üretiminin izlenebilir olmasını sağlar.
 */
@Component
@Fallback
class LoggingAuditEventStore implements AuditEventStore {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("hms.audit");

    @Override
    public void append(AuditEvent event) {
        AUDIT_LOG.info("audit id={} at={} action={} resourceType={} resourceId={} actorId={} actorRole={} "
                + "changedFields={} details={}",
            event.id(), event.occurredAt(), event.action(), event.resourceType(), event.resourceId(),
            event.actor() != null ? event.actor().staffId() : null,
            event.actor() != null ? event.actor().role() : null,
            event.changes().stream().map(FieldChange::field).toList(),
            event.details());
    }
}
