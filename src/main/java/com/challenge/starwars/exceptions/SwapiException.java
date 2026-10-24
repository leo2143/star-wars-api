package com.challenge.starwars.exceptions;

/**
 * Tipo base de los fallos al comunicarse con swapi.tech. Los mensajes nunca llegan al cliente HTTP.
 */
public abstract class SwapiException extends RuntimeException {

    protected SwapiException(String message, Throwable cause) {
        super(message, cause);
    }
}
