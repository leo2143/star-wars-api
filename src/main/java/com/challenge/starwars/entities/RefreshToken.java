package com.challenge.starwars.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Un refresh token de una sesión. Solo se almacena el SHA-256 del token, nunca el token en sí.
 * {@code usedAt} y {@code revokedAt} los escriben las consultas de actualización del repositorio para que la transición sea atómica.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Getter(AccessLevel.NONE)
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Getter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at", insertable = false, updatable = false)
    private Instant usedAt;

    @Column(name = "revoked_at", insertable = false, updatable = false)
    private Instant revokedAt;

    public RefreshToken(Long userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
