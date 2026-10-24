package com.challenge.starwars.security.ratelimit;

import com.challenge.starwars.exceptions.handler.ProblemDetails;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * Limita los endpoints públicos de autenticación por IP de cliente. Un token bucket por endpoint e IP,
 * guardado en memoria (por lo que los límites son por instancia). La recarga es gradual: los tokens vuelven poco a poco, lo que evita
 * la ráfaga en el borde de una ventana y hace que {@code Retry-After} sea el tiempo hasta el siguiente token.
 *
 * <p>La IP del cliente es {@code getRemoteAddr()}; detrás de un proxy el contenedor la resuelve a partir de las
 * cabeceras de reenvío (ver {@code server.forward-headers-strategy}), nunca se parsea aquí.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_BUCKETS = 10_000;
    private final List<Rule> rules;
    private final Cache<String, Bucket> buckets;
    private final JsonMapper jsonMapper;
    public RateLimitFilter(RateLimitProperties properties, JsonMapper jsonMapper) {
        this.rules = List.of(
                rule("/api/v1/auth/login", properties.login()),
                rule("/api/v1/auth/refresh", properties.refresh()),
                rule("/api/v1/auth/register", properties.register()));
        // Un bucket inactivo durante un período completo de recarga es indistinguible de uno nuevo, por lo que se puede descartar.
        Duration longestPeriod = Stream.of(properties.login(), properties.refresh(), properties.register())
                .map(RateLimitProperties.Limit::period).max(Duration::compareTo).orElseThrow();
        this.buckets = Caffeine.newBuilder().expireAfterAccess(longestPeriod).maximumSize(MAX_BUCKETS).build();
        this.jsonMapper = jsonMapper;
    }

    private static Rule rule(String path, RateLimitProperties.Limit limit) {
        return new Rule(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, path), path, limit);
    }

    private static Bucket newBucket(RateLimitProperties.Limit limit) {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth.capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.period()))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = rules.stream().filter(candidate -> candidate.matcher().matches(request)).findFirst().orElse(null);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }
        Bucket bucket = buckets.get(rule.endpoint() + "|" + request.getRemoteAddr(), key -> newBucket(rule.limit()));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, Math.ceilDiv(probe.getNanosToWaitForRefill(), 1_000_000_000L));
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        ProblemDetails.write(response, jsonMapper, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again later.");
    }

    private record Rule(RequestMatcher matcher, String endpoint, RateLimitProperties.Limit limit) {
    }
}
