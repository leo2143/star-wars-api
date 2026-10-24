package com.challenge.starwars.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenge.starwars.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtStartupTest {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, JwtConfig.class)
            .withPropertyValues("app.jwt.issuer=https://issuer.test", "app.jwt.audience=aud", "app.jwt.ttl-seconds=600");

    private static String messageChain(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }

    @Test
    void startsWithAStrongSecret() {
        runner.withPropertyValues("app.jwt.secret-base64=" + TestTokens.randomSecretBase64(32))
                .run(context -> assertThat(context).hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class));
    }

    @Test
    void failsFastWithoutASecret() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(messageChain(context.getStartupFailure())).contains("JWT_SECRET_BASE64");
        });
    }

    @Test
    void failsFastWithAWeakSecretAndNeverPrintsIt() {
        String weak = TestTokens.randomSecretBase64(16);

        runner.withPropertyValues("app.jwt.secret-base64=" + weak).run(context -> {
            assertThat(context).hasFailed();
            assertThat(messageChain(context.getStartupFailure())).contains("JWT_SECRET_BASE64").doesNotContain(weak);
        });
    }

    @Test
    void refreshTtlDefaultsToSevenDaysAndMustBePositive() {
        String secret = "app.jwt.secret-base64=" + TestTokens.randomSecretBase64(32);

        runner.withPropertyValues(secret).run(context -> assertThat(context.getBean(JwtProperties.class).refreshTtl())
                .isEqualTo(java.time.Duration.ofDays(7)));
        runner.withPropertyValues(secret, "app.jwt.refresh-ttl=0s").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWithoutIssuer() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class, JwtConfig.class)
                .withPropertyValues("app.jwt.secret-base64=" + TestTokens.randomSecretBase64(32),
                        "app.jwt.audience=aud", "app.jwt.ttl-seconds=600")
                .run(context -> assertThat(context).hasFailed());
    }
}
