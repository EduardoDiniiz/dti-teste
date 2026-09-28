package co.inter.piggies.buyer.adapter.in.rest;

import co.inter.piggies.buyer.adapter.in.rest.BuyOfferDtos.AcceptedOfferResponse;
import co.inter.piggies.buyer.adapter.in.rest.BuyOfferDtos.CreateBuyOfferRequest;
import co.inter.piggies.buyer.adapter.in.rest.BuyOfferDtos.OfferResponse;
import co.inter.piggies.buyer.domain.model.BuyOffer;
import co.inter.piggies.buyer.domain.port.in.CreateBuyOfferUseCase;
import co.inter.piggies.buyer.domain.port.in.GetBuyOfferUseCase;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.UUID;

/** Borda assíncrona do comprador: gera o lance e responde 202 sem esperar o match. */
@Controller("/buy-offers")
@ExecuteOn(TaskExecutors.BLOCKING)
class BuyOfferController {

    private final CreateBuyOfferUseCase createOffer;
    private final GetBuyOfferUseCase getOffer;

    BuyOfferController(CreateBuyOfferUseCase createOffer, GetBuyOfferUseCase getOffer) {
        this.createOffer = createOffer;
        this.getOffer = getOffer;
    }

    /** Recebe comprador + valor, gera o lance (PENDING) e devolve 202 + Location para consultar o status. */
    @Post
    HttpResponse<AcceptedOfferResponse> create(@Body @Valid CreateBuyOfferRequest request) {
        BuyOffer offer = createOffer.create(request.buyerId(), new BigDecimal(request.priceUsd()));
        String statusUrl = "/buy-offers/" + offer.offerId();
        return HttpResponse.<AcceptedOfferResponse>accepted()
                .header(HttpHeaders.LOCATION, statusUrl)
                .body(new AcceptedOfferResponse(offer.offerId(), offer.status().name(), statusUrl));
    }

    @Get("/{offerId}")
    OfferResponse get(UUID offerId) {
        return OfferResponse.from(getOffer.get(offerId));
    }
}
