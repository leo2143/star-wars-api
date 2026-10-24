package com.challenge.starwars.security.jwt;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Construye la clave de firma HS256, fallando rápido con un mensaje que nunca contiene el secreto.
 */
final class JwtSecretKeys {

    private static final int MIN_KEY_BYTES = 32;
    private static final String INVALID = "JWT secret is missing or invalid: set JWT_SECRET_BASE64 to a Base64 value " + "that decodes to at least " + MIN_KEY_BYTES + " bytes (256 bits)";

    private JwtSecretKeys() {
    }

    static SecretKey fromBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(INVALID);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            // El mensaje del decodificador cita un carácter ofensor del secreto, por eso no se encadena.
            throw new IllegalStateException(INVALID);
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(INVALID);
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
