package com.exchange.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class ConsoleEmailServiceTest {

    private ConsoleEmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new ConsoleEmailService();
    }

    @Test
    void sendOtp_doesNotThrow() {
        assertThatCode(() -> emailService.sendOtp("user@example.com", "123456"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendApprovalRequiredNotification_doesNotThrow() {
        assertThatCode(() -> emailService.sendApprovalRequiredNotification(
                "admin@example.com",
                UUID.randomUUID(),
                "user@example.com",
                new BigDecimal("500.00"),
                "USD", "EUR"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendApprovalNotification_doesNotThrow() {
        assertThatCode(() -> emailService.sendApprovalNotification(
                "user@example.com",
                UUID.randomUUID(),
                new BigDecimal("460.00"),
                "EUR"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendRejectionNotification_doesNotThrow() {
        assertThatCode(() -> emailService.sendRejectionNotification(
                "user@example.com",
                UUID.randomUUID(),
                "Suspicious activity"))
                .doesNotThrowAnyException();
    }
}
