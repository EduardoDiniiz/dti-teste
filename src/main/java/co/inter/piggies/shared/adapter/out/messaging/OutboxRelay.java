package co.inter.piggies.shared.adapter.out.messaging;

import co.inter.piggies.shared.adapter.out.persistence.OutboxEntity;
import co.inter.piggies.shared.adapter.out.persistence.OutboxJpaRepository;
import co.inter.piggies.shared.domain.port.out.TransactionRunner;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Lê eventos pendentes do outbox, publica no Kafka e marca como enviados.
 * At-least-once: consumidores deduplicam pelo eventId.
 */
@Singleton
@Requires(property = "outbox.relay.enabled", notEquals = "false")
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxJpaRepository outbox;
    private final SlpProducer producer;
    private final TransactionRunner tx;
    private final int batchSize;

    OutboxRelay(OutboxJpaRepository outbox, SlpProducer producer, TransactionRunner tx,
                @Value("${outbox.relay.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.producer = producer;
        this.tx = tx;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelay = "${outbox.relay.interval:500ms}", initialDelay = "${outbox.relay.initial-delay:1s}")
    public void relay() {
        int published = tx.inTransaction(this::publishBatch);
        if (published > 0) {
            LOG.debug("Outbox relay published {} events", published);
        }
    }

    private int publishBatch() {
        List<OutboxEntity> pending = outbox.lockPending(batchSize);
        int published = 0;
        for (OutboxEntity row : pending) {
            try {
                producer.send(row.getTopic(), row.getMessageKey(), row.getPayload());
            } catch (RuntimeException e) {
                // Para no primeiro erro para não furar a ordem; os já enviados são commitados.
                LOG.warn("Failed to publish outbox event {} to {}: {}", row.getEventId(), row.getTopic(), e.getMessage());
                break;
            }
            row.setPublishedAt(Instant.now());
            published++;
        }
        return published;
    }
}
