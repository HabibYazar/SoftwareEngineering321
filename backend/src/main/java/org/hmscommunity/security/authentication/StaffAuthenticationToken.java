package org.hmscommunity.security.authentication;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.authorization.Permission;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Geçerli bir bearer token ile doğrulanmış isteğin Spring Security kimliği.
 * Yetkiler, rol ({@code ROLE_*}) ve oturumun veritabanından okunan izinleridir ({@link Permission}).
 * Kimlik bilgisi (credentials) olarak ham token tutulmaz; çıkış için yalnızca oturum kimliği saklanır.
 */
public final class StaffAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedStaff staff;

    public StaffAuthenticationToken(StaffSession session) {
        super(authoritiesOf(session));
        this.staff = session.staff();
        super.setAuthenticated(true);
    }

    @Override
    public AuthenticatedStaff getPrincipal() {
        return staff;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    public UUID sessionId() {
        return staff.sessionId();
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        // Doğrulanmış durum yalnızca kurucu tarafından atanabilir; dışarıdan yükseltme engellenir.
        if (authenticated) {
            throw new IllegalArgumentException("Use the constructor to create an authenticated token");
        }
        super.setAuthenticated(false);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StaffAuthenticationToken that && super.equals(that);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    private static List<GrantedAuthority> authoritiesOf(StaffSession session) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(session.staff().role().authority()));
        session.permissions().stream().sorted()
            .forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission.authority())));
        return authorities;
    }
}
