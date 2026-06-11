package com.exchange.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class LogResponse {
    private Long logId;
    private String event;
    private String eventType;
    private LocalDateTime timestamp;
    private UUID userId;
    private String ipAddress;
    private Map<String, Object> details;
}
