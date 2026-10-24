package com.challenge.starwars.support;

/** Prefijo de la versión de la API que ejercitan los tests; se define una sola vez y cada test lo usa de forma explícita. */
public final class TestPaths {

    /** Versión 1 de la API, fijada a propósito: los tests de v1 no deben seguir una "última versión". */
    public static final String V1 = "/api/v1";

    private TestPaths() {
    }
}
