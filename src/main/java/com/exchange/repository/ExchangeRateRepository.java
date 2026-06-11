package com.exchange.repository;

import com.exchange.model.ExchangeRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByFromCurrencyAndToCurrencyAndIsActiveTrue(
            String fromCurrency, String toCurrency);

    List<ExchangeRate> findAllByIsActiveTrueOrderByFromCurrencyAsc();

    Page<ExchangeRate> findAllByOrderByFromCurrencyAsc(Pageable pageable);

    boolean existsByFromCurrencyAndToCurrency(String fromCurrency, String toCurrency);

    @Query("SELECT DISTINCT e.fromCurrency FROM ExchangeRate e WHERE e.isActive = true")
    List<String> findDistinctActiveCurrencies();
}
