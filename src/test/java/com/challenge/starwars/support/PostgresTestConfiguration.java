package com.challenge.starwars.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Un único contenedor PostgreSQL para toda la ejecución de tests. Se inicia una vez en un inicializador estático y se entrega a cada
 * contexto de Spring como un {@link ServiceConnection}, de modo que contextos con distinta configuración lo comparten igualmente.
 * El sidecar Ryuk de Testcontainers lo elimina cuando termina la JVM.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }
}
