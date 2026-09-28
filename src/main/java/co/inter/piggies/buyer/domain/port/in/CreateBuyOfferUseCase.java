package co.inter.piggies.buyer.domain.port.in;

import co.inter.piggies.buyer.domain.model.BuyOffer;

import java.math.BigDecimal;
import java.util.UUID;

public interface CreateBuyOfferUseCase {

    /** Gera o lance de compra (PENDING) para processamento assíncrono; o match é feito pelo coordinator. */
    BuyOffer create(UUID buyerId, BigDecimal priceUsd);
}
