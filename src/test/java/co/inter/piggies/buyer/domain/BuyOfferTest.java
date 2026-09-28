package co.inter.piggies.buyer.domain;

import co.inter.piggies.buyer.domain.exception.InvalidPriceException;
import co.inter.piggies.buyer.domain.model.BuyOffer;
import co.inter.piggies.buyer.domain.model.BuyOfferStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuyOfferTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-28T18:00:00Z"), ZoneOffset.UTC);
    private final UUID buyerId = UUID.randomUUID();

    @Test
    void createsPendingWithGeneratedIdAndPriceNormalizedToTwoDecimals() {
        BuyOffer offer = BuyOffer.create(buyerId, new BigDecimal("30"), clock);

        assertThat(offer.offerId()).isNotNull();
        assertThat(offer.buyerId()).isEqualTo(buyerId);
        assertThat(offer.status()).isEqualTo(BuyOfferStatus.PENDING);
        assertThat(offer.priceUsd().toPlainString()).isEqualTo("30.00");
        assertThat(offer.createdAt()).isEqualTo(Instant.parse("2026-09-28T18:00:00Z"));
    }

    @Test
    void generatesBuyerIdWhenNotInformed() {
        BuyOffer offer = BuyOffer.create(null, new BigDecimal("30"), clock);

        assertThat(offer.offerId()).isNotNull();
        assertThat(offer.buyerId()).isNotNull();
    }

    @Test
    void eachCreateGeneratesANewOffer() {
        BuyOffer first = BuyOffer.create(buyerId, new BigDecimal("30"), clock);
        BuyOffer second = BuyOffer.create(buyerId, new BigDecimal("30"), clock);

        assertThat(first.offerId()).isNotEqualTo(second.offerId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "10.001", "1234567890123"})
    void rejectsInvalidPrices(String price) {
        assertThatThrownBy(() -> BuyOffer.create(buyerId, new BigDecimal(price), clock))
                .isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void acceptsTwelveIntegerDigits() {
        assertThat(BuyOffer.create(buyerId, new BigDecimal("999999999999.99"), clock).priceUsd())
                .isEqualByComparingTo("999999999999.99");
    }
}
