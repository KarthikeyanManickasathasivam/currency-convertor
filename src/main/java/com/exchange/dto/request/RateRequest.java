package com.exchange.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RateRequest {

    @NotBlank(message = "From currency is required")
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency code must be uppercase letters")
    private String fromCurrency;

    @NotBlank(message = "To currency is required")
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency code must be uppercase letters")
    private String toCurrency;

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Rate must be positive")
    @Digits(integer = 18, fraction = 8, message = "Rate must have at most 18 integer and 8 decimal digits")
    private BigDecimal rate;
}
