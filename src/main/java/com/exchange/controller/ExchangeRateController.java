package com.exchange.controller;

import com.exchange.dto.request.RateRequest;
import com.exchange.dto.request.RateUpdateRequest;
import com.exchange.dto.response.RateResponse;
import com.exchange.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Exchange Rates", description = "Get and manage currency exchange rates")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService rateService;

    @Operation(summary = "Get rate for a currency pair")
    @GetMapping("/{from}/{to}")
    public ResponseEntity<RateResponse> getRate(
            @PathVariable String from,
            @PathVariable String to) {
        return ResponseEntity.ok(rateService.getRateResponse(from, to));
    }

    @Operation(summary = "Get all active exchange rates")
    @GetMapping
    public ResponseEntity<List<RateResponse>> getAllRates() {
        return ResponseEntity.ok(rateService.getAllActiveRates());
    }

    @Operation(summary = "Get all rates paginated (admin)")
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<RateResponse>> getAllRatesPaged(Pageable pageable) {
        return ResponseEntity.ok(rateService.getAllRates(pageable));
    }

    @Operation(summary = "Create a manual exchange rate (admin)")
    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RateResponse> createRate(@Valid @RequestBody RateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rateService.createRate(request));
    }

    @Operation(summary = "Update an exchange rate (admin)")
    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RateResponse> updateRate(
            @PathVariable Long id,
            @Valid @RequestBody RateUpdateRequest request) {
        return ResponseEntity.ok(rateService.updateRate(id, request));
    }

    @Operation(summary = "Soft-delete an exchange rate (admin)")
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRate(@PathVariable Long id) {
        rateService.deleteRate(id);
        return ResponseEntity.noContent().build();
    }
}
