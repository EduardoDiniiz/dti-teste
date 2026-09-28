package co.inter.piggies.coordinator;

import co.inter.piggies.coordinator.application.CoordinateOfferService;
import co.inter.piggies.coordinator.domain.model.*;
import co.inter.piggies.coordinator.domain.port.out.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

/** Isolated application decisions; database atomicity is tested separately. */
class CoordinateOfferServiceTest {
    private final MemoryStore store = new MemoryStore();
    private final Instant time = Instant.parse("2026-09-28T18:00:00Z");
    private final CoordinateOfferService service = new CoordinateOfferService(store,new MatchingEngine(),
            trade -> List.of(new OutboxMessage(UUID.randomUUID(),"trades",trade.id().toString(),"{}",trade.executedAt()),
                    new OutboxMessage(UUID.randomUUID(),"rates","PIGGY-USD","{}",trade.executedAt())),Clock.fixed(time,ZoneOffset.UTC));
    private OrderOffer offer(OfferSide side,String price) {
        return new OrderOffer(UUID.randomUUID(),side,UUID.randomUUID(),new BigDecimal(price),time);
    }

    @Test void unmatchedOfferStaysInBookWithoutEvents() {
        var sell = offer(OfferSide.SELL,"20");
        service.coordinate(UUID.randomUUID(),sell);
        assertThat(store.offers).containsEntry(sell.id(),sell);
        assertThat(store.trades).isEmpty();
        assertThat(store.messages).isEmpty();
    }

    @Test void exampleTradesOnlyTheCompatibleBuyAndEnqueuesBothEvents() {
        var sell = offer(OfferSide.SELL,"20");
        var low = offer(OfferSide.BUY,"10");
        var high = offer(OfferSide.BUY,"30");
        service.coordinate(UUID.randomUUID(),sell);
        service.coordinate(UUID.randomUUID(),low);
        service.coordinate(UUID.randomUUID(),high);
        assertThat(store.trades).singleElement().satisfies(trade -> {
            assertThat(trade.buy()).isEqualTo(high);
            assertThat(trade.sell()).isEqualTo(sell);
            assertThat(trade.price()).isEqualByComparingTo("25.00");
            assertThat(trade.executedAt()).isEqualTo(time);
        });
        assertThat(store.closed).doesNotContain(low.id());
        assertThat(store.messages).hasSize(2);
    }

    @Test void repeatedEventDoesNotRepeatTrade() {
        var buy = offer(OfferSide.BUY,"30");
        var event = UUID.randomUUID();
        service.coordinate(UUID.randomUUID(),offer(OfferSide.SELL,"20"));
        service.coordinate(event,buy);
        service.coordinate(event,buy);
        service.coordinate(UUID.randomUUID(),buy);
        assertThat(store.trades).hasSize(1);
        assertThat(store.messages).hasSize(2);
    }

    @Test void conflictingOfferIdFailsInsteadOfChangingTheBook() {
        var original = offer(OfferSide.BUY,"30");
        service.coordinate(UUID.randomUUID(),original);
        var changed = new OrderOffer(original.id(),original.side(),original.participantId(),new BigDecimal("40"),time);
        assertThatThrownBy(() -> service.coordinate(UUID.randomUUID(),changed)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.offers.get(original.id())).isEqualTo(original);
    }

    private static class MemoryStore implements CoordinatorStore, CoordinatorStore.Transaction {
        final Map<UUID,OrderOffer> offers = new HashMap<>();
        final Set<UUID> processed = new HashSet<>(), closed = new HashSet<>();
        final List<Trade> trades = new ArrayList<>();
        final List<OutboxMessage> messages = new ArrayList<>();
        public void inTransaction(Consumer<Transaction> work) { work.accept(this); }
        public boolean claimEvent(UUID id,Instant time) { return processed.add(id); }
        public Optional<OrderOffer> findOffer(UUID id) { return Optional.ofNullable(offers.get(id)); }
        public void addOffer(OrderOffer offer) { offers.put(offer.id(),offer); }
        public List<OrderOffer> openCounterparties(OrderOffer incoming) { return offers.values().stream().filter(o -> !closed.contains(o.id())).toList(); }
        public void execute(Trade trade) { trades.add(trade); closed.add(trade.buy().id()); closed.add(trade.sell().id()); }
        public void enqueue(OutboxMessage event) { messages.add(event); }
    }
}
