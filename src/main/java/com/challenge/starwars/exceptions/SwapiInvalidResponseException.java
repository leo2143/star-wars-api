package com.challenge.starwars.exceptions;

/**
 * swapi.tech respondió con éxito pero con un cuerpo que esta API no puede interpretar. Se mapea a 502.
 */
public class SwapiInvalidResponseException extends SwapiException {

    public SwapiInvalidResponseException(String message) {
        super(message, null);
    }

    public SwapiInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
