package com.challenge.starwars.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Locale;

/**
 * Una cuenta que puede iniciar sesión. Los nombres de usuario se guardan en su forma canónica, ver {@link #normalize(String)}.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Setter
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Setter
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private final Instant createdAt = Instant.now();

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    /**
     * Nombre de usuario canónico: recortado y en minúsculas, de modo que la unicidad y el login ignoran mayúsculas.
     */
    public static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
