package com.challenge.starwars.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Construye JWT arbitrarios (incluidos deliberadamente incorrectos) para sondear el resource server. */
public final class TestTokens {

    private TestTokens() {
    }

    public static String signed(String secretBase64, MacAlgorithm algorithm, String issuer, String audience,
            Instant issuedAt, Instant expiresAt) {
        byte[] secret = Base64.getDecoder().decode(secretBase64);
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("someone")
                .audience(audience == null ? List.of() : List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims)).getTokenValue();
    }

    /** Un token estructuralmente válido con {@code alg=none} y sin firma. */
    public static String unsigned() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
                ("{\"sub\":\"someone\",\"exp\":" + Instant.now().plusSeconds(600).getEpochSecond() + "}")
                        .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }

    /** Un secreto Base64 aleatorio con la longitud decodificada indicada. */
    public static String randomSecretBase64(int bytes) {
        byte[] secret = new byte[bytes];
        new SecureRandom().nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }
}
