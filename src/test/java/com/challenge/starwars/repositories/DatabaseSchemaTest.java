package com.challenge.starwars.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.challenge.starwars.entities.User;
import com.challenge.starwars.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Flyway migró el contenedor y el {@code validate} de Hibernate aceptó el resultado (el contexto arrancó). */
@IntegrationTest
class DatabaseSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository users;

    @Test
    void flywayAppliedTheFirstMigration() {
        List<String> applied = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);

        assertThat(applied).containsExactly("1");
    }

    @Test
    void refreshTokensTableAndFamilyIndexExist() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'refresh_tokens'", String.class);
        Integer familyIndexes = jdbc.queryForObject(
                "select count(*) from pg_indexes where tablename = 'refresh_tokens' and indexdef like '%(family_id)%'",
                Integer.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "user_id", "family_id", "token_hash", "created_at",
                "expires_at", "used_at", "revoked_at");
        assertThat(familyIndexes).isEqualTo(1);
    }

    @Test
    void theUniqueConstraintRejectsADuplicateUsername() {
        users.saveAndFlush(new User("schema-duplicate", "hash"));

        assertThatThrownBy(() -> users.saveAndFlush(new User("schema-duplicate", "other-hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsAUserByItsExactUsername() {
        users.saveAndFlush(new User("schema-lookup", "hash"));

        assertThat(users.findByUsername("schema-lookup")).get()
                .satisfies(user -> {
                    assertThat(user.getId()).isNotNull();
                    assertThat(user.isEnabled()).isTrue();
                    assertThat(user.getCreatedAt()).isNotNull();
                });
        assertThat(users.findByUsername("schema-missing")).isEmpty();
    }
}
