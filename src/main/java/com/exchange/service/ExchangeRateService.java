package com.exchange.service;

import com.exchange.dto.request.RateRequest;
import com.exchange.dto.request.RateUpdateRequest;
import com.exchange.dto.response.RateResponse;
import com.exchange.exception.DuplicateResourceException;
import com.exchange.exception.ExternalApiException;
import com.exchange.exception.ResourceNotFoundException;
import com.exchange.integration.AlphaVantageClient;
import com.exchange.integration.ExternalRateApiClient;
import com.exchange.model.ExchangeRate;
import com.exchange.model.enums.Source;
import com.exchange.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cache-aside exchange rate service (Redis, 5-min TTL). MANUAL-source rates set by admins
 * bypass live API fetch entirely and are served directly from the DB. Live rates flow through
 * a primary (exchangerate.host + circuit breaker) → fallback (Alpha Vantage) chain.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository rateRepository;
    private final ExternalRateApiClient primaryApiClient;
    private final AlphaVantageClient fallbackApiClient;

    @Cacheable(value = "rates", key = "#fromCurrency + '::' + #toCurrency")
    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        fromCurrency = fromCurrency.toUpperCase();
        toCurrency = toCurrency.toUpperCase();

        // Check DB for manually set (MANUAL source) rates — skip live fetch for admin-overridden rates
        var dbRate = rateRepository.findByFromCurrencyAndToCurrencyAndIsActiveTrue(fromCurrency, toCurrency);
        if (dbRate.isPresent() && dbRate.get().getSource() == Source.MANUAL) {
            log.info("Rate {}/{} served from manually set DB record", fromCurrency, toCurrency);
            return dbRate.get().getRate();
        }

        // Fetch live rate from external API (primary with circuit breaker → fallback)
        BigDecimal rate;
        try {
            rate = primaryApiClient.getRate(fromCurrency, toCurrency);
        } catch (ExternalApiException e) {
            log.warn("Primary API failed for {}/{}, trying fallback: {}", fromCurrency, toCurrency, e.getMessage());
            rate = fallbackApiClient.getRate(fromCurrency, toCurrency);
        }

        // Persist fetched rate (updates lastUpdated via @PreUpdate)
        saveOrUpdateRate(fromCurrency, toCurrency, rate, Source.API);
        return rate;
    }

    public RateResponse getRateResponse(String fromCurrency, String toCurrency) {
        BigDecimal rate = getRate(fromCurrency, toCurrency);
        return RateResponse.builder()
                .fromCurrency(fromCurrency.toUpperCase())
                .toCurrency(toCurrency.toUpperCase())
                .rate(rate)
                .build();
    }

    public List<RateResponse> getAllActiveRates() {
        return rateRepository.findAllByIsActiveTrueOrderByFromCurrencyAsc()
                .stream().map(this::toResponse).toList();
    }

    public Page<RateResponse> getAllRates(Pageable pageable) {
        return rateRepository.findAllByOrderByFromCurrencyAsc(pageable).map(this::toResponse);
    }

    @Transactional
    @CacheEvict(value = "rates", key = "#request.fromCurrency.toUpperCase() + '::' + #request.toCurrency.toUpperCase()")
    public RateResponse createRate(RateRequest request) {
        if (rateRepository.existsByFromCurrencyAndToCurrency(
                request.getFromCurrency(), request.getToCurrency())) {
            throw new DuplicateResourceException(
                    "Rate already exists for pair: " + request.getFromCurrency() + "/" + request.getToCurrency());
        }
        ExchangeRate rate = ExchangeRate.builder()
                .fromCurrency(request.getFromCurrency().toUpperCase())
                .toCurrency(request.getToCurrency().toUpperCase())
                .rate(request.getRate())
                .source(Source.MANUAL)
                .build();
        return toResponse(rateRepository.save(rate));
    }

    @Transactional
    @CacheEvict(value = "rates", allEntries = true)
    public RateResponse updateRate(Long id, RateUpdateRequest request) {
        ExchangeRate rate = rateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exchange rate not found: " + id));
        rate.setRate(request.getRate());
        rate.setSource(Source.MANUAL);
        return toResponse(rateRepository.save(rate));
    }

    // Soft-delete: sets active=false to preserve historical transaction references
    @Transactional
    @CacheEvict(value = "rates", allEntries = true)
    public void deleteRate(Long id) {
        ExchangeRate rate = rateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exchange rate not found: " + id));
        rate.setActive(false);
        rateRepository.save(rate);
    }

    private void saveOrUpdateRate(String fromCurrency, String toCurrency, BigDecimal rateValue, Source source) {
        rateRepository.findByFromCurrencyAndToCurrencyAndIsActiveTrue(fromCurrency, toCurrency)
                .ifPresentOrElse(
                        existing -> {
                            existing.setRate(rateValue);
                            existing.setSource(source);
                            rateRepository.save(existing);
                        },
                        () -> rateRepository.save(ExchangeRate.builder()
                                .fromCurrency(fromCurrency)
                                .toCurrency(toCurrency)
                                .rate(rateValue)
                                .source(source)
                                .build())
                );
    }

    private RateResponse toResponse(ExchangeRate rate) {
        return RateResponse.builder()
                .id(rate.getId())
                .fromCurrency(rate.getFromCurrency())
                .toCurrency(rate.getToCurrency())
                .rate(rate.getRate())
                .lastUpdated(rate.getLastUpdated())
                .source(rate.getSource().name())
                .isActive(rate.isActive())
                .build();
    }
}
