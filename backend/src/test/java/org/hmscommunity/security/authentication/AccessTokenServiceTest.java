package org.hmscommunity.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

class AccessTokenServiceTest {

    private static final AuthenticatedStaff STAFF =
        new AuthenticatedStaff(UUID.randomUUID(), "test.receptionist", StaffRole.RECEPTIONIST);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T08:00:00Z"));
    private final AccessTokenService service = new AccessTokenService(clock, Duration.ofHours(8));

    @Test
    void issuedTokenResolvesToTheSameStaffUntilExpiry() {
        IssuedAccessToken token = service.issue(STAFF);

        assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-11-20T16:00:00Z"));
        assertThat(service.resolve(token.value()))
            .hasValueSatisfying(session -> assertThat(session.staff()).isEqualTo(STAFF));
    }

    @Test
    void tokensAreHighEntropyAndUnique() {
        String first = service.issue(STAFF).value();
        String second = service.issue(STAFF).value();

        // 32 rastgele bayt, dolgusuz Base64URL ile 43 karakterdir.
        assertThat(first).hasSize(43).isNotEqualTo(second);
    }

    @Test
    void serverStoresOnlyTheHashOfTheToken() {
        IssuedAccessToken token = service.issue(STAFF);
        StaffSession session = service.resolve(token.value()).orElseThrow();

        assertThat(session.sessionKey()).isNotEqualTo(token.value()).hasSize(64);
        assertThat(session.toString()).doesNotContain(token.value());
        assertThat(token.toString()).doesNotContain(token.value());
    }

    @Test
    void expiredTokenIsRejectedAndDiscarded() {
        IssuedAccessToken token = service.issue(STAFF);

        clock.advance(Duration.ofHours(8));

        assertThat(service.resolve(token.value())).isEmpty();
        assertThat(service.activeSessionCount()).isZero();
    }

    @Test
    void revokedTokenCanNeverBeResolvedAgain() {
        IssuedAccessToken token = service.issue(STAFF);
        String sessionKey = service.resolve(token.value()).orElseThrow().sessionKey();

        assertThat(service.revoke(sessionKey)).isTrue();

        assertThat(service.resolve(token.value())).isEmpty();
        assertThat(service.revoke(sessionKey)).isFalse();
    }

    @Test
    void unknownOrBlankTokensAreRejected() {
        assertThat(service.resolve(null)).isEmpty();
        assertThat(service.resolve(" ")).isEmpty();
        assertThat(service.resolve("not-a-real-token")).isEmpty();
    }

    @Test
    void issuingPurgesExpiredSessions() {
        service.issue(STAFF);
        clock.advance(Duration.ofHours(9));

        service.issue(STAFF);

        assertThat(service.activeSessionCount()).isEqualTo(1);
    }

    @Test
    void nonPositiveLifetimeIsRejected() {
        assertThatThrownBy(() -> new AccessTokenService(clock, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
