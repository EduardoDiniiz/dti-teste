package co.inter.piggies.contracts;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contrato: contracts/events/offer-created.schema.json (tópico slp.offers.created). side = BUY | SELL. */
@Serdeable
public record OfferCreatedMessage(UUID eventId, UUID offerId, String side, UUID participantId, BigDecimal price,
                                  Instant createdAt) {
}
