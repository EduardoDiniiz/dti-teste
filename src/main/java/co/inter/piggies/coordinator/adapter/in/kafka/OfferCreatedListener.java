package co.inter.piggies.coordinator.adapter.in.kafka;

import co.inter.piggies.contracts.Topics;
import co.inter.piggies.coordinator.domain.port.in.CoordinateOfferUseCase;
import io.micronaut.configuration.kafka.annotation.*;
import io.micronaut.context.annotation.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Requires(property="coordinator.kafka.enabled", value="true", defaultValue="true")
@KafkaListener(groupId="slp-coordinator", offsetReset=OffsetReset.EARLIEST,
        offsetStrategy=OffsetStrategy.SYNC_PER_RECORD,
        errorStrategy=@ErrorStrategy(value=ErrorStrategyValue.RETRY_ON_ERROR,
                retryCount=3, retryDelay="1s", stopOnExhaustedRetry=true))
public class OfferCreatedListener {
    private static final Logger LOG = LoggerFactory.getLogger(OfferCreatedListener.class);
    private final CoordinateOfferUseCase coordinator;
    private final OfferCreatedDecoder decoder;
    public OfferCreatedListener(CoordinateOfferUseCase coordinator, OfferCreatedDecoder decoder) {
        this.coordinator = coordinator;
        this.decoder = decoder;
    }
    @Topic(Topics.OFFERS_CREATED)
    public void receive(@KafkaKey String key, String payload) {
        try {
            if (!Topics.PAIR_KEY.equals(key)) throw new IllegalArgumentException("Expected Kafka key PIGGY-USD");
            var decoded = decoder.decode(payload);
            coordinator.coordinate(decoded.eventId(),decoded.offer());
            LOG.debug("Processed offer {} (event {})",decoded.offer().id(),decoded.eventId());
        } catch (RuntimeException e) {
            LOG.error("Coordinator could not process OfferCreated; offset must not advance",e);
            throw e;
        }
    }
}
