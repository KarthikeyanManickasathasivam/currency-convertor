package com.exchange.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RateResponse {
    private Long id;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal rate;
    private LocalDateTime lastUpdated;
    private String source;
    private boolean isActive;
}
