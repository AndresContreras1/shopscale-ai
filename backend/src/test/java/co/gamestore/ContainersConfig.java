package co.gamestore;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The services the tests run against, as containers.
 *
 * <p>They are declared as beans so Spring Boot wires the connection details itself, and they are
 * static so one PostgreSQL and one Redis are started for the whole test run rather than one per
 * test class. The image tags are pinned: a test that passes today must pass tomorrow.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return REDIS;
    }
}
