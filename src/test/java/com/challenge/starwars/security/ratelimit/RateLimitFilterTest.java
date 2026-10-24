package com.challenge.starwars.security.ratelimit;

import static com.challenge.starwars.support.TestPaths.V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {

    private static final RateLimitProperties.Limit TWO_PER_HOUR = new RateLimitProperties.Limit(2, Duration.ofHours(1));

    private final RateLimitFilter filter = new RateLimitFilter(
            new RateLimitProperties(TWO_PER_HOUR, TWO_PER_HOUR, TWO_PER_HOUR), JsonMapper.builder().build());

    private MockHttpServletResponse call(String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @ParameterizedTest
    @ValueSource(strings = {V1 + "/auth/login", V1 + "/auth/refresh", V1 + "/auth/register"})
    void postToAnAuthEndpointIsLimitedWithAProblemDetailAndRetryAfter(String path) throws Exception {
        assertThat(call("POST", path, "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", path, "10.0.0.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = call("POST", path, "10.0.0.1");

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getContentType()).startsWith("application/problem+json");
        assertThat(limited.getContentAsString())
                .contains("\"status\":429", "\"title\":\"Too Many Requests\"", "Too many requests. Try again later.");
        // Recarga gradual de 2 tokens por hora: el siguiente token llega en unos 30 minutos.
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isBetween(1700L, 1800L);
    }

    @Test
    void eachEndpointAndEachIpHasItsOwnBucket() throws Exception {
        call("POST", V1 + "/auth/login", "10.0.0.2");
        call("POST", V1 + "/auth/login", "10.0.0.2");

        assertThat(call("POST", V1 + "/auth/login", "10.0.0.2").getStatus()).isEqualTo(429);
        assertThat(call("POST", V1 + "/auth/login", "10.0.0.3").getStatus()).isEqualTo(200);
        assertThat(call("POST", V1 + "/auth/register", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void otherMethodsAndPathsAreNeverLimitedNorCounted() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("GET", V1 + "/auth/login", "10.0.0.4").getStatus()).isEqualTo(200);
            assertThat(call("GET", V1 + "/people", "10.0.0.4").getStatus()).isEqualTo(200);
            assertThat(call("POST", V1 + "/auth/login/extra", "10.0.0.4").getStatus()).isEqualTo(200);
        }
        assertThat(call("POST", V1 + "/auth/login", "10.0.0.4").getStatus()).isEqualTo(200);
    }

    @Test
    void retryAfterIsAtLeastOneSecond() throws Exception {
        RateLimitProperties.Limit fast = new RateLimitProperties.Limit(1, Duration.ofMillis(100));
        RateLimitFilter fastFilter = new RateLimitFilter(new RateLimitProperties(fast, fast, fast),
                JsonMapper.builder().build());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", V1 + "/auth/login");
        fastFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletResponse limited = new MockHttpServletResponse();

        fastFilter.doFilter(request, limited, new MockFilterChain());

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void limitsMustHaveAPositiveCapacityAndPeriod() {
        assertThatThrownBy(() -> new RateLimitProperties.Limit(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties.Limit(5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties.Limit(5, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties.Limit(5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
