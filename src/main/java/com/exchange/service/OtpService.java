package com.exchange.service;

public interface OtpService {
    String generateAndStore(String email);
    void storeOtp(String email, String otp);
    boolean verify(String email, String otp);
    void invalidate(String email);
}
