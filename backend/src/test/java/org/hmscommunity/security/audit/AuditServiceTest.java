package org.hmscommunity.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.audit.AuditService.AccessChannel;
import org.hmscommunity.testsupport.MutableClock;
import org.hmscommunity.testsupport.RecordingAuditEventStore;
import org.junit.jupiter.api.Test;

class AuditServiceTest {

    private static final AuthenticatedStaff ACTOR =
        new AuthenticatedStaff(UUID.randomUUID(), "test.receptionist", StaffRole.RECEPTIONIST);
    private static final Instant NOW = Instant.parse("2026-11-20T09:30:00Z");

    private final RecordingAuditEventStore store = new RecordingAuditEventStore();
    private final AuditService audit = new AuditService(store, new MutableClock(NOW));

    @Test
    void everyRequiredOperationProducesATraceableEvent() {
        String id = UUID.randomUUID().toString();

        audit.loginSucceeded(ACTOR);
        audit.loginFailed(null, AuditService.LoginFailureReason.UNKNOWN_ACCOUNT);
        audit.logout(ACTOR);
        audit.created(ACTOR, AuditResourceType.PATIENT, id);
        audit.updated(ACTOR, AuditResourceType.PATIENT, id, List.of(new FieldChange("phone", "1", "2")));
        audit.deleted(ACTOR, AuditResourceType.PATIENT, id);
        audit.cancelled(ACTOR, AuditResourceType.APPOINTMENT, id);
        audit.patientRecordViewed(ACTOR, UUID.fromString(id), AccessChannel.RECORD);
        audit.medicalRecordViewed(ACTOR, "note-1", UUID.fromString(id));

        assertThat(store.events()).extracting(AuditEvent::action).containsExactly(
            AuditAction.LOGIN_SUCCEEDED, AuditAction.LOGIN_FAILED, AuditAction.LOGOUT, AuditAction.CREATE,
            AuditAction.UPDATE, AuditAction.DELETE, AuditAction.CANCEL, AuditAction.VIEW, AuditAction.VIEW);
        assertThat(store.events()).allSatisfy(event -> {
            assertThat(event.id()).isNotNull();
            assertThat(event.occurredAt()).isEqualTo(NOW);
        });
        assertThat(store.events()).extracting(AuditEvent::id).doesNotHaveDuplicates();
    }

    @Test
    void businessEventsCarryActorRoleAndResource() {
        UUID patientId = UUID.randomUUID();

        audit.created(ACTOR, AuditResourceType.PATIENT, patientId.toString());

        AuditEvent event = store.events().getFirst();
        assertThat(event.actor().staffId()).isEqualTo(ACTOR.staffId());
        assertThat(event.actor().role()).isEqualTo(StaffRole.RECEPTIONIST);
        assertThat(event.resourceType()).isEqualTo(AuditResourceType.PATIENT);
        assertThat(event.resourceId()).isEqualTo(patientId.toString());
    }

    @Test
    void medicalRecordViewStoresOnlyIdentifiers() {
        UUID patientId = UUID.randomUUID();

        audit.medicalRecordViewed(ACTOR, "encounter-42", patientId);

        AuditEvent event = store.events().getFirst();
        assertThat(event.resourceType()).isEqualTo(AuditResourceType.MEDICAL_RECORD);
        assertThat(event.resourceId()).isEqualTo("encounter-42");
        assertThat(event.details()).containsOnlyKeys("patientId", "accessChannel");
        assertThat(event.changes()).isEmpty();
    }

    @Test
    void businessOperationsRequireAnActor() {
        assertThatThrownBy(() -> audit.created(null, AuditResourceType.PATIENT, "x"))
            .isInstanceOf(NullPointerException.class);
        assertThat(store.events()).isEmpty();
    }

    @Test
    void storeFailureIsPropagatedToTheCaller() {
        store.failWith(new IllegalStateException("down"));

        assertThatThrownBy(() -> audit.logout(ACTOR)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eventsAreImmutable() {
        audit.updated(ACTOR, AuditResourceType.PATIENT, "x", new java.util.ArrayList<>(List.of(
            new FieldChange("phone", "1", "2"))));

        AuditEvent event = store.events().getFirst();
        assertThatThrownBy(() -> event.changes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> event.details().put("k", "v")).isInstanceOf(UnsupportedOperationException.class);
    }
}
