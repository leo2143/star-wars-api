package com.challenge.starwars.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.io.IOException;
import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class SwapiRequestLoggingInterceptorTest {

    private final SwapiRequestLoggingInterceptor interceptor = new SwapiRequestLoggingInterceptor();
    private Logger logger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(SwapiRequestLoggingInterceptor.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    private static MockClientHttpRequest request(String uri) {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create(uri));
        request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer super-secret-token");
        return request;
    }

    @Test
    void logsMethodUriStatusAndDurationAtDebugAndPassesTheResponseThrough() throws IOException {
        ClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        ClientHttpRequestExecution execution = (req, body) -> response;

        ClientHttpResponse result = interceptor.intercept(request("https://swapi.test/api/people?page=2&limit=10&expanded=true"), new byte[0], execution);

        assertThat(result).isSameAs(response);
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(event.getFormattedMessage()).contains("GET", "https://swapi.test/api/people?page=2&limit=10&expanded=true", "200", "ms");
    }

    @Test
    void neverLogsHeadersOrCredentials() throws IOException {
        ClientHttpRequestExecution execution = (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);

        interceptor.intercept(request("https://user:pass@swapi.test/api/films"), new byte[0], execution);

        String message = appender.list.getFirst().getFormattedMessage();
        assertThat(message).doesNotContain("super-secret-token", "Authorization", "user", "pass@");
        assertThat(message).contains("https://swapi.test/api/films");
    }

    @Test
    void logsOneLineForEveryAttemptSoRetriesAreVisible() throws IOException {
        ClientHttpRequestExecution execution = (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.BAD_GATEWAY);

        interceptor.intercept(request("https://swapi.test/api/films"), new byte[0], execution);
        interceptor.intercept(request("https://swapi.test/api/films"), new byte[0], execution);

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).contains("502"));
    }

    @Test
    void logsTransportFailuresAndRethrowsThem() {
        ClientHttpRequestExecution execution = (req, body) -> {
            throw new IOException("connection refused");
        };

        assertThatThrownBy(() -> interceptor.intercept(request("https://swapi.test/api/films"), new byte[0], execution)).isInstanceOf(IOException.class).hasMessage("connection refused");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(event.getFormattedMessage()).contains("GET", "https://swapi.test/api/films", "IOException");
    }

    @Test
    void staysSilentWhenDebugIsDisabled() throws IOException {
        logger.setLevel(Level.INFO);
        ClientHttpRequestExecution execution = (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);

        interceptor.intercept(request("https://swapi.test/api/films"), new byte[0], execution);

        assertThat(appender.list).isEmpty();
    }
}
