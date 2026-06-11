package com.exchange.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID transactionId;
    private UUID userId;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal amount;
    private BigDecimal convertedAmount;
    private BigDecimal rate;
    private LocalDateTime transactionDate;
    private String status;
    private UUID approvedBy;
    private LocalDateTime approvalDate;
}
