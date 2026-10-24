package com.challenge.starwars.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.challenge.starwars.security.jwt.JwtProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

class SecurityHandlersAndPropertiesTest {

    @Test
    void accessDeniedIsWrittenAsAForbiddenProblemDetail() throws Exception {
        ProblemDetailSecurityHandler handler = new ProblemDetailSecurityHandler(JsonMapper.builder().build());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("internal reason"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":403").doesNotContain("internal reason");
    }

    @Test
    void corsPropertiesRefuseWildcards() {
        assertThatThrownBy(() -> new CorsProperties(List.of("https://ok.example", "*")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorsProperties(List.of("https://*.example.com")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void corsPropertiesTrimAndDropBlankEntries() {
        assertThat(new CorsProperties(List.of(" https://a.example ", " ")).allowedOrigins())
                .containsExactly("https://a.example");
    }

    @Configuration
    @EnableConfigurationProperties(AuthProperties.class)
    static class AuthPropertiesConfig {
    }

    @Test
    void demoCredentialsAreMandatory() {
        new ApplicationContextRunner().withUserConfiguration(AuthPropertiesConfig.class)
                .withPropertyValues("app.auth.username=demo")
                .run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner().withUserConfiguration(AuthPropertiesConfig.class)
                .withPropertyValues("app.auth.username=", "app.auth.password=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void propertiesNeverPrintSecretsInToString() {
        assertThat(new AuthProperties("demo", "super-secret-password").toString()).doesNotContain("super-secret");
        assertThat(new JwtProperties("c2VjcmV0LXZhbHVl", "iss", "aud", 600, java.time.Duration.ofDays(7)).toString())
                .doesNotContain("c2VjcmV0");
        assertThat(new com.challenge.starwars.dtos.auth.TokenResponse("access-jwt", "Bearer", 900, "refresh-opaque", 60)
                .toString()).doesNotContain("access-jwt").doesNotContain("refresh-opaque");
    }
}
