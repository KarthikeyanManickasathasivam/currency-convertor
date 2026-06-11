package com.exchange.service;

import com.exchange.model.Log;
import com.exchange.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

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
