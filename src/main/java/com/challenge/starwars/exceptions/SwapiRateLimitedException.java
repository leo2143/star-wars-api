package com.challenge.starwars.exceptions;

import java.time.Duration;
import java.util.Optional;

/**
 * swapi.tech rechazó la llamada con 429 (100 peticiones por ventana). Se mapea a 503 con Retry-After.
 */
public class SwapiRateLimitedException extends SwapiException {

    private final transient Duration retryAfter;

    public SwapiRateLimitedException(String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    /**
     * Cuánto esperar antes de reintentar, cuando SWAPI lo indicó.
     */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
