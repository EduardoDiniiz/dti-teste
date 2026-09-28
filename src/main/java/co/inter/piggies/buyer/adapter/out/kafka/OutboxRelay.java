package co.inter.piggies.buyer.adapter.out.kafka;

import co.inter.piggies.buyer.application.OutboxEntity;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class OutboxRelay {
    private final EntityManager entityManager;
    private final OutboxProducer producer;

    public OutboxRelay(EntityManager entityManager, OutboxProducer producer) {
        this.entityManager = entityManager;
        this.producer = producer;
    }

    @Scheduled(fixedDelay = "${outbox.relay.interval:500ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEntity> pending = entityManager.createQuery(
                        "from OutboxEntity o where o.publishedAt is null order by o.id", OutboxEntity.class)
                .setMaxResults(100)
                .getResultList();
        for (OutboxEntity event : pending) {
            producer.send(event.getTopic(), event.getMessageKey(), event.getPayload());
            event.markPublished();
        }
    }

    @KafkaClient(id = "slp-outbox-producer")
    public interface OutboxProducer {
        void send(String topic, @KafkaKey String key, String payload);
    }
}
