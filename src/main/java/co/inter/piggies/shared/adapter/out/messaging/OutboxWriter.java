package co.inter.piggies.shared.adapter.out.messaging;

import co.inter.piggies.shared.adapter.out.persistence.OutboxEntity;
import co.inter.piggies.shared.adapter.out.persistence.OutboxJpaRepository;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox: grava a mensagem numa tabela na MESMA transação da operação
 * (sem dual write). O {@link OutboxRelay} publica no Kafka depois.
 * Use dentro do adapter que implementa a porta EventPublisher do seu domínio.
 */
@Singleton
public class OutboxWriter {

    private final OutboxJpaRepository outbox;
    private final JsonMapper jsonMapper;

    OutboxWriter(OutboxJpaRepository outbox, JsonMapper jsonMapper) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
    }

    public void write(UUID eventId, String topic, String key, Object message) {
        OutboxEntity row = new OutboxEntity();
        row.setEventId(eventId);
        row.setTopic(topic);
        row.setMessageKey(key);
        row.setPayload(toJson(message));
        row.setCreatedAt(Instant.now());
        outbox.persist(row);
    }

    private String toJson(Object message) {
        try {
            return jsonMapper.writeValueAsString(message);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
