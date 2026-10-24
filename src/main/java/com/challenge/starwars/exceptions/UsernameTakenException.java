package com.challenge.starwars.exceptions;

/**
 * El nombre de usuario solicitado ya existe. No lleva datos: la respuesta es un mensaje fijo.
 */
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException() {
        super("Username is not available.");
    }
}
