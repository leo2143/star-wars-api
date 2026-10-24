package com.challenge.starwars.exceptions.handler;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construye problem details según RFC 9457 con una forma consistente entre los advice y los filtros de servlet.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }

    /**
     * Escribe un problem directamente en la respuesta, para filtros que se ejecutan fuera de los message converters de Spring MVC.
     */
    public static void write(HttpServletResponse response, JsonMapper jsonMapper, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail problem = of(status, detail);
        URI type = problem.getType();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type == null ? "about:blank" : type.toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
