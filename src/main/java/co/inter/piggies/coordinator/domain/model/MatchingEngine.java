package co.inter.piggies.coordinator.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pure domain logic: no database, Kafka or framework dependency. */
public final class MatchingEngine {
    public Optional<OrderOffer> bestCounterparty(OrderOffer incoming, List<OrderOffer> openOffers) {
        Comparator<OrderOffer> byPrice = Comparator.comparing(OrderOffer::price);
        if (incoming.side() == OfferSide.SELL) byPrice = byPrice.reversed();
        return openOffers.stream()
                .filter(candidate -> candidate.side() == incoming.side().opposite())
                .filter(candidate -> !candidate.id().equals(incoming.id()))
                .filter(candidate -> incoming.side() == OfferSide.BUY
                        ? incoming.price().compareTo(candidate.price()) >= 0
                        : candidate.price().compareTo(incoming.price()) >= 0)
                .min(byPrice.thenComparing(OrderOffer::createdAt)
                        .thenComparing(offer -> offer.id().toString()));
    }

    public Trade execute(OrderOffer incoming, OrderOffer counterparty, UUID tradeId, Instant time) {
        OrderOffer buy = incoming.side() == OfferSide.BUY ? incoming : counterparty;
        OrderOffer sell = incoming.side() == OfferSide.SELL ? incoming : counterparty;
        BigDecimal average = buy.price().add(sell.price()).divide(BigDecimal.TWO, 2, RoundingMode.HALF_EVEN);
        return new Trade(tradeId, buy, sell, average, time);
    }
}
