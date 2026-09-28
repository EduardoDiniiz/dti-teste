package co.inter.piggies.buyer.domain.exception;

import java.util.UUID;

public final class BuyOfferNotFoundException extends BuyerException {

    public BuyOfferNotFoundException(UUID offerId) {
        super("Buy offer %s not found".formatted(offerId));
    }
}
