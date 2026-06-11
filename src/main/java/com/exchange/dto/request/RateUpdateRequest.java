package com.exchange.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RateUpdateRequest {

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Rate must be positive")
    @Digits(integer = 18, fraction = 8, message = "Rate must have at most 18 integer and 8 decimal digits")
    private BigDecimal rate;
}
