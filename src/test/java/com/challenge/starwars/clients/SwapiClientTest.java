package com.challenge.starwars.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.challenge.starwars.config.SwapiProperties;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.swapi.SwapiPage;
import com.challenge.starwars.exceptions.SwapiInvalidResponseException;
import com.challenge.starwars.exceptions.SwapiRateLimitedException;
import com.challenge.starwars.exceptions.SwapiTimeoutException;
import com.challenge.starwars.exceptions.SwapiUnavailableException;
import com.challenge.starwars.support.Fixtures;
import com.challenge.starwars.support.Resources;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SwapiClientTest {

    private static final String BASE = "https://swapi.test/api";
    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    private MockRestServiceServer server;
    private SwapiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        SwapiProperties properties = new SwapiProperties(BASE, 10, Duration.ofHours(1), new SwapiProperties.Retry(2, Duration.ofMillis(1)), new SwapiProperties.CircuitBreaker(50, 100, 100, Duration.ofSeconds(30), 1));
        client = new SwapiClient(builder.build(), Clock.fixed(NOW, ZoneOffset.UTC), properties);
    }

    private static String json(String fixture) {
        return Fixtures.raw(fixture);
    }

    @Test
    void listRequestsExpandedPageAndNormalizesTotals() {
        server.expect(requestTo(BASE + "/people?page=2&limit=10&expanded=true")).andRespond(withSuccess(json("people-list-expanded"), MediaType.APPLICATION_JSON));

        SwapiPage page = client.fetchPage(Resources.PEOPLE, 2, 10);

        assertThat(page.totalPages()).isEqualTo(9);
        assertThat(page.items()).hasSize(10);
        assertThat(page.items().getFirst().uid()).isEqualTo("1");
        assertThat(page.items().getFirst().text("name")).isEqualTo("Luke Skywalker");
        server.verify();
    }

    @Test
    void filmsAreFetchedInOneUnpaginatedCall() {
        server.expect(requestTo(BASE + "/films")).andRespond(withSuccess(json("films-all"), MediaType.APPLICATION_JSON));

        assertThat(client.fetchAll(Resources.FILMS)).hasSize(6);
        server.verify();
    }

    @Test
    void unexpected404OnACollectionIsAnUpstreamFailure() {
        server.expect(requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiUnavailableException.class);
    }

    @Test
    void rateLimitUsesRetryAfterHeader() {
        server.expect(requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "30"));

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOfSatisfying(SwapiRateLimitedException.class, e -> assertThat(e.retryAfter()).contains(Duration.ofSeconds(30)));
    }

    @Test
    void rateLimitFallsBackToTheResetEpochHeader() {
        long reset = NOW.plusSeconds(45).getEpochSecond();
        server.expect(requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("x-ratelimit-reset", String.valueOf(reset)));

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOfSatisfying(SwapiRateLimitedException.class, e -> assertThat(e.retryAfter()).contains(Duration.ofSeconds(45)));
    }

    @Test
    void rateLimitWithoutHintsHasNoRetryAfter() {
        server.expect(requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOfSatisfying(SwapiRateLimitedException.class, e -> assertThat(e.retryAfter()).isEmpty());
    }

    @Test
    void serverErrorsAreUnavailable() {
        server.expect(ExpectedCount.times(3), requestTo(BASE + "/people?page=1&limit=10&expanded=true")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom secret detail"));

        assertThatThrownBy(() -> client.fetchPage(Resources.PEOPLE, 1, 10)).isInstanceOf(SwapiUnavailableException.class).hasMessageNotContaining("secret detail");
    }

    @Test
    void readTimeoutIsATimeout() {
        server.expect(requestTo(BASE + "/films")).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiTimeoutException.class);
    }

    @Test
    void connectionFailureIsUnavailable() {
        server.expect(ExpectedCount.times(3), requestTo(BASE + "/films")).andRespond(request -> {
            throw new ConnectException("Connection refused");
        });

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiUnavailableException.class);
    }

    @Test
    void genericIoFailureIsUnavailable() {
        server.expect(ExpectedCount.times(3), requestTo(BASE + "/films")).andRespond(request -> {
            throw new IOException("reset");
        });

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiUnavailableException.class);
    }

    @Test
    void malformedJsonIsAnInvalidResponse() {
        server.expect(requestTo(BASE + "/people?page=1&limit=10&expanded=true")).andRespond(withSuccess("<html>not json</html>", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchPage(Resources.PEOPLE, 1, 10)).isInstanceOf(SwapiInvalidResponseException.class);
    }

    @Test
    void emptyBodyIsAnInvalidResponse() {
        server.expect(requestTo(BASE + "/films")).andRespond(withSuccess());

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiInvalidResponseException.class);
    }

    @Test
    void collectionWithoutResultArrayIsAnInvalidResponse() {
        server.expect(requestTo(BASE + "/films")).andRespond(withSuccess("{\"message\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiInvalidResponseException.class);
    }

    @Test
    void serverErrorIsRetriedUntilItSucceeds() {
        server.expect(requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BASE + "/films")).andRespond(withSuccess(json("films-all"), MediaType.APPLICATION_JSON));

        assertThat(client.fetchAll(Resources.FILMS)).hasSize(6);
        server.verify();
    }

    @Test
    void connectionFailureIsRetriedUntilItSucceeds() {
        server.expect(requestTo(BASE + "/films")).andRespond(request -> {
            throw new ConnectException("Connection refused");
        });
        server.expect(requestTo(BASE + "/films")).andRespond(withSuccess(json("films-all"), MediaType.APPLICATION_JSON));

        assertThat(client.fetchAll(Resources.FILMS)).hasSize(6);
        server.verify();
    }

    @Test
    void givesUpAfterTheMaximumNumberOfRetries() {
        server.expect(ExpectedCount.times(3), requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiUnavailableException.class);
        server.verify();
    }

    @Test
    void rateLimitIsNeverRetried() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiRateLimitedException.class);
        server.verify();
    }

    @Test
    void clientErrorsAreNeverRetried() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiUnavailableException.class);
        server.verify();
    }

    @Test
    void malformedBodyIsNeverRetried() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/films")).andRespond(withSuccess("<html>not json</html>", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiInvalidResponseException.class);
        server.verify();
    }

    @Test
    void readTimeoutIsNeverRetried() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/films")).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiTimeoutException.class);
        server.verify();
    }

    @Test
    void listWithoutTotalPagesIsAnInvalidResponse() {
        server.expect(requestTo(BASE + "/people?page=1&limit=10&expanded=true")).andRespond(withSuccess("{\"message\":\"ok\",\"total_records\":0,\"results\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchPage(Resources.PEOPLE, 1, 10)).isInstanceOf(SwapiInvalidResponseException.class);
    }
}
