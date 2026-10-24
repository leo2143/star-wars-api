package com.challenge.starwars.exceptions;

/**
 * swapi.tech es inalcanzable o respondió con un 5xx / estado inesperado. Se mapea a 502.
 */
public class SwapiUnavailableException extends SwapiException {

    public SwapiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
