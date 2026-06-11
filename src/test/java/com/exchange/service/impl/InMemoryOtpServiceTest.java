package com.exchange.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class InMemoryOtpServiceTest {

    private InMemoryOtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new InMemoryOtpService();
    }

    @Test
    void generateAndStore_returns6DigitOtp() {
        String otp = otpService.generateAndStore("user@example.com");
        assertThat(otp).matches("\\d{6}");
    }

    @Test
    void verify_correctOtp_returnsTrue() {
        String otp = otpService.generateAndStore("user@example.com");
        assertThat(otpService.verify("user@example.com", otp)).isTrue();
    }

    @Test
    void verify_wrongOtp_returnsFalse() {
        otpService.generateAndStore("user@example.com");
        assertThat(otpService.verify("user@example.com", "000000")).isFalse();
    }

    @Test
    void verify_consumesOtp_cannotVerifyTwice() {
        String otp = otpService.generateAndStore("user@example.com");
        otpService.verify("user@example.com", otp);
        // Second attempt must fail — OTP was consumed
        assertThat(otpService.verify("user@example.com", otp)).isFalse();
    }

    @Test
    void verify_emailCaseInsensitive() {
        String otp = otpService.generateAndStore("USER@Example.COM");
        assertThat(otpService.verify("user@example.com", otp)).isTrue();
    }

    @Test
    void invalidate_removesOtp() {
        String otp = otpService.generateAndStore("user@example.com");
        otpService.invalidate("user@example.com");
        assertThat(otpService.verify("user@example.com", otp)).isFalse();
    }
}
