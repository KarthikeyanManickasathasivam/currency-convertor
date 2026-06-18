package com.exchange.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ApprovalThresholdResponse {
    private BigDecimal threshold;
    private LocalDateTime updatedAt;
    private UUID updatedBy;
}
