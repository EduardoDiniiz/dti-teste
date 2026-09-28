package co.inter.piggies.coordinator;

import co.inter.piggies.contracts.*;
import co.inter.piggies.support.*;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import io.micronaut.jdbc.DataSourceResolver;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real Kafka -> Coordinator -> PostgreSQL/outbox -> Kafka, independent of HTTP modules. */
@MicronautTest(transactional=false)
class CoordinatorKafkaIntegrationTest extends IntegrationTest {
    @Inject JsonMapper mapper;
    @Inject DataSource dataSource;
    @Inject DataSourceResolver resolver;

    @Test void receivesOffersAndPublishesTradeAndRateWithNoDuplicateTransfer() throws Exception {
        dataSource = resolver.resolve(dataSource);
        // Integration classes run sequentially. Only Coordinator tables are reset in this isolated test DB.
        try(var c=dataSource.getConnection();var s=c.createStatement()) {
            s.execute("TRUNCATE wallet_entries,trades,offers");
            s.execute("DELETE FROM outbox WHERE topic IN ('slp.trades.executed','slp.exchange-rate.updated')");
            s.execute("DELETE FROM processed_events WHERE consumer='slp-coordinator'");
        }
        UUID sellId=UUID.randomUUID(), lowId=UUID.randomUUID(), highId=UUID.randomUUID();
        UUID seller=UUID.randomUUID(), lowBuyer=UUID.randomUUID(), highBuyer=UUID.randomUUID();
        var sell=new OfferCreatedMessage(UUID.randomUUID(),sellId,"SELL",seller,new BigDecimal("20.00"),Instant.now());
        var low=new OfferCreatedMessage(UUID.randomUUID(),lowId,"BUY",lowBuyer,new BigDecimal("10.00"),Instant.now());
        var high=new OfferCreatedMessage(UUID.randomUUID(),highId,"BUY",highBuyer,new BigDecimal("30.00"),Instant.now());
        capture("offer-created", mapper.writeValueAsString(high));
        try(var kafka=new KafkaTestClient(List.of(Topics.TRADES_EXECUTED,Topics.EXCHANGE_RATE_UPDATED))) {
            kafka.send(Topics.OFFERS_CREATED,Topics.PAIR_KEY,mapper.writeValueAsString(sell));
            kafka.send(Topics.OFFERS_CREATED,Topics.PAIR_KEY,mapper.writeValueAsString(low));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(status(sellId)).isEqualTo("OPEN");
                assertThat(status(lowId)).isEqualTo("OPEN");
            });
            kafka.send(Topics.OFFERS_CREATED,Topics.PAIR_KEY,mapper.writeValueAsString(high));
            kafka.send(Topics.OFFERS_CREATED,Topics.PAIR_KEY,mapper.writeValueAsString(high));
            // A subsequent valid event is a barrier: its persistence proves the replay was traversed.
            UUID barrier=UUID.randomUUID();
            kafka.send(Topics.OFFERS_CREATED,Topics.PAIR_KEY,mapper.writeValueAsString(new OfferCreatedMessage(UUID.randomUUID(),barrier,"BUY",UUID.randomUUID(),BigDecimal.ONE,Instant.now())));
            await().pollInSameThread().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
                assertThat(status(highId)).isEqualTo("EXECUTED");
                assertThat(status(sellId)).isEqualTo("EXECUTED");
                assertThat(status(lowId)).isEqualTo("OPEN");
                assertThat(status(barrier)).isEqualTo("OPEN");
                assertThat(kafka.records(Topics.TRADES_EXECUTED)).anySatisfy(record -> {
                    var trade=read(record.value(),TradeExecutedMessage.class);
                    assertThat(trade.buyOfferId()).isEqualTo(highId);
                    assertThat(trade.sellOfferId()).isEqualTo(sellId);
                    assertThat(trade.price()).isEqualByComparingTo("25.00");
                    assertThat(record.key()).isEqualTo(trade.tradeId().toString());
                    assertThat(kafka.records(Topics.EXCHANGE_RATE_UPDATED)).anySatisfy(rateRecord -> {
                        var rate=read(rateRecord.value(),ExchangeRateUpdatedMessage.class);
                        assertThat(rate.tradeId()).isEqualTo(trade.tradeId());
                        assertThat(rate.rate()).isEqualByComparingTo("25.00");
                        assertThat(rateRecord.key()).isEqualTo(Topics.PAIR_KEY);
                        capture("trade-executed",record.value());
                        capture("exchange-rate-updated",rateRecord.value());
                    });
                });
            });
            try(var c=dataSource.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) AS entries, sum(amount) AS total FROM wallet_entries")) {
                r.next(); assertThat(r.getInt("entries")).isEqualTo(2);
                assertThat(r.getBigDecimal("total")).isEqualByComparingTo("0");
            }
        }
    }

    private void capture(String name, String payload) {
        try {
            var folder=java.nio.file.Path.of("target","coordinator-contract-examples");
            java.nio.file.Files.createDirectories(folder);
            java.nio.file.Files.writeString(folder.resolve(name+".json"),payload);
        } catch (java.io.IOException e) { throw new AssertionError("Cannot record contract example",e); }
    }

    private <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json,type); }
        catch (java.io.IOException e) { throw new AssertionError("Invalid Kafka output",e); }
    }

    private String status(UUID id) throws Exception {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT status FROM offers WHERE id=?")) {
            s.setObject(1,id); try(var r=s.executeQuery()) { return r.next()?r.getString(1):"ABSENT"; }
        }
    }
}

