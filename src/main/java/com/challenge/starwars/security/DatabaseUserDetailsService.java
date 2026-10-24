package com.challenge.starwars.security;

import com.challenge.starwars.repositories.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carga las cuentas desde la base de datos; una cuenta deshabilitada es rechazada por las verificaciones previas a la autenticación de Spring Security.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(user -> org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles("USER")
                        .disabled(!user.isEnabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
