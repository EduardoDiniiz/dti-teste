package co.inter.piggies.coordinator.domain.port.in;

import co.inter.piggies.coordinator.domain.model.OrderOffer;
import java.util.UUID;

public interface CoordinateOfferUseCase {
    void coordinate(UUID eventId, OrderOffer offer);
}
