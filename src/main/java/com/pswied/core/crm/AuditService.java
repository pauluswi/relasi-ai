package com.pswied.core.crm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** In-memory audit trail for the local synthetic-data showcase. */
public final class AuditService {
    private final List<AuditEvent> events = new ArrayList<>();

    public synchronized void record(String actorId, String action, String target, String outcome) {
        events.add(new AuditEvent(Instant.now().toString(), actorId, action, target, outcome));
    }

    public synchronized List<AuditEvent> events() {
        return List.copyOf(events);
    }

    public synchronized void reset() {
        events.clear();
    }

    public record AuditEvent(String occurredAt, String actorId, String action, String target, String outcome) {
    }
}
