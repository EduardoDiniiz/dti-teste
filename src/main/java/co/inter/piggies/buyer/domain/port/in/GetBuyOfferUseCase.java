package co.inter.piggies.buyer.domain.port.in;

import co.inter.piggies.buyer.domain.model.BuyOffer;

import java.util.UUID;

public interface GetBuyOfferUseCase {

    BuyOffer get(UUID offerId);
}
