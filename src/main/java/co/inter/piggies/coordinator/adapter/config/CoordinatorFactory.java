package co.inter.piggies.coordinator.adapter.config;

import co.inter.piggies.coordinator.application.CoordinateOfferService;
import co.inter.piggies.coordinator.domain.model.MatchingEngine;
import co.inter.piggies.coordinator.domain.port.in.CoordinateOfferUseCase;
import co.inter.piggies.coordinator.domain.port.out.*;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import java.time.Clock;

@Factory
public class CoordinatorFactory {
    @Singleton
    public CoordinateOfferUseCase coordinator(CoordinatorStore store, TradeEvents events) {
        return new CoordinateOfferService(store, new MatchingEngine(), events, Clock.systemUTC());
    }
}
