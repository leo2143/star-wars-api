package com.challenge.starwars.services;

import com.challenge.starwars.entities.RefreshToken;
import com.challenge.starwars.entities.User;
import com.challenge.starwars.exceptions.InvalidRefreshTokenException;
import com.challenge.starwars.repositories.RefreshTokenRepository;
import com.challenge.starwars.repositories.UserRepository;
import com.challenge.starwars.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens opacos con rotación. Cada login inicia una familia; cada refresh consume un token y emite el
 * siguiente en la misma familia, con la misma expiración. Presentar un token que ya fue consumido significa que pudo haber
 * sido robado, por lo que se revoca toda la familia.
 */
@Service
@RequiredArgsConstructor
class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository users;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private static String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /**
     * Inicia una nueva sesión para el usuario.
     */
    public IssuedRefreshToken startSession(User user) {
        Instant now = clock.instant();
        return issue(user, UUID.randomUUID(), now.plus(jwtProperties.refreshTtl()), now);
    }

    /**
     * Consume el token y emite el siguiente. Un rechazo no debe hacer rollback: revocar la familia ante una reutilización tiene que
     * sobrevivir al 401.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedRefreshToken rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken)).orElseThrow(InvalidRefreshTokenException::new);
        if (current.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException();
        }
        if (current.getUsedAt() != null) {
            throw reuseDetected(current, now);
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        User user = users.findById(current.getUserId()).filter(User::isEnabled)
                .orElseThrow(InvalidRefreshTokenException::new);
        // Una petición concurrente pudo haber consumido el token después de la lectura anterior: la actualización entonces no toca ninguna fila.
        if (refreshTokenRepository.markUsed(current.getId(), now) == 0) {
            throw reuseDetected(current, now);
        }
        return issue(user, current.getFamilyId(), current.getExpiresAt(), now);
    }

    /**
     * Revoca toda la familia del token. Los tokens desconocidos se ignoran, para que quien llama no aprenda nada.
     */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private InvalidRefreshTokenException reuseDetected(RefreshToken token, Instant now) {
        refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
        return new InvalidRefreshTokenException();
    }

    private IssuedRefreshToken issue(User user, UUID familyId, Instant expiresAt, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(new RefreshToken(user.getId(), familyId, hash(raw), now, expiresAt));
        return new IssuedRefreshToken(user.getUsername(), raw, expiresAt);
    }

    /**
     * Un token recién emitido; {@code token} es el único momento en que el valor sin procesar existe en el servidor.
     */
    public record IssuedRefreshToken(String username, String token, Instant expiresAt) {
    }
}
