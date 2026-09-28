package co.inter.piggies.contracts;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contrato compartilhado legado; o evento v1 equivalente é TransactionCompleted. */
@Serdeable
public record TradeExecutedMessage(UUID eventId, UUID tradeId, UUID buyOfferId, UUID sellOfferId, UUID buyerId,
                                   UUID sellerId, BigDecimal price, Instant executedAt) {
}
