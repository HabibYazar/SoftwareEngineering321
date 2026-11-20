package org.hmscommunity.security.authentication;

import java.util.List;
import org.hmscommunity.security.AuthenticatedStaff;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Geçerli bir bearer token ile doğrulanmış isteğin Spring Security kimliği.
 * Kimlik bilgisi (credentials) olarak ham token tutulmaz; çıkış işlemi için yalnızca
 * oturum anahtarı (token özeti) saklanır.
 */
public final class StaffAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedStaff staff;
    private final String sessionKey;

    public StaffAuthenticationToken(StaffSession session) {
        super(List.of(new SimpleGrantedAuthority(session.staff().role().authority())));
        this.staff = session.staff();
        this.sessionKey = session.sessionKey();
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

    public String sessionKey() {
        return sessionKey;
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
        return other instanceof StaffAuthenticationToken that && super.equals(that)
            && sessionKey.equals(that.sessionKey);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + sessionKey.hashCode();
    }
}
