package com.exchange.service.impl;

import com.exchange.service.TokenBlacklistService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("local")
public class InMemoryTokenBlacklistService implements TokenBlacklistService {

    private final Map<String, Instant> blacklist = new ConcurrentHashMap<>();

    @Override
    public void blacklist(String token, long expiryMillis) {
        blacklist.put(token, Instant.now().plusMillis(expiryMillis));
    }

    @Override
    public boolean isBlacklisted(String token) {
        Instant expiry = blacklist.get(token);
        if (expiry == null) return false;
        if (Instant.now().isAfter(expiry)) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    @Scheduled(fixedDelay = 300_000)
    public void evictExpired() {
        Instant now = Instant.now();
        blacklist.entrySet().removeIf(e -> now.isAfter(e.getValue()));
    }
}
