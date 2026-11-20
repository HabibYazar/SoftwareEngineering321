package org.hmscommunity.security.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hmscommunity.common.persistence.ConditionalOnDatabase;
import org.hmscommunity.common.persistence.DatabaseActorContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uygulama düzeyindeki denetim olaylarını PostgreSQL'e yazar: {@code SELECT audit_record(...)}.
 *
 * <p>{@code audit_record} aktörü, aktörün o anki rolünü ve oturumu işlemin bağlamından
 * ({@code hms_set_context}) alır; bu yüzden bağlam aynı işlemde önce kurulur. Çağıranın işlemi varsa ona
 * katılır (örn. hasta görüntüleme), yoksa kendi işlemini açar. Tabloya doğrudan erişim yoktur; değiştirme
 * ve silme veritabanında her rol için reddedilir ({@code HL001}).
 */
@Repository
@ConditionalOnDatabase
class JdbcAuditEventStore implements AuditEventStore {

    private final JdbcClient jdbc;
    private final DatabaseActorContext actorContext;
    private final ObjectMapper objectMapper;

    JdbcAuditEventStore(JdbcClient jdbc, DatabaseActorContext actorContext, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.actorContext = actorContext;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void append(AuditEvent event) {
        actorContext.bind(event.actor().accountId(), event.actor().sessionId());
        jdbc.sql("SELECT audit_record(?, ?, ?, CAST(? AS jsonb))")
            .params(event.action().databaseCode(), event.resourceType().name(), event.resourceId(),
                event.details().isEmpty() ? null : toJson(event))
            .query((row, rowNumber) -> row.getLong(1))
            .single();
    }

    private String toJson(AuditEvent event) {
        try {
            return objectMapper.writeValueAsString(event.details());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Audit details cannot be serialized", ex);
        }
    }
}
