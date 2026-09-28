package co.inter.piggies.buyer.application;

import co.inter.piggies.buyer.domain.exception.BuyOfferNotFoundException;
import co.inter.piggies.buyer.domain.model.BuyOffer;
import co.inter.piggies.buyer.domain.port.in.CreateBuyOfferUseCase;
import co.inter.piggies.buyer.domain.port.in.GetBuyOfferUseCase;
import co.inter.piggies.buyer.domain.port.out.BuyOfferEventPublisher;
import co.inter.piggies.buyer.domain.port.out.BuyOfferRepository;
import co.inter.piggies.shared.domain.port.out.TransactionRunner;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

/**
 * Borda assíncrona: gera o lance, grava a oferta e o evento (outbox) na MESMA transação e devolve na hora.
 * Juntar compra e venda é outra rotina (coordinator).
 */
@Singleton
public class BuyOfferService implements CreateBuyOfferUseCase, GetBuyOfferUseCase {

    private final BuyOfferRepository offers;
    private final BuyOfferEventPublisher publisher;
    private final TransactionRunner tx;
    private final Clock clock;

    public BuyOfferService(BuyOfferRepository offers, BuyOfferEventPublisher publisher, TransactionRunner tx,
                           Clock clock) {
        this.offers = offers;
        this.publisher = publisher;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public BuyOffer create(UUID buyerId, BigDecimal priceUsd) {
        BuyOffer offer = BuyOffer.create(buyerId, priceUsd, clock);
        return tx.inTransaction(() -> {
            offers.insert(offer);
            publisher.offerCreated(offer);
            return offer;
        });
    }

    @Override
    public BuyOffer get(UUID offerId) {
        return tx.inTransaction(() -> offers.findById(offerId)
                .orElseThrow(() -> new BuyOfferNotFoundException(offerId)));
    }
}
