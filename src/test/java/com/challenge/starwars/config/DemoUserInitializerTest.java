package com.challenge.starwars.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenge.starwars.entities.User;
import com.challenge.starwars.repositories.UserRepository;
import com.challenge.starwars.security.AuthProperties;
import com.challenge.starwars.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class DemoUserInitializerTest {

    @Autowired
    private AuthProperties credentials;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private DemoUserInitializer initializer;

    private DemoUserInitializer initializerFor(String username, String password) {
        return new DemoUserInitializer(new AuthProperties(username, password), users, encoder);
    }

    @Test
    void theDemoUserExistsAfterStartupWithAHashedPassword() {
        User demo = users.findByUsername(User.normalize(credentials.username())).orElseThrow();

        assertThat(demo.isEnabled()).isTrue();
        assertThat(demo.getPasswordHash()).isNotEqualTo(credentials.password());
        assertThat(encoder.matches(credentials.password(), demo.getPasswordHash())).isTrue();
    }

    @Test
    void runningItAgainKeepsTheSameRow() throws Exception {
        User before = users.findByUsername(User.normalize(credentials.username())).orElseThrow();

        initializer.run(null);

        User after = users.findByUsername(User.normalize(credentials.username())).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
    }

    @Test
    void createsTheUserWithANormalizedUsernameAndUpdatesAChangedPassword() throws Exception {
        initializerFor("Init-Mixed-Case", "first-password-value").run(null);
        User created = users.findByUsername("init-mixed-case").orElseThrow();
        String firstHash = created.getPasswordHash();

        initializerFor("Init-Mixed-Case", "first-password-value").run(null);
        assertThat(users.findByUsername("init-mixed-case").orElseThrow().getPasswordHash()).isEqualTo(firstHash);

        initializerFor("init-mixed-case", "second-password-value").run(null);
        User updated = users.findByUsername("init-mixed-case").orElseThrow();
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(encoder.matches("second-password-value", updated.getPasswordHash())).isTrue();
        assertThat(encoder.matches("first-password-value", updated.getPasswordHash())).isFalse();
    }
}
