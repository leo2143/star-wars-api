package com.challenge.starwars.security.jwt;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuración de JWT ({@code app.jwt.*}).
 *
 * <p>{@code secretBase64} deliberadamente no se valida con bean validation: Spring Boot imprime el
 * valor rechazado en su error de binding. {@link JwtSecretKeys} lo valida sin devolverlo.
 *
 * @param secretBase64 Base64 de la clave HMAC; debe decodificarse a al menos 32 bytes
 * @param issuer       valor del claim {@code iss}, obligatorio en cada token
 * @param audience     valor del claim {@code aud}, obligatorio en cada token
 * @param ttlSeconds   duración del access token en segundos
 * @param refreshTtl   duración de una sesión, contada desde el login; los refresh tokens rotados heredan su expiración
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secretBase64,
        @NotBlank String issuer,
        @NotBlank String audience,
        @Min(60) @Max(86_400) long ttlSeconds,
        @DefaultValue("7d") @NotNull Duration refreshTtl) {

    @AssertTrue(message = "app.jwt.refresh-ttl must be greater than zero")
    boolean isRefreshTtlPositive() {
        return refreshTtl == null || refreshTtl.isPositive();
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", audience=" + audience + ", ttlSeconds=" + ttlSeconds
                + ", refreshTtl=" + refreshTtl + "]";
    }
}
