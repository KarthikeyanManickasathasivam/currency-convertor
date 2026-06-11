package com.exchange.service;

import com.exchange.dto.response.RateResponse;
import com.exchange.exception.ExternalApiException;
import com.exchange.integration.AlphaVantageClient;
import com.exchange.integration.ExternalRateApiClient;
import com.exchange.model.ExchangeRate;
import com.exchange.model.enums.Source;
import com.exchange.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock private ExchangeRateRepository rateRepository;
    @Mock private ExternalRateApiClient primaryApiClient;
    @Mock private AlphaVantageClient fallbackApiClient;

    @InjectMocks
    private ExchangeRateService rateService;

    @Test
    void getRate_fromDb_returnsDbRate() {
        ExchangeRate dbRate = ExchangeRate.builder()
                .fromCurrency("USD")
                .toCurrency("EUR")
                .rate(new BigDecimal("0.92"))
                .source(Source.MANUAL)
                .isActive(true)
                .lastUpdated(LocalDateTime.now())
                .build();

        when(rateRepository.findByFromCurrencyAndToCurrencyAndIsActiveTrue("USD", "EUR"))
                .thenReturn(Optional.of(dbRate));

        BigDecimal rate = rateService.getRate("USD", "EUR");

        assertThat(rate).isEqualByComparingTo("0.92");
        verifyNoInteractions(primaryApiClient);
        verifyNoInteractions(fallbackApiClient);
    }

    @Test
    void getRate_dbMiss_fetchesFromPrimaryApi() {
        when(rateRepository.findByFromCurrencyAndToCurrencyAndIsActiveTrue("USD", "EUR"))
                .thenReturn(Optional.empty());
        when(primaryApiClient.getRate("USD", "EUR")).thenReturn(new BigDecimal("0.91500000"));

        BigDecimal rate = rateService.getRate("USD", "EUR");

        assertThat(rate).isEqualByComparingTo("0.91500000");
        verify(primaryApiClient).getRate("USD", "EUR");
        verifyNoInteractions(fallbackApiClient);
    }

    @Test
    void getRate_primaryFails_usesFallback() {
        when(rateRepository.findByFromCurrencyAndToCurrencyAndIsActiveTrue("USD", "GBP"))
                .thenReturn(Optional.empty());
        when(primaryApiClient.getRate("USD", "GBP"))
                .thenThrow(new ExternalApiException("Primary API down"));
        when(fallbackApiClient.getRate("USD", "GBP")).thenReturn(new BigDecimal("0.79000000"));

        BigDecimal rate = rateService.getRate("USD", "GBP");

        assertThat(rate).isEqualByComparingTo("0.79000000");
        verify(fallbackApiClient).getRate("USD", "GBP");
    }

    @Test
    void getRateResponse_returnsCorrectDto() {
        ExchangeRate dbRate = ExchangeRate.builder()
                .fromCurrency("USD").toCurrency("EUR")
                .rate(new BigDecimal("0.92")).source(Source.API)
                .isActive(true).lastUpdated(LocalDateTime.now()).build();

        when(rateRepository.findByFromCurrencyAndToCurrencyAndIsActiveTrue("USD", "EUR"))
                .thenReturn(Optional.of(dbRate));

        RateResponse response = rateService.getRateResponse("USD", "EUR");

        assertThat(response.getFromCurrency()).isEqualTo("USD");
        assertThat(response.getToCurrency()).isEqualTo("EUR");
        assertThat(response.getRate()).isEqualByComparingTo("0.92");
    }
}
