import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Manual smoke test against docker-compose.coordinator.yml. Creates demonstration data. */
class CoordinatorDemo {
    public static void main(String[] args) throws Exception {
        String bootstrap = "localhost:59093";
        String jdbc = "jdbc:postgresql://localhost:55433/slp";
        UUID sell=UUID.randomUUID(),low=UUID.randomUUID(),high=UUID.randomUUID();
        UUID seller=UUID.randomUUID(),buyer=UUID.randomUUID();
        try(var connection=DriverManager.getConnection(jdbc,"slp","slp");
            var producer=new KafkaProducer<String,String>(Map.of("bootstrap.servers",bootstrap,"acks","all","max.block.ms",10000,
                    "request.timeout.ms",5000,"delivery.timeout.ms",10000),new StringSerializer(),new StringSerializer());
            var consumer=new KafkaConsumer<String,String>(Map.of("bootstrap.servers",bootstrap,"group.id","manual-"+UUID.randomUUID(),
                    "auto.offset.reset","earliest","enable.auto.commit",false),new StringDeserializer(),new StringDeserializer())) {
            consumer.subscribe(List.of("slp.trades.executed","slp.exchange-rate.updated"));
            send(producer,event(sell,"SELL",seller,"20.00"));
            send(producer,event(low,"BUY",UUID.randomUUID(),"10.00"));
            waitForStatus(connection,sell,"OPEN");
            waitForStatus(connection,low,"OPEN");
            System.out.println("Venda 20 e compra 10: OPEN, sem match entre elas.");
            String highEvent=event(high,"BUY",buyer,"30.00");
            send(producer,highEvent);
            send(producer,highEvent); // identical delivery must not repeat the transfer
            waitForStatus(connection,high,"EXECUTED");
            waitForStatus(connection,sell,"EXECUTED");
            UUID tradeId;
            try(var statement=connection.prepareStatement("SELECT id,price,sell_offer_id FROM trades WHERE buy_offer_id=?")) {
                statement.setObject(1,high);
                try(var row=statement.executeQuery()) {
                    if(!row.next()) throw new AssertionError("Trade nao encontrado");
                    tradeId=row.getObject(1,UUID.class);
                    if(row.getBigDecimal(2).compareTo(new BigDecimal("25.00"))!=0 || !row.getObject(3,UUID.class).equals(sell))
                        throw new AssertionError("Outra oferta aberta interferiu na demonstracao; use ambiente dedicado.");
                }
            }
            boolean tradeReceived=false,rateReceived=false;
            Instant deadline=Instant.now().plusSeconds(40);
            while(Instant.now().isBefore(deadline) && !(tradeReceived && rateReceived)) {
                for(var record:consumer.poll(Duration.ofMillis(250))) {
                    if(!record.value().contains(tradeId.toString())) continue;
                    if(record.topic().equals("slp.trades.executed")) tradeReceived=true;
                    if(record.topic().equals("slp.exchange-rate.updated")) rateReceived=true;
                    System.out.println(record.topic()+" -> "+record.value());
                }
            }
            if(!tradeReceived || !rateReceived) throw new AssertionError("Eventos de saida nao recebidos no prazo");
            try(var statement=connection.prepareStatement("SELECT count(*),sum(amount) FROM wallet_entries WHERE trade_id=?")) {
                statement.setObject(1,tradeId);
                try(var row=statement.executeQuery()) {
                    row.next();
                    if(row.getInt(1)!=2 || row.getBigDecimal(2).signum()!=0) throw new AssertionError("Ledger inconsistente");
                }
            }
            System.out.println("OK: trade="+tradeId+", preco=25.00, debito/credito e dois eventos confirmados.");
            System.out.println("Oferta de venda="+sell+"; compra executada="+high+"; compra pendente="+low);
        }
    }
    static String event(UUID offer,String side,UUID participant,String price) {
        return "{\"eventId\":\"%s\",\"offerId\":\"%s\",\"side\":\"%s\",\"participantId\":\"%s\",\"price\":%s,\"createdAt\":\"%s\"}"
                .formatted(UUID.randomUUID(),offer,side,participant,price,Instant.now());
    }
    static void send(KafkaProducer<String,String> producer,String event) throws Exception {
        producer.send(new ProducerRecord<>("slp.offers.created","PIGGY-USD",event)).get(15,TimeUnit.SECONDS);
    }
    static void waitForStatus(Connection connection,UUID offer,String expected) throws Exception {
        Instant deadline=Instant.now().plusSeconds(30);
        while(Instant.now().isBefore(deadline)) {
            try(var statement=connection.prepareStatement("SELECT status FROM offers WHERE id=?")) {
                statement.setObject(1,offer);
                try(var row=statement.executeQuery()) { if(row.next() && expected.equals(row.getString(1))) return; }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Oferta "+offer+" nao chegou a "+expected);
    }
}
