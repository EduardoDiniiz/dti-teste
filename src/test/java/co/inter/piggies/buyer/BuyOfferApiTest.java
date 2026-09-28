package co.inter.piggies.buyer;

import co.inter.piggies.support.IntegrationTest;
import co.inter.piggies.support.KafkaTestClient;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

/** Contrato contracts/buyer.openapi.json: POST /buy-offers e GET /buy-offers/{offerId}. */
@MicronautTest(transactional = false)
class BuyOfferApiTest extends IntegrationTest {

    private static final Argument<Map<String, Object>> JSON = Argument.mapOf(String.class, Object.class);

    @Test
    void createGeneratesOfferReturns202WithLocationAndPublishesEvent() {
        UUID buyerId = UUID.randomUUID();

        HttpResponse<Map<String, Object>> response = post(buyerId, "30.00");

        assertThat(response.code()).isEqualTo(202);
        String offerId = response.body().get("offerId").toString();
        assertThat(response.header("Location")).isEqualTo("/buy-offers/" + offerId);
        assertThat(response.body()).containsEntry("status", "PENDING")
                .containsEntry("statusUrl", "/buy-offers/" + offerId);

        assertThat(get(offerId)).containsEntry("offerId", offerId).containsEntry("buyerId", buyerId.toString())
                .containsEntry("status", "PENDING").containsEntry("priceUsd", "30.00");

        try (KafkaTestClient kafka = new KafkaTestClient(List.of("slp.offers.v1"))) {
            await().atMost(Duration.ofSeconds(20)).pollInSameThread().untilAsserted(() ->
                    assertThat(kafka.records("slp.offers.v1"))
                            .anySatisfy(r -> {
                                assertThat(r.key()).isEqualTo("PIGGY-USD");
                                assertThat(r.value()).contains("\"eventType\":\"BuyOfferCreated\"")
                                        .contains(offerId).contains("\"priceUsd\":\"30.00\"");
                            }));
        }
    }

    @Test
    void createWithOnlyPriceGeneratesIds() {
        HttpResponse<Map<String, Object>> response = http().exchange(HttpRequest.POST("/buy-offers",
                Map.of("priceUsd", "30.00")), JSON);

        assertThat(response.code()).isEqualTo(202);
        Map<String, Object> offer = get(response.body().get("offerId").toString());
        assertThat(offer).containsEntry("status", "PENDING").containsEntry("priceUsd", "30.00")
                .containsKey("buyerId");
    }

    @Test
    void samePayloadTwiceCreatesTwoOffers() {
        UUID buyerId = UUID.randomUUID();

        Object first = post(buyerId, "30.00").body().get("offerId");
        Object second = post(buyerId, "30.00").body().get("offerId");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void unknownOfferReturns404() {
        HttpClientResponseException e = expectError(() -> get(UUID.randomUUID().toString()));

        assertThat(e.code()).isEqualTo(404);
        assertThat(e.getResponse().getBody(JSON)).hasValueSatisfying(b -> assertThat(b)
                .containsEntry("code", "OFFER_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "10.001", "1e3", "abc"})
    void invalidPriceReturns400(String price) {
        HttpClientResponseException e = expectError(() -> post(UUID.randomUUID(), price));

        assertThat(e.code()).isEqualTo(400);
    }

    @Test
    void offerIdInRequestIsRejected() {
        HttpClientResponseException e = expectError(() -> http().exchange(HttpRequest.POST("/buy-offers",
                Map.of("offerId", UUID.randomUUID(), "buyerId", UUID.randomUUID(), "priceUsd", "30.00")), JSON));

        assertThat(e.code()).isEqualTo(400);
    }

    private HttpResponse<Map<String, Object>> post(UUID buyerId, String price) {
        return http().exchange(HttpRequest.POST("/buy-offers", Map.of("buyerId", buyerId, "priceUsd", price)), JSON);
    }

    private Map<String, Object> get(String offerId) {
        return http().retrieve(HttpRequest.GET("/buy-offers/" + offerId), JSON);
    }

    private static HttpClientResponseException expectError(Runnable call) {
        HttpClientResponseException e = catchThrowableOfType(HttpClientResponseException.class, call::run);
        assertThat(e).as("expected an HTTP error").isNotNull();
        return e;
    }
}
