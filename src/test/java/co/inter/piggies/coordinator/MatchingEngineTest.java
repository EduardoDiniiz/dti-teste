package co.inter.piggies.coordinator;

import co.inter.piggies.coordinator.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MatchingEngineTest {
    private final MatchingEngine engine = new MatchingEngine();
    private static final Instant TIME = Instant.parse("2026-09-28T18:00:00Z");

    private OrderOffer offer(OfferSide side, String price, long seconds) {
        return new OrderOffer(UUID.randomUUID(),side,UUID.randomUUID(),new BigDecimal(price),TIME.plusSeconds(seconds));
    }

    @Test void lowerBuyPriceDoesNotMatch() {
        assertThat(engine.bestCounterparty(offer(OfferSide.BUY,"10",0),List.of(offer(OfferSide.SELL,"20",1)))).isEmpty();
    }

    @Test void buySelectsLowestSellPriceBeforeAge() {
        var cheap = offer(OfferSide.SELL,"15",10);
        assertThat(engine.bestCounterparty(offer(OfferSide.BUY,"30",20),
                List.of(offer(OfferSide.SELL,"20",0),cheap))).contains(cheap);
    }

    @Test void sellSelectsHighestBuyPriceBeforeAge() {
        var high = offer(OfferSide.BUY,"35",10);
        assertThat(engine.bestCounterparty(offer(OfferSide.SELL,"20",20),
                List.of(offer(OfferSide.BUY,"30",0),high))).contains(high);
    }

    @Test void equalPricesChooseOldest() {
        var oldest = offer(OfferSide.SELL,"20",0);
        assertThat(engine.bestCounterparty(offer(OfferSide.BUY,"30",10),
                List.of(offer(OfferSide.SELL,"20",5),oldest))).contains(oldest);
    }

    @Test void equalPriceAndTimeUseStableIdTieBreak() {
        var first = new OrderOffer(UUID.fromString("00000000-0000-4000-8000-000000000001"),OfferSide.BUY,UUID.randomUUID(),new BigDecimal("30"),TIME);
        var second = new OrderOffer(UUID.fromString("00000000-0000-4000-8000-000000000002"),OfferSide.BUY,UUID.randomUUID(),new BigDecimal("30"),TIME);
        assertThat(engine.bestCounterparty(offer(OfferSide.SELL,"20",0),List.of(second,first))).contains(first);
    }

    @Test void sameSideAndIncompatibleOffersAreIgnored() {
        assertThat(engine.bestCounterparty(offer(OfferSide.SELL,"20",0),
                List.of(offer(OfferSide.SELL,"30",0),offer(OfferSide.BUY,"19",0)))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"30,20,25.00","20,20,20.00","20.01,20.00,20.00","20.03,20.00,20.02","0.02,0.01,0.02"})
    void executesAtHalfEvenAverage(String buy, String sell, String expected) {
        var trade = engine.execute(offer(OfferSide.BUY,buy,0),offer(OfferSide.SELL,sell,1),UUID.randomUUID(),TIME);
        assertThat(trade.price()).isEqualByComparingTo(expected);
        assertThat(trade.price().scale()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings={"0","-1","1.001","100000000000000000.00"})
    void invalidPricesAreRejected(String price) {
        assertThatThrownBy(() -> offer(OfferSide.BUY,price,0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void missingParticipantIsRejected() {
        assertThatThrownBy(() -> new OrderOffer(UUID.randomUUID(),OfferSide.BUY,null,BigDecimal.ONE,TIME))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void incompatibleTradeCannotBeCreated() {
        assertThatThrownBy(() -> engine.execute(offer(OfferSide.BUY,"10",0),offer(OfferSide.SELL,"20",0),UUID.randomUUID(),TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
