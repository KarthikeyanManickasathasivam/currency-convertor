package com.exchange.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalApiExceptionTest {

    @Test
    void messageConstructor_setsMessage() {
        ExternalApiException ex = new ExternalApiException("API unavailable");
        assertThat(ex.getMessage()).isEqualTo("API unavailable");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageCauseConstructor_setsMessageAndCause() {
        Throwable cause = new RuntimeException("timeout");
        ExternalApiException ex = new ExternalApiException("API unavailable", cause);
        assertThat(ex.getMessage()).isEqualTo("API unavailable");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
