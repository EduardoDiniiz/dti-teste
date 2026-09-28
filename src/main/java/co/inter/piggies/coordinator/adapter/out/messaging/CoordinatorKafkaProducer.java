package co.inter.piggies.coordinator.adapter.out.messaging;

import io.micronaut.configuration.kafka.annotation.*;
import org.apache.kafka.clients.producer.RecordMetadata;
import java.util.concurrent.CompletableFuture;

@KafkaClient(id="slp-producer")
public interface CoordinatorKafkaProducer {
    CompletableFuture<RecordMetadata> send(@Topic String topic, @KafkaKey String key, String payload);
}
