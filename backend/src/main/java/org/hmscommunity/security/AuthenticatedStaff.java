package org.hmscommunity.security;

import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/**
 * Kimliği doğrulanmış personelin istek boyunca taşınan özeti.
 * Parola özeti veya token gibi gizli bilgi içermez; veritabanı aktör bağlamında ({@code hms_set_context})
 * ve denetim kayıtlarında işlemi yapan kişi olarak kullanılır.
 *
 * @param accountId kullanıcı hesabının kimliği ({@code user_accounts.id})
 * @param sessionId isteği yetkilendiren oturumun kimliği ({@code auth_sessions.id})
 */
public record AuthenticatedStaff(UUID accountId, String username, StaffRole role, UUID sessionId)
        implements Principal {

    public AuthenticatedStaff {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(sessionId, "sessionId");
    }

    @Override
    public String getName() {
        return username;
    }
}
