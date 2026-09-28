package co.inter.piggies.contracts;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record BuyOfferCreatedMessage(UUID eventId, String eventType, int schemaVersion, Instant occurredAt,
                                     Payload payload) {
    @Serdeable
    public record Payload(UUID offerId, UUID buyerId, String priceUsd, Instant createdAt) { }
}
