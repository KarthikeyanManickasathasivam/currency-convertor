package com.exchange.integration;

import com.exchange.exception.ExternalApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@Primary
public class ExchangeRateHostClient implements ExternalRateApiClient {

    private final WebClient webClient;

    @Value("${external.api.exchangerate-host.api-key:}")
    private String apiKey;

    public ExchangeRateHostClient(@Qualifier("exchangeRateHostWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    @CircuitBreaker(name = "rateApi", fallbackMethod = "fallbackRate")
    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        log.debug("Fetching rate {}/{} from exchangerate.host", fromCurrency, toCurrency);
        try {
            Map<?, ?> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/live")
                            .queryParam("base", fromCurrency)
                            .queryParam("currencies", toCurrency)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                throw new ExternalApiException("exchangerate.host returned unsuccessful response");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> quotes = (Map<String, Object>) response.get("quotes");
            String key = fromCurrency + toCurrency;
            Object rateObj = quotes.get(key);
            if (rateObj == null) {
                throw new ExternalApiException("Rate not found in response for pair: " + key);
            }
            return new BigDecimal(rateObj.toString());
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Failed to fetch rate from exchangerate.host", e);
        }
    }

    @SuppressWarnings("unused")
    public BigDecimal fallbackRate(String fromCurrency, String toCurrency, Exception ex) {
        log.warn("Circuit breaker fallback triggered for {}/{}: {}", fromCurrency, toCurrency, ex.getMessage());
        throw new ExternalApiException("Exchange rate service unavailable. Please try again later.");
    }
}
