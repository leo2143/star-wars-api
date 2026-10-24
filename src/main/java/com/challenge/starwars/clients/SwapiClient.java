package com.challenge.starwars.clients;

import com.challenge.starwars.config.SwapiProperties;
import com.challenge.starwars.dtos.swapi.SwapiCollectionResponse;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.swapi.SwapiListResponse;
import com.challenge.starwars.dtos.swapi.SwapiPage;
import com.challenge.starwars.exceptions.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * El único componente que habla con swapi.tech.
 *
 * <p>Oculta la forma de las respuestas detrás de {@link SwapiItem} y traduce cada problema de transporte a una
 * {@code Swapi*Exception}. Un circuit breaker deja de llamar a swapi.tech mientras falla de forma sostenida y rechaza
 * las llamadas con {@link SwapiCircuitOpenException}.
 */
@Component
public class SwapiClient {

    private static final Logger log = LoggerFactory.getLogger(SwapiClient.class);
    private static final Duration MAX_RETRY_AFTER = Duration.ofHours(1);

    private final RestClient restClient;
    private final Clock clock;
    private final RetryTemplate retryTemplate;
    private final CircuitBreaker circuitBreaker;
    private final Duration circuitOpenWait;

    public SwapiClient(RestClient swapiRestClient, Clock clock, SwapiProperties properties) {
        this.restClient = swapiRestClient;
        this.clock = clock;
        this.circuitOpenWait = properties.circuitBreaker().waitDurationInOpenState();
        this.retryTemplate = new RetryTemplate(transientFailurePolicy(properties.retry()));
        this.circuitBreaker = buildCircuitBreaker(properties.circuitBreaker(), clock);
    }

    /**
     * Breaker que envuelve la llamada ya con reintentos, de modo que registra el resultado final de cada operación. El
     * reloj inyectado gobierna la espera en estado abierto, lo que permite adelantarla en los tests sin dormir.
     */
    private static CircuitBreaker buildCircuitBreaker(SwapiProperties.CircuitBreaker settings, Clock clock) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.failureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumNumberOfCalls())
                .waitDurationInOpenState(settings.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(settings.permittedCallsInHalfOpenState())
                .clock(clock)
                .recordException(SwapiClient::isUpstreamMalfunction)
                .ignoreException(SwapiClient::isCallerOrRateLimitError)
                .build();
        CircuitBreaker breaker = CircuitBreaker.of("swapi", config);
        breaker.getEventPublisher().onStateTransition(event ->
                log.warn("SWAPI circuit breaker changed state: {}", event.getStateTransition()));
        return breaker;
    }

    /**
     * Fallos que indican un problema de swapi.tech: 5xx, timeouts, errores de conexión y cuerpos ilegibles.
     */
    private static boolean isUpstreamMalfunction(Throwable error) {
        if (error instanceof RestClientResponseException response) {
            return !response.getStatusCode().is4xxClientError();
        }
        return error instanceof RestClientException || error instanceof SwapiInvalidResponseException;
    }

    /**
     * Los 4xx (incluido el 429, que tiene su propio manejo de Retry-After) no dicen nada sobre la salud de swapi.tech: ni
     * abren el breaker ni cuentan como acierto.
     */
    private static boolean isCallerOrRateLimitError(Throwable error) {
        return error instanceof RestClientResponseException response && response.getStatusCode().is4xxClientError();
    }

    /**
     * Reintenta solo lo que una repetición puede arreglar: respuestas 5xx y fallos de conexión.
     */
    private static RetryPolicy transientFailurePolicy(SwapiProperties.Retry retry) {
        return RetryPolicy.builder()
                .maxRetries(retry.maxRetries())
                .delay(retry.delay())
                .multiplier(2)
                .jitter(retry.delay().dividedBy(4))
                .predicate(SwapiClient::isTransient)
                .build();
    }

    private static boolean isTransient(Throwable error) {
        if (error instanceof RestClientResponseException response) {
            return response.getStatusCode().is5xxServerError();
        }
        return error instanceof ResourceAccessException && !isTimeout(error);
    }

    private static Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof InterruptedIOException || t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Una página de un recurso paginado, con los atributos expandidos de cada elemento.
     */
    public SwapiPage fetchPage(SwapiResource resource, int page, int limit) {
        SwapiListResponse body = callWithRetry(resource, () -> restClient.get()
                .uri(uri -> uri.path("/{resource}")
                        .queryParam("page", "{page}")
                        .queryParam("limit", "{limit}")
                        .queryParam("expanded", "true")
                        .build(Map.of("resource", resource.path(), "page", page, "limit", limit)))
                .retrieve()
                .body(SwapiListResponse.class),
                listing -> listing != null && listing.results() != null && listing.totalPages() != null,
                "Listing response without results or total_pages");
        return new SwapiPage(body.results(), body.totalPages());
    }

    /**
     * Todos los elementos de un recurso sin paginar (films) en una sola llamada.
     */
    public List<SwapiItem> fetchAll(SwapiResource resource) {
        SwapiCollectionResponse body = callWithRetry(resource, () -> restClient.get()
                .uri(uri -> uri.path("/{resource}").build(Map.of("resource", resource.path())))
                .retrieve()
                .body(SwapiCollectionResponse.class),
                collection -> collection != null && collection.result() != null,
                "Collection response without a result array");
        return body.result();
    }

    /**
     * Ejecuta la llamada con reintentos dentro del breaker. La validación de forma corre dentro del breaker, para que un
     * 200 con un cuerpo inservible cuente como fallo, pero fuera del reintento, porque repetir no lo arreglaría.
     */
    private <T> T callWithRetry(SwapiResource resource, Supplier<T> call, Predicate<T> isWellFormed,
                                String malformedMessage) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                T body = retryTemplate.invoke(call);
                if (!isWellFormed.test(body)) {
                    throw new SwapiInvalidResponseException(malformedMessage);
                }
                return body;
            });
        } catch (CallNotPermittedException e) {
            throw new SwapiCircuitOpenException("SWAPI circuit breaker is open for " + resource.path(),
                    circuitOpenWait, e);
        } catch (RestClientResponseException e) {
            throw toSwapiException(resource, e);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                log.warn("SWAPI timeout on {}", resource.path());
                throw new SwapiTimeoutException("Timeout calling SWAPI " + resource.path(), e);
            }
            log.warn("SWAPI unreachable on {}: {}", resource.path(), e.getClass().getSimpleName());
            throw new SwapiUnavailableException("SWAPI unreachable for " + resource.path(), e);
        } catch (RestClientException e) {
            log.warn("SWAPI response could not be decoded on {}", resource.path());
            throw new SwapiInvalidResponseException("Unreadable SWAPI response for " + resource.path(), e);
        }
    }

    private SwapiException toSwapiException(SwapiResource resource, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (e.getStatusCode().is2xxSuccessful()) {
            return new SwapiInvalidResponseException("Unreadable SWAPI response for " + resource.path(), e);
        }
        log.warn("SWAPI answered {} on {}", status, resource.path());
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new SwapiRateLimitedException("SWAPI rate limit reached", retryAfter(e.getResponseHeaders()), e);
        }
        return new SwapiUnavailableException("SWAPI answered " + status + " for " + resource.path(), e);
    }

    private Duration retryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        Long seconds = parseLong(headers.getFirst(HttpHeaders.RETRY_AFTER));
        if (seconds == null) {
            Long resetEpoch = parseLong(headers.getFirst("x-ratelimit-reset"));
            seconds = resetEpoch == null ? null : resetEpoch - clock.instant().getEpochSecond();
        }
        if (seconds == null || seconds <= 0) {
            return null;
        }
        Duration wait = Duration.ofSeconds(seconds);
        return wait.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : wait;
    }
}
