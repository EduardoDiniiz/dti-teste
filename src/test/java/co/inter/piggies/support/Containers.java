package co.inter.piggies.support;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Singleton containers: sobem uma vez por JVM e são compartilhados por todas as classes de teste. */
public final class Containers {

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("slp")
            .withUsername("slp")
            .withPassword("slp");

    public static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));

    static {
        Startables.deepStart(POSTGRES, KAFKA).join();
    }

    private Containers() {
    }
}
