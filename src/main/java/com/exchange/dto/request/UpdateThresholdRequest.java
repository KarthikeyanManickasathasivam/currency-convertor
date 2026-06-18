package com.exchange.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateThresholdRequest(
        @NotNull(message = "threshold is required")
        @DecimalMin(value = "0.01", message = "threshold must be at least 0.01")
        @Digits(integer = 18, fraction = 2, message = "threshold must have at most 2 decimal places")
        BigDecimal threshold
) {}
