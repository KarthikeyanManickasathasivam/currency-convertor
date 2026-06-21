package com.exchange.service;

import com.exchange.model.Log;
import com.exchange.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Fire-and-forget audit logging. All writes are {@code @Async} so a slow DB insert or
 * write failure never blocks or rolls back the calling transaction.
 */
@Service
@RequiredArgsConstructor
public class LogService {

    private final LogRepository logRepository;

    @Async
    public void log(String event, String eventType, UUID userId, String ipAddress, Map<String, Object> details) {
        Log entry = Log.builder()
                .event(event)
                .eventType(eventType)
                .userId(userId)
                .ipAddress(ipAddress)
                .details(details)
                .build();
        logRepository.save(entry);
    }
}
