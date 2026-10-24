package com.challenge.starwars.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.challenge.starwars.config.SwapiProperties;
import com.challenge.starwars.exceptions.SwapiCircuitOpenException;
import com.challenge.starwars.exceptions.SwapiInvalidResponseException;
import com.challenge.starwars.exceptions.SwapiRateLimitedException;
import com.challenge.starwars.exceptions.SwapiTimeoutException;
import com.challenge.starwars.exceptions.SwapiUnavailableException;
import com.challenge.starwars.support.Fixtures;
import com.challenge.starwars.support.MutableClock;
import com.challenge.starwars.support.Resources;

import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SwapiClientCircuitBreakerTest {

    private static final String BASE = "https://swapi.test/api";
    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final int MINIMUM_CALLS = 4;

    private final MutableClock clock = new MutableClock();
    private MockRestServiceServer server;

    private SwapiClient clientWith(int maxRetries) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        SwapiProperties properties = new SwapiProperties(BASE, 10, Duration.ofHours(1), new SwapiProperties.Retry(maxRetries, Duration.ofMillis(1)), new SwapiProperties.CircuitBreaker(50, MINIMUM_CALLS, MINIMUM_CALLS, WAIT, 1));
        return new SwapiClient(builder.build(), clock, properties);
    }

    private void failTimes(SwapiClient client, int calls) {
        for (int i = 0; i < calls; i++) {
            assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiUnavailableException.class);
        }
    }

    /**
     * MockRestServiceServer no admite expectativas nuevas tras recibir peticiones: cierra la fase anterior y empieza otra.
     */
    private void startNextPhase() {
        server.verify();
        server.reset();
    }

    private void expectFailures(int count) {
        startNextPhase();
        server.expect(ExpectedCount.times(count), requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private void expectFilms() {
        startNextPhase();
        server.expect(requestTo(BASE + "/films")).andRespond(withSuccess(Fixtures.raw("films-all"), MediaType.APPLICATION_JSON));
    }

    @Test
    void opensAfterTheConfiguredFailuresAndFailsFastWithoutCallingSwapi() {
        SwapiClient client = clientWith(0);
        expectFailures(MINIMUM_CALLS);
        failTimes(client, MINIMUM_CALLS);

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiCircuitOpenException.class);
        assertThatThrownBy(() -> client.fetchPage(Resources.PEOPLE, 1, 10)).isInstanceOf(SwapiCircuitOpenException.class);
        server.verify();
    }

    @Test
    void timeoutsCountAsFailures() {
        SwapiClient client = clientWith(0);
        server.expect(ExpectedCount.times(MINIMUM_CALLS), requestTo(BASE + "/films")).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });
        for (int i = 0; i < MINIMUM_CALLS; i++) {
            assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiTimeoutException.class);
        }

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiCircuitOpenException.class);
        server.verify();
    }

    @Test
    void retriesBeforeTheBreakerRecordsOneFailurePerLogicalCall() {
        SwapiClient client = clientWith(2);
        expectFailures(3 * (MINIMUM_CALLS - 1));
        failTimes(client, MINIMUM_CALLS - 1);
        expectFilms();

        assertThat(client.fetchAll(Resources.FILMS)).hasSize(6);
        server.verify();
    }

    @Test
    void clientErrorsNeverOpenTheBreaker() {
        SwapiClient client = clientWith(0);
        server.expect(ExpectedCount.times(MINIMUM_CALLS * 2), requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        for (int i = 0; i < MINIMUM_CALLS * 2; i++) {
            assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiUnavailableException.class);
        }
        expectFilms();

        assertThat(client.fetchAll(Resources.FILMS)).hasSize(6);
        server.verify();
    }

    @Test
    void rateLimitingNeverOpensTheBreaker() {
        SwapiClient client = clientWith(0);
        server.expect(ExpectedCount.times(MINIMUM_CALLS * 2), requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        for (int i = 0; i < MINIMUM_CALLS * 2; i++) {
            assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiRateLimitedException.class);
        }
        expectFilms();

        assertThat(client.fetchAll(Resources.FILMS)).hasSize(6);
        server.verify();
    }

    @Test
    void ignoredCallsDoNotDiluteTheFailureRate() {
        SwapiClient client = clientWith(0);
        server.expect(ExpectedCount.times(MINIMUM_CALLS), requestTo(BASE + "/films")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        for (int i = 0; i < MINIMUM_CALLS; i++) {
            assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiRateLimitedException.class);
        }
        // Sin llamadas registradas todavía: hacen falta MINIMUM_CALLS fallos reales para abrir.
        expectFailures(MINIMUM_CALLS);
        failTimes(client, MINIMUM_CALLS);

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiCircuitOpenException.class);
        server.verify();
    }

    @Test
    void staysOpenUntilTheWaitElapsesThenAProbeSuccessClosesIt() {
        SwapiClient client = clientWith(0);
        expectFailures(MINIMUM_CALLS);
        failTimes(client, MINIMUM_CALLS);
        clock.advance(WAIT.minusSeconds(1));
        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiCircuitOpenException.class);

        clock.advance(Duration.ofSeconds(2));
        expectFilms();
        assertThat(client.fetchAll(Resources.FILMS)).hasSize(6);

        // Cerrado de nuevo: un único fallo ya no abre el breaker.
        expectFailures(1);
        failTimes(client, 1);
        expectFilms();
        assertThat(client.fetchAll(Resources.FILMS)).hasSize(6);
        server.verify();
    }

    @Test
    void malformedSuccessfulResponsesCountAsFailuresAndAreNotRetried() {
        SwapiClient client = clientWith(2);
        server.expect(ExpectedCount.times(MINIMUM_CALLS), requestTo(BASE + "/films")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        for (int i = 0; i < MINIMUM_CALLS; i++) {
            assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiInvalidResponseException.class);
        }

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiCircuitOpenException.class);
        server.verify();
    }

    @Test
    void malformedListingsCountAsFailures() {
        SwapiClient client = clientWith(0);
        server.expect(ExpectedCount.times(MINIMUM_CALLS), requestTo(BASE + "/people?page=1&limit=10&expanded=true")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        for (int i = 0; i < MINIMUM_CALLS; i++) {
            assertThatThrownBy(() -> client.fetchPage(Resources.PEOPLE, 1, 10)).isInstanceOf(SwapiInvalidResponseException.class);
        }

        assertThatThrownBy(() -> client.fetchPage(Resources.PEOPLE, 1, 10)).isInstanceOf(SwapiCircuitOpenException.class);
        server.verify();
    }

    @Test
    void theOpenBreakerCarriesTheConfiguredWaitAsRetryAfter() {
        SwapiClient client = clientWith(0);
        expectFailures(MINIMUM_CALLS);
        failTimes(client, MINIMUM_CALLS);

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOfSatisfying(SwapiCircuitOpenException.class, e -> assertThat(e.retryAfter()).isEqualTo(WAIT));
    }

    @Test
    void aFailedProbeReopensTheBreaker() {
        SwapiClient client = clientWith(0);
        expectFailures(MINIMUM_CALLS + 1);
        failTimes(client, MINIMUM_CALLS);
        clock.advance(WAIT.plusSeconds(1));

        failTimes(client, 1);

        assertThatThrownBy(() -> client.fetchAll(Resources.FILMS)).isInstanceOf(SwapiCircuitOpenException.class);
        server.verify();
    }
}
