package com.challenge.starwars.clients;

import com.challenge.starwars.config.SwapiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Construye el {@link RestClient} asociado a swapi.tech.
 *
 * <p>Parte del {@link RestClient.Builder} autoconfigurado por Spring Boot, que es donde se aplican las propiedades
 * {@code spring.http.clients.connect-timeout} y {@code read-timeout}.
 */
@Configuration(proxyBeanMethods = false)
class SwapiClientConfig {

    @Bean
    RestClient swapiRestClient(RestClient.Builder builder, SwapiProperties properties) {
        return builder.baseUrl(properties.baseUrl()).defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).defaultHeader(HttpHeaders.USER_AGENT, "star-wars-challenge-api").requestInterceptor(new SwapiRequestLoggingInterceptor()).build();
    }
}
