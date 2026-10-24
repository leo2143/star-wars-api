package com.challenge.starwars.services;

import com.challenge.starwars.dtos.auth.RegisterRequest;
import com.challenge.starwars.dtos.auth.RegisterResponse;
import com.challenge.starwars.entities.User;
import com.challenge.starwars.exceptions.UsernameTakenException;
import com.challenge.starwars.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Crea cuentas. La restricción de unicidad de la base de datos es la fuente de verdad; la comprobación previa solo evita calcular el hash.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegisterResponse register(RegisterRequest request) {
        String username = User.normalize(request.username());
        if (users.existsByUsername(username)) {
            throw new UsernameTakenException();
        }
        try {
            User saved = users.saveAndFlush(new User(username, passwordEncoder.encode(request.password())));
            return new RegisterResponse(saved.getId(), saved.getUsername());
        } catch (DataIntegrityViolationException concurrentRegistration) {
            throw new UsernameTakenException();
        }
    }
}
