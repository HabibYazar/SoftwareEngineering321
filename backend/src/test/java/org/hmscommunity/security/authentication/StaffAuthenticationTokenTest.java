package org.hmscommunity.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class StaffAuthenticationTokenTest {

    private final StaffSession session = new StaffSession("session-key-hash",
        new AuthenticatedStaff(UUID.randomUUID(), "test.nurse", StaffRole.NURSE), Instant.parse("2026-11-20T18:00:00Z"));

    @Test
    void carriesStaffAndRoleAuthorityButNoCredentials() {
        StaffAuthenticationToken token = new StaffAuthenticationToken(session);

        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getPrincipal()).isEqualTo(session.staff());
        assertThat(token.getName()).isEqualTo("test.nurse");
        assertThat(token.getCredentials()).isNull();
        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_NURSE");
        assertThat(token.sessionKey()).isEqualTo("session-key-hash");
    }

    @Test
    void cannotBeEscalatedToAuthenticatedFromOutside() {
        StaffAuthenticationToken token = new StaffAuthenticationToken(session);
        token.setAuthenticated(false);

        assertThatThrownBy(() -> token.setAuthenticated(true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(token.isAuthenticated()).isFalse();
    }

    @Test
    void equalityIncludesTheSession() {
        StaffSession otherSession = new StaffSession("other-hash", session.staff(), session.expiresAt());

        assertThat(new StaffAuthenticationToken(session)).isEqualTo(new StaffAuthenticationToken(session))
            .hasSameHashCodeAs(new StaffAuthenticationToken(session))
            .isNotEqualTo(new StaffAuthenticationToken(otherSession));
    }
}
