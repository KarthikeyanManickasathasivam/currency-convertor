package com.exchange.service.impl;

import com.exchange.service.OtpService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("local")
public class InMemoryOtpService implements OtpService {

    private static final long TTL_SECONDS = 300; // 5 minutes
    private static final SecureRandom RANDOM = new SecureRandom();

    private record OtpEntry(String otp, Instant expiresAt) {}

    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    @Override
    public String generateAndStore(String email) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        store.put(email.toLowerCase(), new OtpEntry(otp, Instant.now().plusSeconds(TTL_SECONDS)));
        return otp;
    }

    @Override
    public void storeOtp(String email, String otp) {
        store.put(email.toLowerCase(), new OtpEntry(otp, Instant.now().plusSeconds(TTL_SECONDS)));
    }

    @Override
    public boolean verify(String email, String otp) {
        OtpEntry entry = store.get(email.toLowerCase());
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            store.remove(email.toLowerCase());
            return false;
        }
        if (entry.otp().equals(otp)) {
            store.remove(email.toLowerCase());
            return true;
        }
        return false;
    }

    @Override
    public void invalidate(String email) {
        store.remove(email.toLowerCase());
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }
}
