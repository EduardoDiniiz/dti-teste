package co.inter.piggies.buyer.domain.port.out;

import co.inter.piggies.buyer.domain.model.BuyOffer;

/** Publica eventos do buyer. Participa da transação corrente (a implementação grava no outbox). */
public interface BuyOfferEventPublisher {

    void offerCreated(BuyOffer offer);
}
