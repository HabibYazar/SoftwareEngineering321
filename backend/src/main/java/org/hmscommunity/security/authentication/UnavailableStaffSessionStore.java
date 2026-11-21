package org.hmscommunity.security.authentication;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hmscommunity.common.IntegrationPendingException;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

/**
 * Veritabanı yapılandırılmadığında ({@code HMS_DB_URL} yok) kullanılan fail-closed uygulama.
 * Oturum açmaz; hiçbir oturum var olamayacağı için her token geçersizdir (HTTP 401).
 */
@Component
@Fallback
class UnavailableStaffSessionStore implements StaffSessionStore {

    private static final String INTEGRATION = "staff sessions (#114)";

    @Override
    public Optional<UUID> open(StaffAccount account, byte[] tokenHash, Instant expiresAt) {
        throw new IntegrationPendingException(INTEGRATION);
    }

    @Override
    public void recordFailedLogin(StaffAccount matchedAccount, LoginFailureReason reason) {
        throw new IntegrationPendingException(INTEGRATION);
    }

    @Override
    public Optional<StaffSession> findActive(byte[] tokenHash) {
        return Optional.empty();
    }

    @Override
    public boolean close(UUID sessionId) {
        return false;
    }
}
