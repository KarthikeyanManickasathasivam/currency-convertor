package com.exchange.controller;

import com.exchange.dto.request.ConversionRequest;
import com.exchange.dto.response.TransactionResponse;
import com.exchange.model.User;
import com.exchange.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transactions", description = "Currency conversion and transaction history")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "Convert currency")
    @PostMapping
    public ResponseEntity<TransactionResponse> convert(
            @Valid @RequestBody ConversionRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.convert(request, user));
    }

    @Operation(summary = "Get current user's transaction history")
    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getMyTransactions(
            @AuthenticationPrincipal User user,
            Pageable pageable) {
        return ResponseEntity.ok(transactionService.getTransactionsForUser(user.getUserId(), pageable));
    }
}
