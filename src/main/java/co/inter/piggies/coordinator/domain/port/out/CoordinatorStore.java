package co.inter.piggies.coordinator.domain.port.out;

import co.inter.piggies.coordinator.domain.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface CoordinatorStore {
    /** All effects, including event deduplication and outbox writes, commit together. */
    void inTransaction(Consumer<Transaction> work);

    interface Transaction {
        boolean claimEvent(UUID eventId, Instant processedAt);
        Optional<OrderOffer> findOffer(UUID offerId);
        void addOffer(OrderOffer offer);
        List<OrderOffer> openCounterparties(OrderOffer incoming);
        void execute(Trade trade);
        void enqueue(OutboxMessage message);
    }
}
