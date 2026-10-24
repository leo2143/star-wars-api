package com.challenge.starwars.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cuenta demo ({@code app.auth.*}, alimentada por {@code APP_AUTH_USERNAME} / {@code APP_AUTH_PASSWORD}).
 * Ambos valores son obligatorios. Al arrancar, {@link com.challenge.starwars.config.DemoUserInitializer} guarda la cuenta con un hash BCrypt.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(@NotBlank String username, @NotBlank String password) {

    @Override
    public String toString() {
        return "AuthProperties[username=" + username + ", password=***]";
    }
}
