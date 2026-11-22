package org.hmscommunity.security.authentication;

import java.util.Optional;
import org.hmscommunity.common.IntegrationPendingException;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

/**
 * Veritabanı yapılandırılmadığında ({@code HMS_DB_URL} yok) kullanılan fail-closed uygulama.
 * Hiçbir hesap uydurmaz ve hiçbir girişe izin vermez; her çağrı HTTP 503 ile sonuçlanır.
 * {@code JdbcStaffAccountDirectory} etkin olduğunda {@link Fallback} sayesinde kullanılmaz.
 */
@Component
@Fallback
class UnavailableStaffAccountDirectory implements StaffAccountDirectory {

    @Override
    public Optional<StaffAccount> findByUsername(String username) {
        throw new IntegrationPendingException("staff accounts (#114)");
    }
}
