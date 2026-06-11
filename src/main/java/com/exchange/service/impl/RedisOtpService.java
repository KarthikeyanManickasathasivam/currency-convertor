package com.exchange.service.impl;

import com.exchange.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@Profile("aws")
@RequiredArgsConstructor
public class RedisOtpService implements OtpService {

    private static final String KEY_PREFIX = "otp::";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    @Override
    public String generateAndStore(String email) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(KEY_PREFIX + email.toLowerCase(), otp, TTL);
        return otp;
    }

    @Override
    public void storeOtp(String email, String otp) {
        redisTemplate.opsForValue().set(KEY_PREFIX + email.toLowerCase(), otp, TTL);
    }

    @Override
    public boolean verify(String email, String otp) {
        String key = KEY_PREFIX + email.toLowerCase();
        String stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.equals(otp)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    @Override
    public void invalidate(String email) {
        redisTemplate.delete(KEY_PREFIX + email.toLowerCase());
    }
}
