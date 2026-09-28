package co.inter.piggies.coordinator;

import co.inter.piggies.contracts.*;
import co.inter.piggies.coordinator.application.CoordinateOfferService;
import co.inter.piggies.coordinator.domain.model.*;
import co.inter.piggies.coordinator.domain.port.in.CoordinateOfferUseCase;
import co.inter.piggies.coordinator.domain.port.out.*;
import co.inter.piggies.coordinator.adapter.in.kafka.OfferCreatedDecoder;
import co.inter.piggies.coordinator.adapter.out.messaging.*;
import co.inter.piggies.support.IntegrationTest;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import io.micronaut.jdbc.DataSourceResolver;
import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@MicronautTest(transactional=false)
class CoordinatorPersistenceTest extends IntegrationTest {
    @Inject CoordinateOfferUseCase coordinator;
    @Inject CoordinatorStore store;
    @Inject JsonMapper mapper;
    @Inject OfferCreatedDecoder decoder;
    @Inject DataSource dataSource;
    @Inject DataSourceResolver resolver;

    @Override public Map<String,String> getProperties() {
        var properties = new HashMap<>(super.getProperties());
        properties.put("coordinator.kafka.enabled","false");
        properties.put("coordinator.outbox.enabled","false");
        return properties;
    }

    @BeforeEach void clearCoordinatorTables() throws Exception {
        dataSource = resolver.resolve(dataSource);
        try (var connection=dataSource.getConnection(); var sql=connection.createStatement()) {
            sql.execute("TRUNCATE wallet_entries,trades,offers");
            sql.execute("DELETE FROM outbox WHERE topic IN ('slp.trades.executed','slp.exchange-rate.updated')");
            sql.execute("DELETE FROM processed_events WHERE consumer='slp-coordinator'");
        }
    }

    private OrderOffer offer(OfferSide side,String price) {
        return new OrderOffer(UUID.randomUUID(),side,UUID.randomUUID(),new BigDecimal(price),Instant.now());
    }
    private long count(String table) throws Exception {
        try (var connection=dataSource.getConnection();var sql=connection.createStatement();var rows=sql.executeQuery("SELECT count(*) FROM "+table)) {
            rows.next(); return rows.getLong(1);
        }
    }
    private String status(UUID id) throws Exception {
        try (var connection=dataSource.getConnection();var sql=connection.prepareStatement("SELECT status FROM offers WHERE id=?")) {
            sql.setObject(1,id); try(var rows=sql.executeQuery()) { rows.next(); return rows.getString(1); }
        }
    }

    @Test void commitsOneTradeTwoLedgerEntriesAndTwoOutboxMessages() throws Exception {
        var sell=offer(OfferSide.SELL,"20"); var low=offer(OfferSide.BUY,"10"); var high=offer(OfferSide.BUY,"30");
        coordinator.coordinate(UUID.randomUUID(),sell);
        coordinator.coordinate(UUID.randomUUID(),low);
        assertThat(count("trades")).isZero();
        var eventId=UUID.randomUUID();
        coordinator.coordinate(eventId,high);
        coordinator.coordinate(eventId,high);
        coordinator.coordinate(UUID.randomUUID(),high);
        assertThat(count("trades")).isEqualTo(1);
        assertThat(count("wallet_entries")).isEqualTo(2);
        assertThat(count("outbox")).isEqualTo(2);
        assertThat(status(sell.id())).isEqualTo("EXECUTED");
        assertThat(status(high.id())).isEqualTo("EXECUTED");
        assertThat(status(low.id())).isEqualTo("OPEN");
        try(var c=dataSource.getConnection();var s=c.createStatement();var rows=s.executeQuery("SELECT participant_id,amount FROM wallet_entries")) {
            Map<UUID,BigDecimal> ledger=new HashMap<>();
            while(rows.next()) ledger.put(rows.getObject(1,UUID.class),rows.getBigDecimal(2));
            assertThat(ledger.get(sell.participantId())).isEqualByComparingTo("-1");
            assertThat(ledger.get(high.participantId())).isEqualByComparingTo("1");
        }
        try(var c=dataSource.getConnection();var s=c.createStatement();var rows=s.executeQuery("SELECT topic,payload FROM outbox ORDER BY id")) {
            rows.next();
            var trade=mapper.readValue(rows.getString("payload"),TradeExecutedMessage.class);
            assertThat(rows.getString("topic")).isEqualTo(Topics.TRADES_EXECUTED);
            assertThat(trade.price()).isEqualByComparingTo("25.00");
            assertThat(trade.buyOfferId()).isEqualTo(high.id());
            rows.next();
            var rate=mapper.readValue(rows.getString("payload"),ExchangeRateUpdatedMessage.class);
            assertThat(rate.tradeId()).isEqualTo(trade.tradeId());
            assertThat(rate.rate()).isEqualByComparingTo("25.00");
            assertThat(rate.pair()).isEqualTo(Topics.PAIR_KEY);
        }
    }

    @Test void serializationFailureRollsBackTradeLedgerOfferAndDeduplication() throws Exception {
        var sell=offer(OfferSide.SELL,"20"); var buy=offer(OfferSide.BUY,"30"); var eventId=UUID.randomUUID();
        coordinator.coordinate(UUID.randomUUID(),sell);
        TradeEvents broken = trade -> { throw new IllegalStateException("serialization failure"); };
        var failing = new CoordinateOfferService(store,new MatchingEngine(),broken,Clock.systemUTC());
        assertThatThrownBy(() -> failing.coordinate(eventId,buy)).hasMessage("serialization failure");
        assertThat(count("offers")).isEqualTo(1);
        assertThat(count("trades")).isZero();
        assertThat(count("wallet_entries")).isZero();
        assertThat(count("outbox")).isZero();
        assertThat(status(sell.id())).isEqualTo("OPEN");
        coordinator.coordinate(eventId,buy);
        assertThat(count("trades")).isEqualTo(1);
    }

    @Test void concurrentBuysCannotConsumeTheSameSellTwice() throws Exception {
        var sell=offer(OfferSide.SELL,"20"); coordinator.coordinate(UUID.randomUUID(),sell);
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Void> first=() -> {gate.await(); coordinator.coordinate(UUID.randomUUID(),offer(OfferSide.BUY,"30")); return null;};
            var a=pool.submit(first); var b=pool.submit(first); gate.countDown();
            a.get(15,TimeUnit.SECONDS); b.get(15,TimeUnit.SECONDS);
        }
        assertThat(count("trades")).isEqualTo(1);
        assertThat(count("wallet_entries")).isEqualTo(2);
        assertThat(count("outbox")).isEqualTo(2);
    }

    @Test void conflictingDuplicateRollsBackTheNewEventClaim() throws Exception {
        var offer=offer(OfferSide.BUY,"30"); coordinator.coordinate(UUID.randomUUID(),offer);
        var bad=new OrderOffer(offer.id(),offer.side(),offer.participantId(),new BigDecimal("31"),offer.createdAt());
        var eventId=UUID.randomUUID();
        assertThatThrownBy(() -> coordinator.coordinate(eventId,bad)).isInstanceOf(IllegalArgumentException.class);
        coordinator.coordinate(eventId,offer);
        assertThat(count("offers")).isEqualTo(1);
    }

    @Test void databaseSelectsBestPriceThenOldestCounterparty() throws Exception {
        var expensive=offer(OfferSide.SELL,"22");
        var older=new OrderOffer(UUID.randomUUID(),OfferSide.SELL,UUID.randomUUID(),new BigDecimal("20"),Instant.parse("2026-01-01T00:00:00Z"));
        var newer=offer(OfferSide.SELL,"20");
        coordinator.coordinate(UUID.randomUUID(),expensive);
        coordinator.coordinate(UUID.randomUUID(),newer);
        coordinator.coordinate(UUID.randomUUID(),older);
        coordinator.coordinate(UUID.randomUUID(),offer(OfferSide.BUY,"30"));
        assertThat(status(older.id())).isEqualTo("EXECUTED");
        assertThat(status(expensive.id())).isEqualTo("OPEN");
        assertThat(status(newer.id())).isEqualTo("OPEN");
    }

    @Test void brokerFailureLeavesStableOutboxPayloadsForRetry() throws Exception {
        coordinator.coordinate(UUID.randomUUID(),offer(OfferSide.SELL,"20"));
        coordinator.coordinate(UUID.randomUUID(),offer(OfferSide.BUY,"30"));
        List<String> attempted=new ArrayList<>();
        CoordinatorKafkaProducer failing=(topic,key,payload) -> {attempted.add(payload); return attempted.size() == 1 ? CompletableFuture.completedFuture(null) : CompletableFuture.failedFuture(new IllegalStateException("broker down after first publication"));};
        new CoordinatorOutboxRelay(dataSource,resolver,failing,10).publishPending();
        assertThat(count("trades")).isEqualTo(1);
        try(var c=dataSource.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM outbox WHERE published_at IS NOT NULL")) {
            r.next(); assertThat(r.getInt(1)).isZero();
        }
        List<String> retried=new ArrayList<>();
        CoordinatorKafkaProducer recovered=(topic,key,payload) -> {retried.add(payload); return CompletableFuture.completedFuture(null);};
        new CoordinatorOutboxRelay(dataSource,resolver,recovered,10).publishPending();
        assertThat(attempted).hasSize(2);
        assertThat(retried).containsExactlyElementsOf(attempted);
        try(var c=dataSource.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM outbox WHERE published_at IS NOT NULL")) {
            r.next(); assertThat(r.getInt(1)).isEqualTo(2);
        }
    }

    @Test void rejectsInvalidWirePayloadBeforeAnyDatabaseWrite() throws Exception {
        var offer=offer(OfferSide.BUY,"30");
        var valid=mapper.writeValueAsString(new OfferCreatedMessage(UUID.randomUUID(),offer.id(),"BUY",offer.participantId(),offer.price(),offer.createdAt()));
        assertThat(decoder.decode(valid).offer()).isEqualTo(offer);
        assertThatThrownBy(() -> decoder.decode(valid.replace("\"BUY\"","\"INVALID\""))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decode(valid.replaceFirst("\\{","{\"unknown\":true,"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("offers")).isZero();
    }
}

