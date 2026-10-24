package com.challenge.starwars.exceptions.handler;

import com.challenge.starwars.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Único lugar donde los fallos se convierten en respuestas HTTP, siempre como {@code application/problem+json}.
 *
 * <p>Los mensajes y causas de las excepciones se registran en el log, nunca se devuelven: los clientes solo ven los textos fijos de abajo.
 * Las excepciones propias de Spring MVC (405, 404, cuerpo ilegible...) conservan los valores por defecto de la clase base.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ProblemDetail invalidParameters(List<Map<String, String>> errors) {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "One or more request parameters are invalid.");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> error(String field, String message) {
        return Map.of("field", String.valueOf(field), "message", String.valueOf(message));
    }

    /**
     * Los fallos de conversión de otro modo devolverían el valor rechazado, por eso reciben un mensaje fijo.
     */
    private static Map<String, String> describe(FieldError error) {
        // Las reglas entre campos son métodos booleanos como isPasswordWithinBcryptLimit; los clientes las ven sobre "password".
        String field = error.getField().startsWith("password") ? "password" : error.getField();
        return error(field, error.isBindingFailure() ? "invalid value" : error.getDefaultMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream().map(GlobalExceptionHandler::describe).toList();
        return invalidParameters(ex, errors, headers, request);
    }

    /**
     * Violaciones de restricciones en variables de ruta o parámetros de la petición, p. ej. {@code @Positive} sobre un id.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream().flatMap(result -> result.getResolvableErrors().stream().map(error -> error(result.getMethodParameter().getParameterName(), error.getDefaultMessage()))).toList();
        return invalidParameters(ex, errors, headers, request);
    }

    /**
     * Una variable de ruta que no es un número (o desborda): el valor rechazado nunca se devuelve.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return invalidParameters(ex, List.of(error(ex.getPropertyName(), "invalid value")), headers, request);
    }

    private ResponseEntity<Object> invalidParameters(Exception ex, List<Map<String, String>> errors, HttpHeaders headers, WebRequest request) {
        return handleExceptionInternal(ex, invalidParameters(errors), headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Un campo {@code sort} que el recurso no ofrece; el mensaje lista los permitidos.
     */
    @ExceptionHandler(InvalidSortException.class)
    ProblemDetail handleInvalidSort(InvalidSortException ex) {
        return invalidParameters(List.of(error("sort", ex.getMessage())));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        log.debug("Not found: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "The requested resource was not found.");
    }

    @ExceptionHandler(SwapiTimeoutException.class)
    ProblemDetail handleTimeout(SwapiTimeoutException ex) {
        log.warn("Upstream timeout: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.GATEWAY_TIMEOUT, "The Star Wars data provider did not respond in time.");
    }

    @ExceptionHandler({SwapiUnavailableException.class, SwapiInvalidResponseException.class})
    ProblemDetail handleUpstreamFailure(SwapiException ex) {
        log.warn("Upstream failure: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.BAD_GATEWAY, "The Star Wars data provider failed to answer correctly.");
    }

    @ExceptionHandler(SwapiRateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimit(SwapiRateLimitedException ex) {
        log.warn("Upstream rate limit reached");
        ProblemDetail problem = ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "The Star Wars data provider is temporarily limiting requests. Try again later.");
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);
        ex.retryAfter().ifPresent(wait -> response.header(HttpHeaders.RETRY_AFTER, String.valueOf(wait.toSeconds())));
        return response.body(problem);
    }

    @ExceptionHandler(SwapiCircuitOpenException.class)
    ResponseEntity<ProblemDetail> handleCircuitOpen(SwapiCircuitOpenException ex) {
        log.debug("Upstream circuit open: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "The Star Wars data provider is temporarily unavailable. Try again later.");
        // Redondeo hacia arriba: la cabecera es una cota superior de la espera.
        long retryAfterSeconds = ex.retryAfter().plusNanos(999_999_999).toSeconds();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(problem);
    }

    @ExceptionHandler(UsernameTakenException.class)
    ProblemDetail handleUsernameTaken(UsernameTakenException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }
}
