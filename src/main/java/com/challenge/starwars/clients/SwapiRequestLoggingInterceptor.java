package com.challenge.starwars.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

/**
 * Registra a nivel DEBUG una línea por cada intento de llamada a swapi.tech: método, URI, estado y duración.
 *
 * <p>Al ejecutarse por intento (el reintento envuelve a todo el cliente), los reintentos quedan visibles. Nunca registra
 * cabeceras, cuerpos ni la información de usuario de la URI. Se activa con el nivel de log de este paquete
 * ({@code SWAPI_LOG_LEVEL}) y, con DEBUG desactivado, no hace nada salvo delegar.
 */
class SwapiRequestLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SwapiRequestLoggingInterceptor.class);

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String safe(URI uri) {
        StringBuilder text = new StringBuilder();
        if (uri.getScheme() != null) {
            text.append(uri.getScheme()).append("://");
        }
        if (uri.getHost() != null) {
            text.append(uri.getHost());
            if (uri.getPort() != -1) {
                text.append(':').append(uri.getPort());
            }
        }
        text.append(uri.getRawPath() == null ? "" : uri.getRawPath());
        if (uri.getRawQuery() != null) {
            text.append('?').append(uri.getRawQuery());
        }
        return text.toString();
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!log.isDebugEnabled()) {
            return execution.execute(request, body);
        }
        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            log.debug("SWAPI {} {} -> {} in {} ms", request.getMethod(), safe(request.getURI()),
                    response.getStatusCode().value(), elapsedMillis(start));
            return response;
        } catch (IOException e) {
            log.debug("SWAPI {} {} -> failed with {} in {} ms", request.getMethod(), safe(request.getURI()),
                    e.getClass().getSimpleName(), elapsedMillis(start));
            throw e;
        }
    }
}
