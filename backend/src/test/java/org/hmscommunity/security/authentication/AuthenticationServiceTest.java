package org.hmscommunity.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.audit.AuditAction;
import org.hmscommunity.security.audit.AuditEvent;
import org.hmscommunity.security.audit.AuditService;
import org.hmscommunity.testsupport.InMemoryStaffAccountDirectory;
import org.hmscommunity.testsupport.RecordingAuditEventStore;
import org.hmscommunity.testsupport.SyntheticStaff;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthenticationServiceTest {

    private final InMemoryStaffAccountDirectory directory = new InMemoryStaffAccountDirectory();
    private final AccessTokenService tokens = new AccessTokenService(Clock.systemUTC(), Duration.ofHours(1));
    private final RecordingAuditEventStore auditStore = new RecordingAuditEventStore();
    private final AuthenticationService service = new AuthenticationService(
        directory, new BCryptPasswordEncoder(4), tokens, new AuditService(auditStore, Clock.systemUTC()));

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
        String username = SyntheticStaff.username(StaffRole.NURSE).toUpperCase(Locale.ROOT);

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

    // --- US-03 (#145): giriş/çıkış denetim olayları ---

    @Test
    void successfulLoginIsAudited() {
        IssuedAccessToken token = service.login(SyntheticStaff.username(StaffRole.NURSE), SyntheticStaff.PASSWORD);

        AuditEvent event = auditStore.events(AuditAction.LOGIN_SUCCEEDED).getFirst();
        assertThat(event.actor()).isEqualTo(token.staff());
        assertThat(event.resourceId()).isEqualTo(token.staff().staffId().toString());
        assertThat(event.toString()).doesNotContain(token.value()).doesNotContain(SyntheticStaff.PASSWORD);
    }

    @Test
    void failedLoginForExistingAccountRecordsAccountButNoActor() {
        String username = SyntheticStaff.username(StaffRole.NURSE);

        assertThatThrownBy(() -> service.login(username, "wrong-password"));

        AuditEvent event = auditStore.events(AuditAction.LOGIN_FAILED).getFirst();
        assertThat(event.actor()).isNull();
        assertThat(event.resourceId()).isEqualTo(directory.get(StaffRole.NURSE).id().toString());
        assertThat(event.details()).containsEntry("reason", "BAD_CREDENTIALS");
        assertThat(event.toString()).doesNotContain("wrong-password");
    }

    @Test
    void failedLoginForUnknownUserDoesNotStoreTheTypedUsername() {
        assertThatThrownBy(() -> service.login("maybe-a-mistyped-password", SyntheticStaff.PASSWORD));

        AuditEvent event = auditStore.events(AuditAction.LOGIN_FAILED).getFirst();
        assertThat(event.resourceId()).isNull();
        assertThat(event.details()).containsEntry("reason", "UNKNOWN_ACCOUNT");
        assertThat(event.toString()).doesNotContain("maybe-a-mistyped-password");
    }

    @Test
    void logoutIsAudited() {
        IssuedAccessToken token = service.login(SyntheticStaff.username(StaffRole.PHYSICIAN), SyntheticStaff.PASSWORD);

        service.logout(new StaffAuthenticationToken(tokens.resolve(token.value()).orElseThrow()));

        assertThat(auditStore.events(AuditAction.LOGOUT)).singleElement()
            .satisfies(event -> assertThat(event.actor()).isEqualTo(token.staff()));
    }

    @Test
    void loginIsRejectedAndTokenRevokedWhenAuditCannotBeWritten() {
        auditStore.failWith(new IllegalStateException("audit store down"));

        assertThatThrownBy(() -> service.login(SyntheticStaff.username(StaffRole.NURSE), SyntheticStaff.PASSWORD))
            .isInstanceOf(IllegalStateException.class);
        assertThat(tokens.activeSessionCount()).isZero();
    }
}
