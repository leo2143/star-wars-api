package com.challenge.starwars.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuración de la integración con SWAPI ({@code app.swapi.*}). Los timeouts de conexión y lectura se configuran mediante las
 * propiedades {@code spring.http.clients.*} de Spring Boot.
 *
 * @param baseUrl    raíz de la API de swapi.tech, sin barra final
 * @param pageSize   tamaño fijo de cada página que expone esta API
 * @param catalogTtl intervalo de refresco de un catálogo cacheado de swapi.tech (una colección completa): pasado este tiempo se sigue
 *                   sirviendo el catálogo anterior mientras se recarga en segundo plano; no hay expiración dura.
 *                   Si la recarga falla (también si el circuit breaker la rechaza) se conserva el catálogo anterior y el
 *                   siguiente intento ocurre pasado otro intervalo
 * @param retry      política de reintentos para fallos transitorios de swapi.tech
 * @param circuitBreaker corte de circuito que deja de llamar a swapi.tech mientras está caído
 */
@Validated
@ConfigurationProperties(prefix = "app.swapi")
public record SwapiProperties(@NotBlank String baseUrl, @Min(1) @Max(100) int pageSize, @NotNull Duration catalogTtl,
                              @NotNull @Valid Retry retry, @NotNull @Valid CircuitBreaker circuitBreaker) {

    /**
     * Ventana deslizante por número de llamadas: el breaker se abre cuando, con al menos {@code minimumNumberOfCalls}
     * llamadas registradas entre las últimas {@code slidingWindowSize}, el porcentaje de fallos alcanza
     * {@code failureRateThreshold}. Cuenta una llamada por operación lógica (después de los reintentos).
     *
     * @param failureRateThreshold       porcentaje de fallos (1 a 100) que abre el breaker
     * @param slidingWindowSize          cuántas llamadas recientes se evalúan
     * @param minimumNumberOfCalls       llamadas necesarias antes de calcular el porcentaje; no puede superar la ventana
     * @param waitDurationInOpenState    cuánto rechaza llamadas el breaker abierto antes de probar de nuevo
     * @param permittedCallsInHalfOpenState llamadas de prueba permitidas al volver a probar; si aciertan, el breaker se cierra
     */
    public record CircuitBreaker(@Min(1) @Max(100) int failureRateThreshold, @Min(1) @Max(1000) int slidingWindowSize,
                                 @Min(1) @Max(1000) int minimumNumberOfCalls, @NotNull Duration waitDurationInOpenState,
                                 @Min(1) @Max(100) int permittedCallsInHalfOpenState) {

        public CircuitBreaker {
            if (waitDurationInOpenState != null && waitDurationInOpenState.compareTo(Duration.ofMillis(1)) < 0) {
                throw new IllegalArgumentException(
                        "app.swapi.circuit-breaker.wait-duration-in-open-state must be at least 1ms");
            }
            // Los rangos básicos (@Min/@Max) los informa la validación; esta regla solo compara valores ya válidos.
            boolean basicRangesHold = slidingWindowSize >= 1 && minimumNumberOfCalls >= 1;
            if (basicRangesHold && minimumNumberOfCalls > slidingWindowSize) {
                throw new IllegalArgumentException(
                        "app.swapi.circuit-breaker.minimum-number-of-calls must not exceed sliding-window-size");
            }
        }
    }

    /**
     * @param maxRetries reintentos después del primer intento (0 desactiva los reintentos)
     * @param delay      espera antes del primer reintento; se duplica en cada reintento siguiente
     */
    public record Retry(@Min(0) @Max(5) int maxRetries, @NotNull Duration delay) {

        public Retry {
            if (delay != null && delay.isNegative()) {
                throw new IllegalArgumentException("app.swapi.retry.delay must not be negative");
            }
        }
    }
}
