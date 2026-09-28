package co.inter.piggies.coordinator.adapter.out.messaging;

import co.inter.piggies.contracts.Topics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import io.micronaut.jdbc.DataSourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.sql.*;
import java.util.concurrent.TimeUnit;

/** Publishes only Coordinator-owned topics. Disable when adopting a shared relay. */
@Singleton
@Requires(property="outbox.relay.enabled", value="true", defaultValue="true")
@Requires(property="coordinator.outbox.enabled", value="true", defaultValue="true")
public class CoordinatorOutboxRelay {
    private static final Logger LOG = LoggerFactory.getLogger(CoordinatorOutboxRelay.class);
    private final DataSource dataSource;
    private final CoordinatorKafkaProducer producer;
    private final int batchSize;
    public CoordinatorOutboxRelay(DataSource dataSource, DataSourceResolver resolver, CoordinatorKafkaProducer producer,
                                 @Value("${outbox.relay.batch-size:100}") int batchSize) {
        this.dataSource = resolver.resolve(dataSource);
        this.producer = producer;
        this.batchSize = Math.max(1,Math.min(batchSize,1000));
    }

    @Scheduled(fixedDelay="${outbox.relay.interval:500ms}", initialDelay="${outbox.relay.initial-delay:2s}")
    public void publishPending() {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // A single publisher for this market across processes preserves outbox ordering.
                try (var lock = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(732011, 2)"); var result=lock.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) { connection.rollback(); return; }
                }
                try (var select=connection.prepareStatement("SELECT id,topic,message_key,payload FROM outbox WHERE published_at IS NULL AND topic IN (?,?) ORDER BY id LIMIT ? FOR UPDATE")) {
                    select.setString(1,Topics.TRADES_EXECUTED);
                    select.setString(2,Topics.EXCHANGE_RATE_UPDATED);
                    select.setInt(3,batchSize);
                    try (var rows=select.executeQuery()) {
                        while (rows.next()) {
                            // Runs on a scheduler, never on an HTTP event loop. Failure keeps rows pending.
                            producer.send(rows.getString("topic"),rows.getString("message_key"),rows.getString("payload"))
                                    .get(15,TimeUnit.SECONDS);
                            try (var mark=connection.prepareStatement("UPDATE outbox SET published_at=CURRENT_TIMESTAMP WHERE id=?")) {
                                mark.setLong(1,rows.getLong("id"));
                                mark.executeUpdate();
                            }
                        }
                    }
                }
                connection.commit();
            } catch (Exception e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                LOG.warn("Coordinator outbox publication failed; pending events will be retried",e);
            }
        } catch (SQLException e) {
            LOG.warn("Cannot access Coordinator outbox; will retry",e);
        }
    }
}

