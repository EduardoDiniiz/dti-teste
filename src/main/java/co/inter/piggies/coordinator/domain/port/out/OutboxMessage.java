package co.inter.piggies.coordinator.domain.port.out;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(UUID eventId, String topic, String key, String payload, Instant createdAt) { }
