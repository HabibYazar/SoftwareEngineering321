package org.hmscommunity.testsupport;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Veritabanısız testlerde kalıcılık portlarının yerine geçen test dublörleri.
 * Üretimdeki geçici uygulamalar {@code @Fallback} olduğundan buradaki bean'ler otomatik olarak tercih edilir;
 * PostgreSQL adaptörleri de aynı mekanizmayla devreye girer.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDoublesConfiguration {

    @Bean
    InMemoryStaffAccountDirectory inMemoryStaffAccountDirectory() {
        return new InMemoryStaffAccountDirectory();
    }

    @Bean
    InMemoryStaffSessionStore inMemoryStaffSessionStore(Clock clock) {
        return new InMemoryStaffSessionStore(clock);
    }

    @Bean
    RecordingAuditEventStore recordingAuditEventStore() {
        return new RecordingAuditEventStore();
    }

    @Bean
    InMemoryPatientRepository inMemoryPatientRepository() {
        return new InMemoryPatientRepository();
    }
}
