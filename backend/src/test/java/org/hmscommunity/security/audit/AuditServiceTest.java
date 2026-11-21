package org.hmscommunity.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.audit.AuditService.AccessChannel;
import org.hmscommunity.testsupport.MutableClock;
import org.hmscommunity.testsupport.RecordingAuditEventStore;
import org.junit.jupiter.api.Test;

class AuditServiceTest {

    private static final AuthenticatedStaff ACTOR = new AuthenticatedStaff(UUID.randomUUID(), "test.receptionist",
        StaffRole.RECEPTIONIST, UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-11-20T09:30:00Z");

    private final RecordingAuditEventStore store = new RecordingAuditEventStore();
    private final AuditService audit = new AuditService(store, new MutableClock(NOW));

    @Test
    void patientViewCarriesActorSessionResourceAndChannel() {
        UUID patientId = UUID.randomUUID();

        audit.patientRecordViewed(ACTOR, patientId, AccessChannel.SEARCH_RESULT);

        AuditEvent event = store.events().getFirst();
        assertThat(event.action()).isEqualTo(AuditAction.VIEW);
        assertThat(event.action().databaseCode()).isEqualTo("RECORD_VIEW");
        assertThat(event.actor()).isEqualTo(ACTOR);
        assertThat(event.resourceType()).isEqualTo(AuditResourceType.PATIENT);
        assertThat(event.resourceId()).isEqualTo(patientId);
        assertThat(event.details()).containsExactlyEntriesOf(Map.of("accessChannel", "SEARCH_RESULT"));
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void medicalRecordViewStoresOnlyIdentifiers() {
        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        audit.medicalRecordViewed(ACTOR, recordId, patientId);

        AuditEvent event = store.events().getFirst();
        assertThat(event.resourceType()).isEqualTo(AuditResourceType.MEDICAL_RECORD);
        assertThat(event.resourceId()).isEqualTo(recordId);
        assertThat(event.details()).containsOnlyKeys("patientId", "accessChannel");
    }

    @Test
    void everyEventGetsItsOwnIdentifier() {
        UUID patientId = UUID.randomUUID();

        audit.patientRecordViewed(ACTOR, patientId, AccessChannel.RECORD);
        audit.patientRecordViewed(ACTOR, patientId, AccessChannel.RECORD);

        assertThat(store.events()).extracting(AuditEvent::id).doesNotHaveDuplicates();
    }

    @Test
    void viewsRequireAnActor() {
        assertThatThrownBy(() -> audit.patientRecordViewed(null, UUID.randomUUID(), AccessChannel.RECORD))
            .isInstanceOf(NullPointerException.class);
        assertThat(store.events()).isEmpty();
    }

    @Test
    void storeFailureIsPropagatedToTheCaller() {
        store.failWith(new IllegalStateException("down"));

        assertThatThrownBy(() -> audit.patientRecordViewed(ACTOR, UUID.randomUUID(), AccessChannel.RECORD))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eventsAreImmutable() {
        audit.patientRecordViewed(ACTOR, UUID.randomUUID(), AccessChannel.RECORD);

        AuditEvent event = store.events().getFirst();
        assertThatThrownBy(() -> event.details().put("k", "v")).isInstanceOf(UnsupportedOperationException.class);
    }
}
