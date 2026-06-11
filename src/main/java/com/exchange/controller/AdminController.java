package com.exchange.controller;

import com.exchange.dto.response.DashboardStatsResponse;
import com.exchange.dto.response.LogResponse;
import com.exchange.dto.response.TransactionResponse;
import com.exchange.dto.response.UserResponse;
import com.exchange.model.Log;
import com.exchange.model.User;
import com.exchange.model.enums.TransactionStatus;
import com.exchange.repository.LogRepository;
import com.exchange.repository.TransactionRepository;
import com.exchange.repository.UserRepository;
import com.exchange.service.LogService;
import com.exchange.service.TransactionService;
import com.exchange.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Admin", description = "Admin-only operations")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final TransactionService transactionService;
    private final LogRepository logRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    // ── Dashboard ──────────────────────────────────────────────────────────────

    @Operation(summary = "Get dashboard statistics")
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStatsResponse> getDashboard() {
        List<Object[]> topCurrencies = transactionRepository.findTopFromCurrencies(PageRequest.of(0, 5));
        List<String> topFrom = topCurrencies.stream()
                .map(row -> (String) row[0])
                .toList();

        DashboardStatsResponse stats = DashboardStatsResponse.builder()
                .totalUsers(userRepository.count())
                .activeUsers(userRepository.countActive())
                .totalTransactions(transactionRepository.countAll())
                .pendingApprovals(transactionRepository.countByStatus(TransactionStatus.PENDING_APPROVAL))
                .topFromCurrencies(topFrom)
                .build();
        return ResponseEntity.ok(stats);
    }

    // ── User Management ────────────────────────────────────────────────────────

    @Operation(summary = "List all users")
    @GetMapping("/users")
    public ResponseEntity<Page<UserResponse>> listUsers(Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @Operation(summary = "Get a user by ID")
    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @Operation(summary = "Deactivate a user")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    // ── Transaction Approvals ─────────────────────────────────────────────────

    @Operation(summary = "List all transactions (admin view)")
    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionResponse>> listTransactions(Pageable pageable) {
        return ResponseEntity.ok(transactionService.getAllTransactions(pageable));
    }

    @Operation(summary = "List pending approval transactions")
    @GetMapping("/transactions/pending")
    public ResponseEntity<List<TransactionResponse>> listPending() {
        return ResponseEntity.ok(transactionService.getPendingApprovals());
    }

    @Operation(summary = "Approve a transaction")
    @PostMapping("/transactions/{id}/approve")
    public ResponseEntity<TransactionResponse> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(transactionService.approve(id, admin.getUserId()));
    }

    @Operation(summary = "Reject a transaction")
    @PostMapping("/transactions/{id}/reject")
    public ResponseEntity<TransactionResponse> reject(
            @PathVariable UUID id,
            @AuthenticationPrincipal User admin,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(transactionService.reject(id, admin.getUserId(), reason));
    }

    // ── Logs ──────────────────────────────────────────────────────────────────

    @Operation(summary = "View system logs")
    @GetMapping("/logs")
    public ResponseEntity<Page<LogResponse>> getLogs(Pageable pageable) {
        return ResponseEntity.ok(logRepository.findAllByOrderByTimestampDesc(pageable).map(this::toLogResponse));
    }

    @Operation(summary = "View logs by event type")
    @GetMapping("/logs/type/{eventType}")
    public ResponseEntity<Page<LogResponse>> getLogsByType(
            @PathVariable String eventType, Pageable pageable) {
        return ResponseEntity.ok(
                logRepository.findByEventTypeOrderByTimestampDesc(eventType, pageable).map(this::toLogResponse));
    }

    private LogResponse toLogResponse(Log log) {
        return LogResponse.builder()
                .logId(log.getLogId())
                .event(log.getEvent())
                .eventType(log.getEventType())
                .timestamp(log.getTimestamp())
                .userId(log.getUserId())
                .ipAddress(log.getIpAddress())
                .details(log.getDetails())
                .build();
    }
}
