package org.hmscommunity.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.authorization.Permission;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class StaffAuthenticationTokenTest {

    private final StaffSession session = new StaffSession(
        new AuthenticatedStaff(UUID.randomUUID(), "test.receptionist", StaffRole.RECEPTIONIST, UUID.randomUUID()),
        Instant.parse("2026-11-20T18:00:00Z"),
        Set.of(Permission.PATIENT_VIEW, Permission.PATIENT_CREATE));

    @Test
    void carriesStaffRoleAndDatabasePermissionsButNoCredentials() {
        StaffAuthenticationToken token = new StaffAuthenticationToken(session);

        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getPrincipal()).isEqualTo(session.staff());
        assertThat(token.getName()).isEqualTo("test.receptionist");
        assertThat(token.getCredentials()).isNull();
        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_RECEPTIONIST", "PATIENT_CREATE", "PATIENT_VIEW");
        assertThat(token.sessionId()).isEqualTo(session.sessionId());
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
        AuthenticatedStaff sameStaffOtherSession = new AuthenticatedStaff(session.staff().accountId(),
            session.staff().username(), session.staff().role(), UUID.randomUUID());
        StaffSession otherSession = new StaffSession(sameStaffOtherSession, session.expiresAt(), session.permissions());

        assertThat(new StaffAuthenticationToken(session)).isEqualTo(new StaffAuthenticationToken(session))
            .hasSameHashCodeAs(new StaffAuthenticationToken(session))
            .isNotEqualTo(new StaffAuthenticationToken(otherSession));
    }
}
