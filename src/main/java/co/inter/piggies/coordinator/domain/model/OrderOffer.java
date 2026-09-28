package co.inter.piggies.coordinator.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** An immutable, validated offer for exactly one Piggy. */
public record OrderOffer(UUID id, OfferSide side, UUID participantId,
                         BigDecimal price, Instant createdAt) {
    public OrderOffer {
        Objects.requireNonNull(id, "offerId is required");
        Objects.requireNonNull(side, "side is required");
        Objects.requireNonNull(participantId, "participantId is required");
        Objects.requireNonNull(price, "price is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        try {
            price = price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("price must have at most two decimal places", e);
        }
        if (price.signum() <= 0 || price.precision() > 19) {
            throw new IllegalArgumentException("price must be positive and fit NUMERIC(19,2)");
        }
        // PostgreSQL TIMESTAMP(6): normalize once so event retries compare identically.
        createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
    }
}
