package com.exchange.model;

import com.exchange.model.enums.Source;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;

    @Column(name = "to_currency", nullable = false, length = 3)
    private String toCurrency;

    @Column(name = "rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    @Builder.Default
    private Source source = Source.API;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
}
