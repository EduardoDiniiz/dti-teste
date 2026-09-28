package co.inter.piggies.shared.adapter.out.config;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.ZoneId;

@Factory
class ClockFactory {

    @Singleton
    Clock clock(@Value("${app.zone:America/Sao_Paulo}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
