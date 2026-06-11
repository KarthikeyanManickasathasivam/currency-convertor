package com.exchange.service;

public interface TokenBlacklistService {
    void blacklist(String token, long expiryMillis);
    boolean isBlacklisted(String token);
}
