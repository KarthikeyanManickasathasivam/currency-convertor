package com.exchange.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ConversionRequest {

    @NotBlank(message = "From currency is required")
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency code must be uppercase letters")
    private String fromCurrency;

    @NotBlank(message = "To currency is required")
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency code must be uppercase letters")
    private String toCurrency;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 18, fraction = 2, message = "Amount must have at most 18 integer and 2 decimal digits")
    private BigDecimal amount;
}
