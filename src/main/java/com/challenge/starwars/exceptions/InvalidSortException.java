package com.challenge.starwars.exceptions;

/**
 * El parámetro {@code sort} nombra un campo por el que el recurso no puede ordenarse. El mensaje es seguro para devolver.
 */
public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String message) {
        super(message);
    }
}
