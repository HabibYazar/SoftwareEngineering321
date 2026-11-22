package org.hmscommunity.security.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LoggingAuditEventStoreTest {

    @Test
    void interimStoreLogsMetadataButNeverDetailValues(CapturedOutput output) {
        AuthenticatedStaff actor = new AuthenticatedStaff(UUID.randomUUID(), "test.receptionist",
            StaffRole.RECEPTIONIST, UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        AuditEvent event = new AuditEvent(UUID.randomUUID(), Instant.parse("2026-11-20T10:00:00Z"),
            AuditAction.VIEW, actor, AuditResourceType.PATIENT, patientId, Map.of("accessChannel", "SEARCH_RESULT"));

        new LoggingAuditEventStore().append(event);

        assertThat(output.getOut())
            .contains("action=VIEW", "resourceType=PATIENT", "resourceId=" + patientId, "detailKeys=[accessChannel]",
                "actorRole=RECEPTIONIST", event.id().toString())
            .doesNotContain("SEARCH_RESULT");
    }

    /** #145: denetim kayıtları iş API'leri üzerinden düzenlenemez veya silinemez. */
    @Test
    void auditStorePortIsAppendOnly() {
        List<String> methods = Arrays.stream(AuditEventStore.class.getDeclaredMethods())
            .filter(method -> Modifier.isAbstract(method.getModifiers()))
            .map(Method::getName)
            .toList();

        assertThat(methods).containsExactly("append");
    }
}
