package com.challenge.starwars.exceptions;

/**
 * El refresh token no puede usarse. Un único mensaje fijo para cualquier causa, de modo que quien llama no pueda distinguirlas.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Invalid refresh token.");
    }
}
