package com.challenge.starwars.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SwapiCircuitBreakerPropertiesTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidSettings() {
        assertThatCode(() -> new SwapiProperties.CircuitBreaker(50, 10, 5, WAIT, 2)).doesNotThrowAnyException();
        assertThatCode(() -> new SwapiProperties.CircuitBreaker(50, 5, 5, Duration.ofMillis(1), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMoreMinimumCallsThanTheWindow() {
        assertThatThrownBy(() -> new SwapiProperties.CircuitBreaker(50, 4, 5, WAIT, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum-number-of-calls");
    }

    @Test
    void rejectsZeroNegativeAndSubMillisecondWaits() {
        for (Duration wait : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofNanos(500_000)}) {
            assertThatThrownBy(() -> new SwapiProperties.CircuitBreaker(50, 10, 5, wait, 2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("wait-duration-in-open-state");
        }
    }

    @Test
    void aNullWaitIsLeftToBeanValidation() {
        SwapiProperties.CircuitBreaker settings = new SwapiProperties.CircuitBreaker(50, 10, 5, null, 2);

        assertThat(validator.validate(settings))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("waitDurationInOpenState");
    }

    @Test
    void aBasicRangeErrorWinsOverTheMinimumCallsComparison() {
        SwapiProperties.CircuitBreaker settings = new SwapiProperties.CircuitBreaker(50, 0, 1, WAIT, 2);

        assertThat(validator.validate(settings))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("slidingWindowSize");
    }

    @Test
    void mainApplicationPropertiesBindTheDocumentedDefaults() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    SwapiProperties.CircuitBreaker settings = Binder.get(context.getEnvironment())
                            .bind("app.swapi.circuit-breaker", SwapiProperties.CircuitBreaker.class)
                            .get();

                    assertThat(settings).isEqualTo(new SwapiProperties.CircuitBreaker(50, 10, 5, WAIT, 2));
                });
    }
}
