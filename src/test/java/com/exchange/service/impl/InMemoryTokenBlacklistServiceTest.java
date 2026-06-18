package com.exchange.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class InMemoryTokenBlacklistServiceTest {

    private InMemoryTokenBlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        blacklistService = new InMemoryTokenBlacklistService();
    }

    @Test
    void isBlacklisted_unknownToken_returnsFalse() {
        assertThat(blacklistService.isBlacklisted("unknown-token")).isFalse();
    }

    @Test
    void blacklist_thenIsBlacklisted_returnsTrue() {
        blacklistService.blacklist("my-token", 60_000L);
        assertThat(blacklistService.isBlacklisted("my-token")).isTrue();
    }

    @Test
    void isBlacklisted_expiredEntry_returnsFalse() {
        // Use a negative TTL so the entry is already expired
        blacklistService.blacklist("expired-token", -1000L);
        assertThat(blacklistService.isBlacklisted("expired-token")).isFalse();
    }

    @Test
    void isBlacklisted_expiredEntry_removedFromMap() {
        blacklistService.blacklist("expired-token", -1000L);
        // First call discovers and removes the expired entry
        blacklistService.isBlacklisted("expired-token");
        // Second call still returns false (entry was removed)
        assertThat(blacklistService.isBlacklisted("expired-token")).isFalse();
    }

    @Test
    void evictExpired_removesOnlyExpiredEntries() {
        blacklistService.blacklist("valid-token", 60_000L);
        blacklistService.blacklist("expired-token", -1000L);

        blacklistService.evictExpired();

        assertThat(blacklistService.isBlacklisted("valid-token")).isTrue();
        assertThat(blacklistService.isBlacklisted("expired-token")).isFalse();
    }

    @Test
    void multipleTokens_eachTrackedIndependently() {
        blacklistService.blacklist("token-a", 60_000L);
        blacklistService.blacklist("token-b", 60_000L);

        assertThat(blacklistService.isBlacklisted("token-a")).isTrue();
        assertThat(blacklistService.isBlacklisted("token-b")).isTrue();
        assertThat(blacklistService.isBlacklisted("token-c")).isFalse();
    }
}
