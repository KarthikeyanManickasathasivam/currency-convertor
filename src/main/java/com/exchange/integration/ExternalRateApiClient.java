package com.exchange.integration;

import java.math.BigDecimal;

public interface ExternalRateApiClient {
    BigDecimal getRate(String fromCurrency, String toCurrency);
}
