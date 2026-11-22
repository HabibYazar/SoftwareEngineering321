package org.hmscommunity.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.InMemoryStaffSessionStore;
import org.hmscommunity.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

class AccessTokenServiceTest {

    private static final StaffAccount ACCOUNT = new StaffAccount(UUID.randomUUID(), "test.receptionist",
        "$2a$04$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyzABCDE", StaffRole.RECEPTIONIST);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T08:00:00Z"));
    private final InMemoryStaffSessionStore sessions = new InMemoryStaffSessionStore(clock);
    private final AccessTokenService service = new AccessTokenService(sessions, clock, Duration.ofHours(8));

    @Test
    void issuedTokenResolvesToTheSameStaffUntilExpiry() {
        IssuedAccessToken token = service.issue(ACCOUNT).orElseThrow();

        assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-11-20T16:00:00Z"));
        assertThat(token.staff().accountId()).isEqualTo(ACCOUNT.id());
        assertThat(service.resolve(token.value()))
            .hasValueSatisfying(session -> assertThat(session.staff()).isEqualTo(token.staff()));
    }

    @Test
    void tokensAreHighEntropyAndUnique() {
        String first = service.issue(ACCOUNT).orElseThrow().value();
        String second = service.issue(ACCOUNT).orElseThrow().value();

        // 32 rastgele bayt, dolgusuz Base64URL ile 43 karakterdir.
        assertThat(first).hasSize(43).isNotEqualTo(second);
    }

    @Test
    void onlyTheSha256OfTheTokenLeavesTheService() {
        IssuedAccessToken token = service.issue(ACCOUNT).orElseThrow();

        assertThat(AccessTokenService.tokenHash(token.value())).hasSize(32);
        assertThat(token.toString()).doesNotContain(token.value());
        assertThat(service.resolve(token.value()).orElseThrow().toString()).doesNotContain(token.value());
    }

    @Test
    void expiredTokenIsRejected() {
        IssuedAccessToken token = service.issue(ACCOUNT).orElseThrow();

        clock.advance(Duration.ofHours(8));

        assertThat(service.resolve(token.value())).isEmpty();
    }

    @Test
    void revokedTokenCanNeverBeResolvedAgain() {
        IssuedAccessToken token = service.issue(ACCOUNT).orElseThrow();
        UUID sessionId = service.resolve(token.value()).orElseThrow().sessionId();

        assertThat(service.revoke(sessionId)).isTrue();

        assertThat(service.resolve(token.value())).isEmpty();
        assertThat(service.revoke(sessionId)).isFalse();
    }

    @Test
    void noSessionIsOpenedForADeactivatedAccount() {
        sessions.deactivate(ACCOUNT.id());

        assertThat(service.issue(ACCOUNT)).isEmpty();
        assertThat(sessions.activeSessionCount()).isZero();
    }

    @Test
    void unknownBlankOrMalformedTokensAreRejected() {
        assertThat(service.resolve(null)).isEmpty();
        assertThat(service.resolve(" ")).isEmpty();
        assertThat(service.resolve("not-a-real-token")).isEmpty();
        assertThat(service.resolve("A".repeat(43))).isEmpty();
        assertThat(service.revoke(null)).isFalse();
    }

    @Test
    void nonPositiveLifetimeIsRejected() {
        assertThatThrownBy(() -> new AccessTokenService(sessions, clock, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
