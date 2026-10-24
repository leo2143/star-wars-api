package com.challenge.starwars.security;

import com.challenge.starwars.exceptions.handler.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * 401 / 403 producidos dentro de la cadena de filtros de seguridad, donde el controller advice nunca se ejecuta. Conserva el
 * desafío estándar {@code WWW-Authenticate} y agrega un cuerpo RFC 9457 consistente con el resto de la API.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint challenge = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler forbiddenChallenge = new BearerTokenAccessDeniedHandler();
    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        challenge.commence(request, response, authException);
        ProblemDetails.write(response, jsonMapper, HttpStatus.UNAUTHORIZED,
                "A valid Bearer token is required to access this resource.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        forbiddenChallenge.handle(request, response, accessDeniedException);
        ProblemDetails.write(response, jsonMapper, HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource.");
    }
}
