package com.challenge.starwars.controllers.support;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.*;

/**
 * Marca un controlador como listado de solo lectura de un recurso de swapi.tech: un controlador REST montado en
 * la ruta indicada, fijado a la versión {@code v1} de la API (ver {@code spring.mvc.apiversion.*}). Cada controlador declara su propia etiqueta OpenAPI. El customizer de OpenAPI agrega las respuestas
 * de error comunes a cada operación de un controlador que lleve esta anotación.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
@RequestMapping(version = "v1")
public @interface ListingApi {

    /**
     * Ruta completa del listado, con la versión como variable de ruta, p. ej. {@code /api/{version}/people}.
     */
    @AliasFor(annotation = RequestMapping.class, attribute = "path")
    String[] value();
}
