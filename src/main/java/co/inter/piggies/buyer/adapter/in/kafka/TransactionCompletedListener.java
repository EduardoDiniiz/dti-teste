package co.inter.piggies.buyer.adapter.in.kafka;

import co.inter.piggies.buyer.application.BuyOfferService;
import co.inter.piggies.buyer.application.ProcessedEventEntity;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.json.JsonMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.io.IOException;

@KafkaListener(groupId = "slp-buyer-v1", offsetReset = OffsetReset.EARLIEST)
public class TransactionCompletedListener {
    private final EntityManager entityManager;
    private final BuyOfferService service;
    private final JsonMapper jsonMapper;

    public TransactionCompletedListener(EntityManager entityManager, BuyOfferService service, JsonMapper jsonMapper) {
        this.entityManager = entityManager;
        this.service = service;
        this.jsonMapper = jsonMapper;
    }

    @Topic("slp.transactions.v1")
    @Transactional
    public void receive(String value) {
        try {
            TransactionCompletedEvent event = jsonMapper.readValue(value, TransactionCompletedEvent.class);
            int inserted = entityManager.createNativeQuery(
                            "insert into processed_events (consumer, event_id, processed_at) values (:consumer, :eventId, current_timestamp) on conflict do nothing")
                    .setParameter("consumer", "slp-buyer-v1")
                    .setParameter("eventId", event.eventId())
                    .executeUpdate();
            if (inserted == 1) {
                service.execute(new BuyOfferService.TransactionCompletedMessage(event.eventId(), event.payload().transactionId(),
                        event.payload().buyOfferId(), event.payload().executionPriceUsd()));
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid TransactionCompleted event", exception);
        }
    }

    public record TransactionCompletedEvent(java.util.UUID eventId, String eventType, int schemaVersion,
                                            java.time.Instant occurredAt, Payload payload) { }
    public record Payload(java.util.UUID transactionId, java.util.UUID buyOfferId, java.math.BigDecimal executionPriceUsd) { }
}
