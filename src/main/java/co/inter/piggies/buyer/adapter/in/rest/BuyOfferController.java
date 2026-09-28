package co.inter.piggies.buyer.adapter.in.rest;

<<<<<<< HEAD
import co.inter.piggies.buyer.application.BuyOfferService;
import co.inter.piggies.buyer.domain.model.BuyOfferEntity;
import co.inter.piggies.buyer.domain.model.BuyOfferStatus;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
=======
import co.inter.piggies.buyer.adapter.in.rest.BuyOfferDtos.AcceptedOfferResponse;
import co.inter.piggies.buyer.adapter.in.rest.BuyOfferDtos.CreateBuyOfferRequest;
import co.inter.piggies.buyer.adapter.in.rest.BuyOfferDtos.OfferResponse;
import co.inter.piggies.buyer.domain.model.BuyOffer;
import co.inter.piggies.buyer.domain.port.in.CreateBuyOfferUseCase;
import co.inter.piggies.buyer.domain.port.in.GetBuyOfferUseCase;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
>>>>>>> efdc1b8768b932c2ccfa09ea4d4e648a1dc42593
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
<<<<<<< HEAD
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.HttpException;
import io.micronaut.http.uri.UriTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Controller
@Produces(MediaType.APPLICATION_JSON)
public class BuyOfferController {
    private static final String PRICE_PATTERN = "^(?!0(?:\\.0{1,2})?$)(?:0|[1-9][0-9]{0,11})(?:\\.[0-9]{1,2})?$";
    private final BuyOfferService service;

    public BuyOfferController(BuyOfferService service) {
        this.service = service;
    }

    @Post("/buy-offers")
    public HttpResponse<?> create(@Body CreateOfferRequest request) {
        if (request == null || request.offerId() == null || request.buyerId() == null
                || request.priceUsd() == null || !request.priceUsd().matches(PRICE_PATTERN)) {
            return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "JSON, UUID ou preço inválido.");
        }
        BuyOfferService.AcceptedOffer accepted;
        try {
            accepted = service.accept(request.offerId(), request.buyerId(), new BigDecimal(request.priceUsd()));
        } catch (BuyOfferService.OfferConflictException exception) {
            return problem(HttpStatus.CONFLICT, "OFFER_ID_CONFLICT",
                    "offerId já utilizado com participante ou preço diferente.");
        }
        if (accepted.created()) {
                return HttpResponse.<AcceptedResponse>status(HttpStatus.ACCEPTED)
                    .body(new AcceptedResponse(accepted.offerId(), "PENDING",
                        "/buy-offers/" + accepted.offerId()))
                    .header(HttpHeaders.LOCATION, "/buy-offers/" + accepted.offerId());
        }
        return HttpResponse.ok(toResponse(service.get(accepted.offerId())));
    }

    @Get("/buy-offers/{offerId}")
    public HttpResponse<?> get(String offerId) {
        try {
            return HttpResponse.ok(toResponse(service.get(UUID.fromString(offerId))));
        } catch (IllegalArgumentException exception) {
            return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "offerId inválido.");
        } catch (BuyOfferService.OfferNotFoundException exception) {
            return problem(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Oferta inexistente.");
        }
    }

    private Object toResponse(BuyOfferEntity offer) {
        if (offer.getStatus() == BuyOfferStatus.EXECUTED) {
            return new ExecutedOffer(offer.getId(), offer.getBuyerId(), offer.getPrice().toPlainString(),
                    offer.getCreatedAt(), "EXECUTED", offer.getTransactionId(),
                    offer.getExecutionPrice().toPlainString());
        }
        return new PendingOffer(offer.getId(), offer.getBuyerId(), offer.getPrice().toPlainString(),
                offer.getCreatedAt(), "PENDING");
    }

    private HttpResponse<Problem> problem(HttpStatus status, String code, String detail) {
        return HttpResponse.status(status).contentType("application/problem+json")
                .body(new Problem("about:blank", detail, status.getCode(), detail, code));
    }

    public record CreateOfferRequest(UUID offerId, UUID buyerId, String priceUsd) { }
    public record AcceptedResponse(UUID offerId, String status, String statusUrl) { }
    public record PendingOffer(UUID offerId, UUID buyerId, String priceUsd, Instant createdAt, String status) { }
    public record ExecutedOffer(UUID offerId, UUID buyerId, String priceUsd, Instant createdAt, String status,
                                UUID transactionId, String executionPriceUsd) { }
    public record Problem(String type, String title, int status, String detail, String code) { }
=======
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
>>>>>>> efdc1b8768b932c2ccfa09ea4d4e648a1dc42593
}
