package org.hmscommunity.security.authentication;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.authorization.Permission;

/**
 * Doğrulanmış, hâlâ geçerli bir oturum ({@code auth_validate_session} sonucu).
 *
 * @param permissions oturum sahibinin İSTEK ANINDAKİ izinleri (veritabanındaki {@code has_permission});
 *                    rol veya matris değişikliği bir sonraki istekte hemen etkili olur
 */
public record StaffSession(AuthenticatedStaff staff, Instant expiresAt, Set<Permission> permissions) {

    public StaffSession {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(expiresAt, "expiresAt");
        permissions = Set.copyOf(permissions);
    }

    public UUID sessionId() {
        return staff.sessionId();
    }
}
