package co.inter.piggies.support;

import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;

/**
 * Base dos testes de integração: injeta as URLs dos containers no Micronaut.
 * Subclasses usam {@code @MicronautTest(transactional = false)}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IntegrationTest implements TestPropertyProvider {

    @Inject
    @Client("/")
    protected HttpClient httpClient;

    @Override
    public Map<String, String> getProperties() {
        return Map.of(
                "datasources.default.url", Containers.POSTGRES.getJdbcUrl(),
                "datasources.default.username", Containers.POSTGRES.getUsername(),
                "datasources.default.password", Containers.POSTGRES.getPassword(),
                "kafka.bootstrap.servers", Containers.KAFKA.getBootstrapServers());
    }

    protected BlockingHttpClient http() {
        return httpClient.toBlocking();
    }
}
