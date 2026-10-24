package com.challenge.starwars.exceptions;

/**
 * Se solicitó por identificador un recurso que no existe en el origen. Nunca incluye detalles del origen.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, long id) {
        super(resource + " " + id + " not found");
    }
}
