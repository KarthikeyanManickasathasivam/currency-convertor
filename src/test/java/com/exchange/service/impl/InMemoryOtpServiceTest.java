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

    @Test
    void storeOtp_thenVerify_succeeds() {
        otpService.storeOtp("user@example.com", "999999");
        assertThat(otpService.verify("user@example.com", "999999")).isTrue();
    }

    @Test
    void verify_expiredOtp_returnsFalse() throws Exception {
        // Store an OTP with a manually planted expired entry via storeOtp then
        // manipulate via reflection so the entry is already past its TTL.
        otpService.generateAndStore("expired@example.com");

        // Access internal store and overwrite with an already-expired entry
        var storeField = InMemoryOtpService.class.getDeclaredField("store");
        storeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var store = (java.util.Map<String, Object>) storeField.get(otpService);

        // Construct an OtpEntry record with an expiry in the past
        var entryClass = java.util.Arrays.stream(InMemoryOtpService.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("OtpEntry"))
                .findFirst().orElseThrow();
        var ctor = entryClass.getDeclaredConstructor(String.class, java.time.Instant.class);
        ctor.setAccessible(true);
        Object expiredEntry = ctor.newInstance("123456", java.time.Instant.now().minusSeconds(10));
        store.put("expired@example.com", expiredEntry);

        assertThat(otpService.verify("expired@example.com", "123456")).isFalse();
    }

    @Test
    void evictExpired_removesExpiredEntries() throws Exception {
        otpService.generateAndStore("keep@example.com");
        otpService.generateAndStore("expire@example.com");

        // Plant an expired entry for the second email
        var storeField = InMemoryOtpService.class.getDeclaredField("store");
        storeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var store = (java.util.Map<String, Object>) storeField.get(otpService);

        var entryClass = java.util.Arrays.stream(InMemoryOtpService.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("OtpEntry"))
                .findFirst().orElseThrow();
        var ctor = entryClass.getDeclaredConstructor(String.class, java.time.Instant.class);
        ctor.setAccessible(true);
        Object expiredEntry = ctor.newInstance("000000", java.time.Instant.now().minusSeconds(10));
        store.put("expire@example.com", expiredEntry);

        otpService.evictExpired();

        assertThat(store).containsKey("keep@example.com");
        assertThat(store).doesNotContainKey("expire@example.com");
    }
}
