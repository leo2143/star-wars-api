package com.challenge.starwars.exceptions.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class ProblemDetailsTest {

    @Test
    void writesAProblemJsonBodyWithTheStatusAndDetail() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemDetails.write(response, JsonMapper.builder().build(), HttpStatus.TOO_MANY_REQUESTS, "Slow down.");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(response.getContentAsString()).contains("\"type\":\"about:blank\"",
                "\"title\":\"Too Many Requests\"", "\"status\":429", "\"detail\":\"Slow down.\"");
    }
}
