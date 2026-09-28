package co.inter.piggies.contracts;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contrato: contracts/events/trade-executed.schema.json (tópico slp.trades.executed). */
@Serdeable
public record TradeExecutedMessage(UUID eventId, UUID tradeId, UUID buyOfferId, UUID sellOfferId, UUID buyerId,
                                   UUID sellerId, BigDecimal price, Instant executedAt) {
}
