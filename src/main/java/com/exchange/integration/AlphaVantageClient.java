package com.exchange.integration;

import com.exchange.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
public class AlphaVantageClient {

    private final WebClient webClient;

    @Value("${external.api.alpha-vantage.api-key:demo}")
    private String apiKey;

    public AlphaVantageClient(@Qualifier("alphaVantageWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        log.info("Fetching rate {}/{} from Alpha Vantage (fallback)", fromCurrency, toCurrency);
        try {
            Map<?, ?> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("function", "CURRENCY_EXCHANGE_RATE")
                            .queryParam("from_currency", fromCurrency)
                            .queryParam("to_currency", toCurrency)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                throw new ExternalApiException("Alpha Vantage returned null response");
            }

            @SuppressWarnings("unchecked")
            Map<String, String> realtimeRate =
                    (Map<String, String>) response.get("Realtime Currency Exchange Rate");
            if (realtimeRate == null) {
                throw new ExternalApiException("Alpha Vantage: missing 'Realtime Currency Exchange Rate' field");
            }
            String rateStr = realtimeRate.get("5. Exchange Rate");
            if (rateStr == null) {
                throw new ExternalApiException("Alpha Vantage: missing exchange rate value");
            }
            return new BigDecimal(rateStr);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Failed to fetch rate from Alpha Vantage", e);
        }
    }
}
