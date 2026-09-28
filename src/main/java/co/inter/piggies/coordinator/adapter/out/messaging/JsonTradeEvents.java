package co.inter.piggies.coordinator.adapter.out.messaging;

import co.inter.piggies.contracts.*;
import co.inter.piggies.coordinator.domain.model.Trade;
import co.inter.piggies.coordinator.domain.port.out.*;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Singleton
public class JsonTradeEvents implements TradeEvents {
    private final JsonMapper mapper;
    public JsonTradeEvents(JsonMapper mapper) { this.mapper = mapper; }

    @Override
    public List<OutboxMessage> from(Trade trade) {
        UUID tradeEventId = UUID.randomUUID();
        UUID rateEventId = UUID.randomUUID();
        var executed = new TradeExecutedMessage(tradeEventId, trade.id(), trade.buy().id(), trade.sell().id(),
                trade.buy().participantId(), trade.sell().participantId(), trade.price(), trade.executedAt());
        var rate = new ExchangeRateUpdatedMessage(rateEventId,trade.id(),Topics.PAIR_KEY,trade.price(),trade.executedAt());
        try {
            return List.of(new OutboxMessage(tradeEventId,Topics.TRADES_EXECUTED,trade.id().toString(),mapper.writeValueAsString(executed),trade.executedAt()),
                    new OutboxMessage(rateEventId,Topics.EXCHANGE_RATE_UPDATED,Topics.PAIR_KEY,mapper.writeValueAsString(rate),trade.executedAt()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialize trade events; transaction must roll back", e);
        }
    }
}
