package co.inter.piggies.contracts;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contrato compartilhado legado; os eventos v1 usam envelopes específicos por lado. */
@Serdeable
public record OfferCreatedMessage(UUID eventId, UUID offerId, String side, UUID participantId, BigDecimal price,
                                  Instant createdAt) {
}
