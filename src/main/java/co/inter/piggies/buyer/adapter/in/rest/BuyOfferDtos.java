package co.inter.piggies.buyer.adapter.in.rest;

import co.inter.piggies.buyer.domain.model.BuyOffer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** DTOs da API do buyer (contracts/buyer.openapi.json). Nunca saem do pacote adapter. */
final class BuyOfferDtos {

    /** Mesmo pattern do contrato: USD positivo, até 12 dígitos inteiros e 2 casas, sem expoente. */
    static final String PRICE_PATTERN = "^(?!0(?:\\.0{1,2})?$)(?:0|[1-9][0-9]{0,11})(?:\\.[0-9]{1,2})?$";

    private BuyOfferDtos() {
    }

    /** additionalProperties: false no contrato → campo desconhecido (inclusive offerId) é rejeitado. */
    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = false)
    record CreateBuyOfferRequest(@Nullable UUID buyerId,
                                 @NotNull @Pattern(regexp = PRICE_PATTERN) String priceUsd) {
    }

    /** offerId gerado pelo servidor: é assim que o cliente sabe qual lance acompanhar. */
    @Serdeable
    record AcceptedOfferResponse(UUID offerId, String status, String statusUrl) {
    }

    /** Offer = PendingOffer | ExecutedOffer | RejectedOffer; campos nulos não são serializados. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OfferResponse(UUID offerId, UUID buyerId, String priceUsd, Instant createdAt, String status,
                         UUID transactionId, String executionPriceUsd, String rejectionCode,
                         String rejectionReason) {

        static OfferResponse from(BuyOffer offer) {
            String executionPrice = offer.executionPriceUsd() == null ? null
                    : offer.executionPriceUsd().setScale(3, RoundingMode.UNNECESSARY).toPlainString();
            return new OfferResponse(offer.offerId(), offer.buyerId(), offer.priceUsd().toPlainString(),
                    offer.createdAt(), offer.status().name(), offer.transactionId(), executionPrice,
                    offer.rejectionCode(), offer.rejectionReason());
        }
    }
}
