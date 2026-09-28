package co.inter.piggies.coordinator.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Trade(UUID id, OrderOffer buy, OrderOffer sell,
                    BigDecimal price, Instant executedAt) {
    public Trade {
        Objects.requireNonNull(id);
        Objects.requireNonNull(buy);
        Objects.requireNonNull(sell);
        Objects.requireNonNull(price);
        Objects.requireNonNull(executedAt);
        if (buy.side() != OfferSide.BUY || sell.side() != OfferSide.SELL
                || buy.id().equals(sell.id()) || buy.price().compareTo(sell.price()) < 0) {
            throw new IllegalArgumentException("Trade requires two distinct compatible offers");
        }
        var expected = buy.price().add(sell.price()).divide(BigDecimal.TWO, 2, RoundingMode.HALF_EVEN);
        if (expected.compareTo(price) != 0) {
            throw new IllegalArgumentException("Trade price must be the HALF_EVEN average");
        }
    }
}
