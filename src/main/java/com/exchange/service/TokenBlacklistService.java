package com.exchange.service;

/**
 * JWT revocation contract. Profile-specific implementations:
 * {@code InMemoryTokenBlacklistService} ({@code local}) and {@code RedisTokenBlacklistService} ({@code aws}).
 */
public interface TokenBlacklistService {
    void blacklist(String token, long expiryMillis);
    boolean isBlacklisted(String token);
}
