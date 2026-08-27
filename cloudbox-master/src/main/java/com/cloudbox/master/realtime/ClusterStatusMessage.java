package com.cloudbox.master.realtime;

import java.time.Instant;
import java.util.UUID;

public record ClusterStatusMessage(
        String eventType,
        String resourceType,
        UUID resourceId,
        String previousStatus,
        String currentStatus,
        Instant occurredAt) {
}
