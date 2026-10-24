package com.challenge.starwars.exceptions;

/**
 * swapi.tech no respondió dentro de los timeouts configurados. Se mapea a 504.
 */
public class SwapiTimeoutException extends SwapiException {

    public SwapiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
