package co.inter.piggies.coordinator.adapter.out.persistence;

import co.inter.piggies.coordinator.domain.model.*;
import co.inter.piggies.coordinator.domain.port.out.*;
import jakarta.inject.Singleton;
import io.micronaut.jdbc.DataSourceResolver;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** JDBC adapter reusing V1 tables; never reads or writes Buyer/Seller tables. */
@Singleton
public class JdbcCoordinatorStore implements CoordinatorStore {
    private final DataSource dataSource;

    public JdbcCoordinatorStore(DataSource dataSource, DataSourceResolver resolver) { this.dataSource = resolver.resolve(dataSource); }

    @Override
    public void inTransaction(Consumer<Transaction> work) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                // Cross-process lock for the only market, PIGGY-USD. Released on commit/rollback.
                // Also protects direct/replayed/concurrent invocations beyond Kafka partition ordering.
                try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(732011, 1)")) {
                    statement.execute();
                }
                work.accept(new JdbcTransaction(connection));
                connection.commit();
            } catch (RuntimeException | Error | SQLException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                if (failure instanceof SQLException sql) throw new CoordinatorPersistenceException("Coordinator transaction failed", sql);
                throw failure;
            }
        } catch (SQLException e) {
            throw new CoordinatorPersistenceException("Cannot access Coordinator database", e);
        }
    }

    private static final class JdbcTransaction implements Transaction {
        private final Connection connection;
        private JdbcTransaction(Connection connection) { this.connection = connection; }

        private PreparedStatement prepare(String sql, Object... values) throws SQLException {
            var statement = connection.prepareStatement(sql);
            try {
                for (int i=0; i<values.length; i++) {
                    if (values[i] instanceof Instant instant) statement.setTimestamp(i+1, Timestamp.from(instant));
                    else statement.setObject(i+1, values[i]);
                }
                return statement;
            } catch (SQLException | RuntimeException e) {
                statement.close();
                throw e;
            }
        }

        private int update(String sql, Object... values) {
            try (var statement = prepare(sql, values)) { return statement.executeUpdate(); }
            catch (SQLException e) { throw new CoordinatorPersistenceException("Coordinator write failed", e); }
        }

        @Override
        public boolean claimEvent(UUID eventId, Instant time) {
            return update("INSERT INTO processed_events (consumer,event_id,processed_at) VALUES ('slp-coordinator',?,?) ON CONFLICT DO NOTHING", eventId,time) == 1;
        }

        @Override
        public Optional<OrderOffer> findOffer(UUID id) {
            try (var statement = prepare("SELECT * FROM offers WHERE id = ?", id); var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readOffer(rows)) : Optional.empty();
            } catch (SQLException e) { throw new CoordinatorPersistenceException("Cannot read offer", e); }
        }

        @Override
        public void addOffer(OrderOffer offer) {
            update("INSERT INTO offers (id,side,participant_id,price,status,created_at) VALUES (?,?,?,?,'OPEN',?)",
                    offer.id(), offer.side().name(), offer.participantId(), offer.price(), offer.createdAt());
        }

        @Override
        public List<OrderOffer> openCounterparties(OrderOffer incoming) {
            // Fetch just the best candidate. The pure domain engine checks compatibility again.
            String comparison = incoming.side() == OfferSide.BUY ? "<= ? ORDER BY price ASC" : ">= ? ORDER BY price DESC";
            String sql = "SELECT * FROM offers WHERE status='OPEN' AND side=? AND price " + comparison
                    + ", created_at ASC, id ASC LIMIT 1 FOR UPDATE";
            try (var statement = prepare(sql, incoming.side().opposite().name(), incoming.price()); var rows = statement.executeQuery()) {
                return rows.next() ? List.of(readOffer(rows)) : List.of();
            } catch (SQLException e) { throw new CoordinatorPersistenceException("Cannot find counterparty", e); }
        }

        private OrderOffer readOffer(ResultSet row) throws SQLException {
            return new OrderOffer(row.getObject("id", UUID.class), OfferSide.valueOf(row.getString("side")),
                    row.getObject("participant_id", UUID.class), row.getBigDecimal("price"), row.getTimestamp("created_at").toInstant());
        }

        @Override
        public void execute(Trade trade) {
            update("INSERT INTO trades (id,buy_offer_id,sell_offer_id,buyer_id,seller_id,price,executed_at) VALUES (?,?,?,?,?,?,?)",
                    trade.id(),trade.buy().id(),trade.sell().id(),trade.buy().participantId(),trade.sell().participantId(),trade.price(),trade.executedAt());
            int closed = update("UPDATE offers SET status='EXECUTED', trade_id=? WHERE id IN (?,?) AND status='OPEN'",
                    trade.id(),trade.buy().id(),trade.sell().id());
            if (closed != 2) throw new IllegalStateException("Both offers must still be open");
            // Team MVP: ledger records movement; no initial balance or funds check.
            update("INSERT INTO wallet_entries (participant_id,asset,amount,trade_id,created_at) VALUES (?,'PIGGY',-1,?,?),(?,'PIGGY',1,?,?)",
                    trade.sell().participantId(),trade.id(),trade.executedAt(),trade.buy().participantId(),trade.id(),trade.executedAt());
        }

        @Override
        public void enqueue(OutboxMessage event) {
            update("INSERT INTO outbox (event_id,topic,message_key,payload,created_at) VALUES (?,?,?,?,?)",
                    event.eventId(),event.topic(),event.key(),event.payload(),event.createdAt());
        }
    }
}

