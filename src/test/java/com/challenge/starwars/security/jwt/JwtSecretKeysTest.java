package com.challenge.starwars.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.challenge.starwars.support.TestTokens;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtSecretKeysTest {

    @Test
    void acceptsExactly32DecodedBytes() {
        SecretKey key = JwtSecretKeys.fromBase64(TestTokens.randomSecretBase64(32));

        assertThat(key.getEncoded()).hasSize(32);
        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void rejects31DecodedBytes() {
        String shortSecret = TestTokens.randomSecretBase64(31);

        assertThatThrownBy(() -> JwtSecretKeys.fromBase64(shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET_BASE64")
                .hasMessageNotContaining(shortSecret);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingSecret(String value) {
        assertThatThrownBy(() -> JwtSecretKeys.fromBase64(value))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET_BASE64");
    }

    @Test
    void rejectsInvalidBase64WithoutEchoingTheValueNorKeepingTheCause() {
        String notBase64 = "this is *not* base64 !!!";

        assertThatThrownBy(() -> JwtSecretKeys.fromBase64(notBase64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(notBase64)
                .hasNoCause();
    }
}
