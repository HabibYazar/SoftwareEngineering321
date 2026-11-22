package org.hmscommunity.testsupport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hmscommunity.security.audit.AuditAction;
import org.hmscommunity.security.audit.AuditEvent;
import org.hmscommunity.security.audit.AuditEventStore;

/** #140 kalıcı denetim deposu yerine, üretilen olayları bellekte toplayan test dublörü. */
public class RecordingAuditEventStore implements AuditEventStore {

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    @Override
    public void append(AuditEvent event) {
        if (failure != null) {
            throw failure;
        }
        events.add(event);
    }

    public List<AuditEvent> events() {
        return List.copyOf(events);
    }

    public List<AuditEvent> events(AuditAction action) {
        return events.stream().filter(event -> event.action() == action).toList();
    }

    /** Depo arızasını taklit eder; {@code null} ile normale döner. */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public void clear() {
        events.clear();
        failure = null;
    }
}
