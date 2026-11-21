package org.hmscommunity.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.InMemoryStaffAccountDirectory;
import org.hmscommunity.testsupport.InMemoryStaffSessionStore;
import org.hmscommunity.testsupport.InMemoryStaffSessionStore.FailedLogin;
import org.hmscommunity.testsupport.SyntheticStaff;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthenticationServiceTest {

    private final InMemoryStaffAccountDirectory directory = new InMemoryStaffAccountDirectory();
    private final InMemoryStaffSessionStore sessions = new InMemoryStaffSessionStore(Clock.systemUTC());
    private final AccessTokenService tokens = new AccessTokenService(sessions, Clock.systemUTC(), Duration.ofHours(1));
    private final AuthenticationService service =
        new AuthenticationService(directory, new BCryptPasswordEncoder(4), tokens, sessions);

    @Test
    void validCredentialsIssueTokenWithStaffRole() {
        IssuedAccessToken token = service.login(SyntheticStaff.username(StaffRole.NURSE), SyntheticStaff.PASSWORD);

        assertThat(token.staff().role()).isEqualTo(StaffRole.NURSE);
        assertThat(token.staff().accountId()).isEqualTo(directory.get(StaffRole.NURSE).id());
        assertThat(tokens.resolve(token.value())).isPresent();
        assertThat(sessions.failedLogins()).isEmpty();
    }

    @Test
    void wrongPasswordIsRejectedWithoutIssuingToken() {
        String username = SyntheticStaff.username(StaffRole.NURSE);

        assertThatThrownBy(() -> service.login(username, "wrong-password"))
            .isInstanceOf(InvalidCredentialsException.class);
        assertThat(sessions.activeSessionCount()).isZero();
    }

    @Test
    void unknownUserGetsTheSameGenericError() {
        assertThatThrownBy(() -> service.login("nobody", SyntheticStaff.PASSWORD))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid username or password.");
        assertThat(sessions.activeSessionCount()).isZero();
    }

    @Test
    void deactivatedAccountGetsTheSameGenericErrorEvenWithTheRightPassword() {
        sessions.deactivate(directory.get(StaffRole.NURSE).id());

        assertThatThrownBy(() -> service.login(SyntheticStaff.username(StaffRole.NURSE), SyntheticStaff.PASSWORD))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid username or password.");
        assertThat(sessions.activeSessionCount()).isZero();
    }

    /** Veritabanı kullanıcı adlarını küçük harfle saklar ve {@code lower(username)} ile eşleştirir. */
    @Test
    void usernameMatchIsCaseInsensitiveAndIgnoresSurroundingWhitespace() {
        String username = "  " + SyntheticStaff.username(StaffRole.NURSE).toUpperCase(Locale.ROOT) + " ";

        IssuedAccessToken token = service.login(username, SyntheticStaff.PASSWORD);

        assertThat(token.staff().username()).isEqualTo(SyntheticStaff.username(StaffRole.NURSE));
    }

    @Test
    void passwordMatchStaysCaseSensitive() {
        String username = SyntheticStaff.username(StaffRole.NURSE);

        assertThatThrownBy(() -> service.login(username, SyntheticStaff.PASSWORD.toUpperCase(Locale.ROOT)))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void logoutRevokesTheSession() {
        IssuedAccessToken token = service.login(SyntheticStaff.username(StaffRole.PHYSICIAN), SyntheticStaff.PASSWORD);
        StaffAuthenticationToken authentication = new StaffAuthenticationToken(tokens.resolve(token.value()).orElseThrow());

        service.logout(authentication);

        assertThat(tokens.resolve(token.value())).isEmpty();
    }

    // --- US-03: başarısız giriş kaydına hangi bilginin gittiği (kayıt veritabanı fonksiyonunda yazılır) ---

    @Test
    void failedLoginForExistingAccountPassesOnlyTheMatchedAccount() {
        String username = SyntheticStaff.username(StaffRole.NURSE);

        assertThatThrownBy(() -> service.login(username, "wrong-password"));

        assertThat(sessions.failedLogins()).containsExactly(
            new FailedLogin(directory.get(StaffRole.NURSE), LoginFailureReason.BAD_CREDENTIALS));
    }

    @Test
    void failedLoginForUnknownUserNeverPassesTheTypedText() {
        assertThatThrownBy(() -> service.login("maybe-a-mistyped-password", SyntheticStaff.PASSWORD));

        assertThat(sessions.failedLogins()).containsExactly(new FailedLogin(null, LoginFailureReason.UNKNOWN_ACCOUNT));
    }

    @Test
    void noTokenIsIssuedWhenTheSessionStoreFails() {
        sessions.failWith(new IllegalStateException("database down"));

        assertThatThrownBy(() -> service.login(SyntheticStaff.username(StaffRole.NURSE), SyntheticStaff.PASSWORD))
            .isInstanceOf(IllegalStateException.class);
        assertThat(sessions.activeSessionCount()).isZero();
    }
}
