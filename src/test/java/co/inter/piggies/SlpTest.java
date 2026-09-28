package co.inter.piggies;

import co.inter.piggies.support.IntegrationTest;
import io.micronaut.http.HttpRequest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Sobe a aplicação com Postgres + Kafka reais e confere o health (Flyway + validate + Kafka ok). */
@MicronautTest(transactional = false)
class SlpTest extends IntegrationTest {

    @Test
    void applicationStartsHealthy() {
        String body = http().retrieve(HttpRequest.GET("/health"));
        assertThat(body).contains("UP");
    }
}
