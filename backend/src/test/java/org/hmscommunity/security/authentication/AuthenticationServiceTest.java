package org.hmscommunity.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.InMemoryStaffAccountDirectory;
import org.hmscommunity.testsupport.SyntheticStaff;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthenticationServiceTest {

    private final InMemoryStaffAccountDirectory directory = new InMemoryStaffAccountDirectory();
    private final AccessTokenService tokens = new AccessTokenService(Clock.systemUTC(), Duration.ofHours(1));
    private final AuthenticationService service =
        new AuthenticationService(directory, new BCryptPasswordEncoder(4), tokens);

    @Test
    void validCredentialsIssueTokenWithStaffRole() {
        IssuedAccessToken token = service.login(SyntheticStaff.username(StaffRole.NURSE), SyntheticStaff.PASSWORD);

        assertThat(token.staff().role()).isEqualTo(StaffRole.NURSE);
        assertThat(token.staff().staffId()).isEqualTo(directory.get(StaffRole.NURSE).id());
        assertThat(tokens.resolve(token.value())).isPresent();
    }

    @Test
    void wrongPasswordIsRejectedWithoutIssuingToken() {
        String username = SyntheticStaff.username(StaffRole.NURSE);

        assertThatThrownBy(() -> service.login(username, "wrong-password"))
            .isInstanceOf(InvalidCredentialsException.class);
        assertThat(tokens.activeSessionCount()).isZero();
    }

    @Test
    void unknownUserGetsTheSameGenericError() {
        assertThatThrownBy(() -> service.login("nobody", SyntheticStaff.PASSWORD))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid username or password.");
        assertThat(tokens.activeSessionCount()).isZero();
    }

    @Test
    void usernameMatchIsExact() {
        String username = SyntheticStaff.username(StaffRole.NURSE).toUpperCase(java.util.Locale.ROOT);

        assertThatThrownBy(() -> service.login(username, SyntheticStaff.PASSWORD))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void logoutRevokesTheSession() {
        IssuedAccessToken token = service.login(SyntheticStaff.username(StaffRole.PHYSICIAN), SyntheticStaff.PASSWORD);
        StaffAuthenticationToken authentication = new StaffAuthenticationToken(tokens.resolve(token.value()).orElseThrow());

        service.logout(authentication);

        assertThat(tokens.resolve(token.value())).isEmpty();
    }
}
