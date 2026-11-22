package org.hmscommunity.common.persistence;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * İşlemi yapan personeli veritabanına bildirir: {@code SELECT hms_set_context(:accountId, :sessionId)}.
 *
 * <p>Şema her yazma işleminde bu bağlamı zorunlu tutar (yoksa {@code HA401}); veritabanı izin kontrolleri
 * ({@code HA403}) ve tetikleyicilerle yazılan denetim kayıtlarındaki aktör/oturum bilgisi buradan gelir.
 * Değer işlem (transaction) yereldir; bu yüzden yalnızca etkin bir işlemin içinde ve o işlemin
 * yazmalarından önce çağrılmalıdır. İşlem dışında çağrılırsa bağlam hemen kaybolacağı için hata verilir.
 */
@Component
@ConditionalOnDatabase
public class DatabaseActorContext {

    private final JdbcClient jdbc;

    public DatabaseActorContext(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param accountId işlemi yapan kullanıcı hesabı ({@code user_accounts.id})
     * @param sessionId isteğin ait olduğu oturum ({@code auth_sessions.id})
     */
    public void bind(UUID accountId, UUID sessionId) {
        Objects.requireNonNull(accountId, "Database writes require an authenticated actor");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("hms_set_context must be called inside an active transaction");
        }
        jdbc.sql("SELECT hms_set_context(?, ?)")
            .params(accountId, sessionId)
            .query((resultSet, rowNumber) -> Boolean.TRUE)
            .single();
    }
}
