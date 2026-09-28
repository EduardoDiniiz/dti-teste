package co.inter.piggies.shared.adapter.out.messaging;

import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;
import org.apache.kafka.clients.producer.RecordMetadata;

@KafkaClient(id = "slp-producer", acks = KafkaClient.Acknowledge.ALL)
public interface SlpProducer {

    /** Síncrono: o outbox só é marcado como enviado depois do ack do broker. */
    RecordMetadata send(@Topic String topic, @KafkaKey String key, String payload);
}
