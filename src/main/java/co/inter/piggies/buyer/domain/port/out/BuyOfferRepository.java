package co.inter.piggies.buyer.domain.port.out;

import co.inter.piggies.buyer.domain.model.BuyOffer;

import java.util.Optional;
import java.util.UUID;

public interface BuyOfferRepository {

    Optional<BuyOffer> findById(UUID offerId);

    void insert(BuyOffer offer);
}
