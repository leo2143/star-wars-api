package com.challenge.starwars.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Expone el {@link Clock} del sistema en UTC como bean, para que los componentes dependientes del tiempo
 * puedan sustituirlo por un reloj controlado en las pruebas.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
