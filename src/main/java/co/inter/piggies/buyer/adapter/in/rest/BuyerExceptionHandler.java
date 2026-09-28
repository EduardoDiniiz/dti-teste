package co.inter.piggies.buyer.adapter.in.rest;

import co.inter.piggies.buyer.domain.exception.BuyOfferNotFoundException;
import co.inter.piggies.buyer.domain.exception.BuyerException;
import co.inter.piggies.buyer.domain.exception.InvalidPriceException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traduz erros do buyer para problem+json com o campo "code" exigido pelo contrato. */
@Produces("application/problem+json")
@Singleton
class BuyerExceptionHandler implements ExceptionHandler<BuyerException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, BuyerException exception) {
        record Mapping(HttpStatus status, String code) {
        }
        Mapping m = switch (exception) {
            case InvalidPriceException e -> new Mapping(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
            case BuyOfferNotFoundException e -> new Mapping(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND");
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", m.status().getReason());
        body.put("status", m.status().getCode());
        body.put("detail", exception.getMessage());
        body.put("code", m.code());
        body.put("instance", request.getPath());
        return HttpResponse.status(m.status()).contentType("application/problem+json").body(body);
    }
}
