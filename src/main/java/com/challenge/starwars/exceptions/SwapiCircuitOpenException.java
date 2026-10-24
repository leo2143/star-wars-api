package com.challenge.starwars.exceptions;

import java.time.Duration;

/**
 * El circuit breaker frente a swapi.tech está abierto: la llamada se rechazó sin llegar a la red. Se mapea a 503 con
 * Retry-After.
 */
public class SwapiCircuitOpenException extends SwapiException {

    private final transient Duration retryAfter;

    public SwapiCircuitOpenException(String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    /**
     * Cota superior de la espera: el tiempo que el breaker permanece abierto desde que se abrió. La espera real puede ser menor.
     */
    public Duration retryAfter() {
        return retryAfter;
    }
}
