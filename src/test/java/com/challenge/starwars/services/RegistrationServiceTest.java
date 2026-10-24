package com.challenge.starwars.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.challenge.starwars.dtos.auth.RegisterRequest;
import com.challenge.starwars.entities.User;
import com.challenge.starwars.exceptions.UsernameTakenException;
import com.challenge.starwars.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

@SuppressWarnings("deprecation")
class RegistrationServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RegistrationService service = new RegistrationService(users, NoOpPasswordEncoder.getInstance());

    @Test
    void storesTheNormalizedUsernameAndTheEncodedPassword() {
        when(users.existsByUsername("luke")).thenReturn(false);
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        service.register(new RegisterRequest("Luke", "a-long-enough-password"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("luke");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("a-long-enough-password");
    }

    @Test
    void anExistingUsernameIsRejectedWithoutSaving() {
        when(users.existsByUsername("luke")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Luke", "a-long-enough-password")))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void theUniqueConstraintDecidesWhenTwoRegistrationsRace() {
        when(users.existsByUsername("luke")).thenReturn(false);
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("users_username_key"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("luke", "a-long-enough-password")))
                .isInstanceOf(UsernameTakenException.class);
    }
}
