package com.challenge.starwars.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Orígenes de navegador autorizados a llamar a esta API ({@code app.cors.allowed-origins}).
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("app.cors.allowed-origins must list explicit origins, not wildcards");
        }
    }
}
