package co.inter.piggies.contracts;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contrato: contracts/events/exchange-rate-updated.schema.json (tópico slp.exchange-rate.updated). */
@Serdeable
public record ExchangeRateUpdatedMessage(UUID eventId, UUID tradeId, String pair, BigDecimal rate,
                                         Instant occurredAt) {
}
