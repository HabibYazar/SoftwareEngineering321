package org.hmscommunity.security;

import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/**
 * Kimliği doğrulanmış personelin istek boyunca taşınan özeti.
 * Parola özeti veya token gibi gizli bilgi içermez; denetim kayıtlarında aktör olarak kullanılır.
 */
public record AuthenticatedStaff(UUID staffId, String username, StaffRole role) implements Principal {

    public AuthenticatedStaff {
        Objects.requireNonNull(staffId, "staffId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(role, "role");
    }

    @Override
    public String getName() {
        return username;
    }
}
