package co.inter.piggies.coordinator.application;

import co.inter.piggies.coordinator.domain.model.*;
import co.inter.piggies.coordinator.domain.port.in.CoordinateOfferUseCase;
import co.inter.piggies.coordinator.domain.port.out.*;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Matching and ledger changes belong exclusively to the Coordinator. */
public final class CoordinateOfferService implements CoordinateOfferUseCase {
    private final CoordinatorStore store;
    private final MatchingEngine matching;
    private final TradeEvents events;
    private final Clock clock;

    public CoordinateOfferService(CoordinatorStore store, MatchingEngine matching, TradeEvents events, Clock clock) {
        this.store = store;
        this.matching = matching;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public void coordinate(UUID eventId, OrderOffer offer) {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(offer, "offer is required");
        store.inTransaction(tx -> {
            if (!tx.claimEvent(eventId, clock.instant())) return;
            var existing = tx.findOffer(offer.id());
            if (existing.isPresent()) {
                if (!existing.get().equals(offer)) {
                    throw new IllegalArgumentException("offerId already exists with a different payload: " + offer.id());
                }
                // Same logical offer under another eventId must not enter the book again.
                return;
            }
            tx.addOffer(offer);
            matching.bestCounterparty(offer, tx.openCounterparties(offer)).ifPresent(counterparty -> {
                var trade = matching.execute(offer, counterparty, UUID.randomUUID(), clock.instant());
                tx.execute(trade);
                events.from(trade).forEach(tx::enqueue);
            });
        });
    }
}
