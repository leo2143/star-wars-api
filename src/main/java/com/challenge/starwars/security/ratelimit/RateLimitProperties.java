package com.challenge.starwars.security.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Límites por cliente para los endpoints de autenticación ({@code app.rate-limit.*}).
 *
 * @param login    {@code POST /api/v1/auth/login}
 * @param refresh  {@code POST /api/v1/auth/refresh}
 * @param register {@code POST /api/v1/auth/register}
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @NotNull @Valid Limit login,
        @NotNull @Valid Limit refresh,
        @NotNull @Valid Limit register) {

    /**
     * @param capacity peticiones permitidas por {@code period} (también el tamaño de la ráfaga)
     * @param period   tiempo en el que se recargan los {@code capacity} tokens
     */
    public record Limit(int capacity, Duration period) {

        public Limit {
            if (capacity < 1) {
                throw new IllegalArgumentException("rate limit capacity must be at least 1");
            }
            if (period == null || period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("rate limit period must be greater than zero");
            }
        }
    }
}
