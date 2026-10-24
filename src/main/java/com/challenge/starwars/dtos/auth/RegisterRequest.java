package com.challenge.starwars.dtos.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

/**
 * Payload de registro. Las reglas de contraseña siguen NIST SP 800-63B rev. 4 (mínimo 15 caracteres cuando es el único
 * factor, sin reglas de composición); el tope de 72 bytes es el límite de entrada de BCrypt, que los caracteres multibyte pueden superar
 * antes de alcanzar el límite de caracteres.
 *
 * <p>Los controles entre campos se reportan sobre {@code password} mediante el manejador de excepciones, nunca con su valor.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "may only contain letters, digits, '.', '_' and '-'")
        String username,
        @NotBlank @Size(min = 15, max = 64) String password) {

    private static final int BCRYPT_MAX_BYTES = 72;

    @AssertTrue(message = "must be at most 72 bytes in UTF-8")
    boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }

    @AssertTrue(message = "must not be the same as the username")
    boolean isPasswordDifferentFromUsername() {
        return password == null || !password.equalsIgnoreCase(username);
    }

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=***]";
    }
}
