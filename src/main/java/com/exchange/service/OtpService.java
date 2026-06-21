package com.exchange.service;

/**
 * OTP lifecycle contract. Profile-specific implementations:
 * {@code InMemoryOtpService} ({@code local}) and {@code RedisOtpService} ({@code aws}).
 */
public interface OtpService {
    String generateAndStore(String email);
    void storeOtp(String email, String otp);
    boolean verify(String email, String otp);
    void invalidate(String email);
}
