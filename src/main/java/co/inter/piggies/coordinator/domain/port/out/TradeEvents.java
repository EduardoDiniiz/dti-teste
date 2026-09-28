package co.inter.piggies.coordinator.domain.port.out;

import co.inter.piggies.coordinator.domain.model.Trade;
import java.util.List;

public interface TradeEvents {
    List<OutboxMessage> from(Trade trade);
}
