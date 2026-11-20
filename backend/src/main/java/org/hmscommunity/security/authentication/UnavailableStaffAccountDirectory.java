package org.hmscommunity.security.authentication;

import java.util.Optional;
import org.hmscommunity.common.IntegrationPendingException;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

/**
 * #114 veri modeli entegre edilene kadar kullanılan geçici (fail-closed) uygulama.
 * Hiçbir hesap uydurmaz ve hiçbir girişe izin vermez; her çağrı HTTP 503 ile sonuçlanır.
 * Başka bir {@link StaffAccountDirectory} bean'i tanımlandığında {@link Fallback} sayesinde kullanılmaz.
 */
@Component
@Fallback
class UnavailableStaffAccountDirectory implements StaffAccountDirectory {

    @Override
    public Optional<StaffAccount> findByUsername(String username) {
        throw new IntegrationPendingException("staff accounts (#114)");
    }
}
