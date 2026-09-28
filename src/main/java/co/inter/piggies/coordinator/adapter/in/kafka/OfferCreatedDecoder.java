package co.inter.piggies.coordinator.adapter.in.kafka;

import co.inter.piggies.contracts.OfferCreatedMessage;
import co.inter.piggies.coordinator.domain.model.*;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Singleton
public class OfferCreatedDecoder {
    private static final Set<String> FIELDS = Set.of("eventId","offerId","side","participantId","price","createdAt");
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private final JsonMapper mapper;
    public OfferCreatedDecoder(JsonMapper mapper) { this.mapper = mapper; }
    public record DecodedOffer(UUID eventId, OrderOffer offer) { }

    public DecodedOffer decode(String payload) {
        try {
            var fields = mapper.readValue(payload, Argument.mapOf(String.class,Object.class));
            if (fields == null || !fields.keySet().equals(FIELDS) || fields.values().stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("OfferCreated must contain exactly the required fields");
            }
            for (String field : Set.of("eventId","offerId","participantId")) {
                if (!(fields.get(field) instanceof String value) || !UUID_PATTERN.matcher(value).matches()) {
                    throw new IllegalArgumentException("Invalid UUID: " + field);
                }
            }
            if (!(fields.get("price") instanceof Number) || !(fields.get("createdAt") instanceof String)) {
                throw new IllegalArgumentException("price must be a JSON number and createdAt a timestamp string");
            }
            var message = mapper.readValue(payload, OfferCreatedMessage.class);
            return new DecodedOffer(message.eventId(), new OrderOffer(message.offerId(),OfferSide.valueOf(message.side()),
                    message.participantId(),message.price(),message.createdAt()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid OfferCreated JSON", e);
        }
    }
}
