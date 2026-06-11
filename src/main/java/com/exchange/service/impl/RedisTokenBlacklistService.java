package com.exchange.service.impl;

import com.exchange.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Profile("aws")
@RequiredArgsConstructor
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final String KEY_PREFIX = "blacklist::";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blacklist(String token, long expiryMillis) {
        if (expiryMillis > 0) {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + token, "1", Duration.ofMillis(expiryMillis));
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
