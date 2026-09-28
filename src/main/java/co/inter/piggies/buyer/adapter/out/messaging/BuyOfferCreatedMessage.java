package co.inter.piggies.buyer.adapter.out.messaging;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** Contrato: contracts/events/buy-offer-created.schema.json (envelope v1 + payload). */
@Serdeable
public record BuyOfferCreatedMessage(UUID eventId, String eventType, int schemaVersion, Instant occurredAt,
                                     Payload payload) {

    public static final String EVENT_TYPE = "BuyOfferCreated";
    public static final int SCHEMA_VERSION = 1;

    /** priceUsd é string no contrato (sem expoente, convertida com BigDecimal pelo consumidor). */
    @Serdeable
    public record Payload(UUID offerId, UUID buyerId, String priceUsd, Instant createdAt) {
    }
}
