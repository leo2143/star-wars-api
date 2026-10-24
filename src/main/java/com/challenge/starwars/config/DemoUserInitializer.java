package com.challenge.starwars.config;

import com.challenge.starwars.entities.User;
import com.challenge.starwars.repositories.UserRepository;
import com.challenge.starwars.security.AuthProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Mantiene la cuenta demo sincronizada con {@code app.auth.*} al arrancar: la crea si falta y renueva su
 * hash cuando la contraseña configurada ya no coincide. Es idempotente y ninguna credencial vive jamás en una migración.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class DemoUserInitializer implements ApplicationRunner {

    private final AuthProperties authProperties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        String username = User.normalize(authProperties.username());
        User demo = users.findByUsername(username).orElse(null);
        if (demo == null) {
            users.save(new User(username, passwordEncoder.encode(authProperties.password())));
        } else if (!passwordEncoder.matches(authProperties.password(), demo.getPasswordHash())) {
            demo.setPasswordHash(passwordEncoder.encode(authProperties.password()));
            users.save(demo);
        }
    }
}
