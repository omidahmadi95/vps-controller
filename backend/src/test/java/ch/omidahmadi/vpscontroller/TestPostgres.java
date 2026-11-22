package ch.omidahmadi.vpscontroller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A throwaway PostgreSQL for the integration tests. Managed and started by Spring for the whole context. */
@TestConfiguration(proxyBeanMethods = false)
public class TestPostgres {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17.11-alpine");
    }
}
