package com.exchange.service;

import com.exchange.dto.request.ConversionRequest;
import com.exchange.dto.response.TransactionResponse;
import com.exchange.exception.ResourceNotFoundException;
import com.exchange.exception.TransactionNotFoundException;
import com.exchange.model.Transaction;
import com.exchange.model.User;
import com.exchange.model.enums.TransactionStatus;
import com.exchange.repository.TransactionRepository;
import com.exchange.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    private final EmailService emailService;
    private final LogService logService;
    private final AppSettingService appSettingService;

    @org.springframework.beans.factory.annotation.Value("${transaction.admin.email:admin@example.com}")
    private String adminEmail;

    @Transactional
    public TransactionResponse convert(ConversionRequest request, User user) {
        BigDecimal rate = exchangeRateService.getRate(request.getFromCurrency(), request.getToCurrency());
        BigDecimal convertedAmount = request.getAmount()
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);

        // Threshold is in USD — convert amount to USD equivalent before comparing
        BigDecimal amountInUsd;
        if ("USD".equalsIgnoreCase(request.getFromCurrency())) {
            amountInUsd = request.getAmount();
        } else {
            BigDecimal rateToUsd = exchangeRateService.getRate(request.getFromCurrency(), "USD");
            amountInUsd = request.getAmount().multiply(rateToUsd).setScale(2, RoundingMode.HALF_UP);
        }
        boolean requiresApproval = amountInUsd.compareTo(appSettingService.getApprovalThreshold()) >= 0;
        TransactionStatus status = requiresApproval
                ? TransactionStatus.PENDING_APPROVAL
                : TransactionStatus.APPROVED;

        Transaction tx = Transaction.builder()
                .user(user)
                .fromCurrency(request.getFromCurrency().toUpperCase())
                .toCurrency(request.getToCurrency().toUpperCase())
                .amount(request.getAmount())
                .convertedAmount(convertedAmount)
                .rate(rate)
                .status(status)
                .build();

        Transaction saved = transactionRepository.save(tx);

        if (requiresApproval) {
            emailService.sendApprovalRequiredNotification(
                    adminEmail, saved.getTransactionId(), user.getEmail(),
                    request.getAmount(), request.getFromCurrency(), request.getToCurrency());
            log.info("Transaction {} requires admin approval. Amount: {} {}",
                    saved.getTransactionId(), request.getAmount(), request.getFromCurrency());
        }

        logService.log("TRANSACTION_CREATED", "TRANSACTION", user.getUserId(),
                null, Map.of(
                        "transactionId", saved.getTransactionId().toString(),
                        "status", status.name(),
                        "amount", request.getAmount().toString(),
                        "from", request.getFromCurrency(),
                        "to", request.getToCurrency()
                ));

        return toResponse(saved);
    }

    public Page<TransactionResponse> getTransactionsForUser(UUID userId, Pageable pageable) {
        return transactionRepository
                .findByUserUserIdOrderByTransactionDateDesc(userId, pageable)
                .map(this::toResponse);
    }

    public Page<TransactionResponse> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAllByOrderByTransactionDateDesc(pageable).map(this::toResponse);
    }

    public List<TransactionResponse> getPendingApprovals() {
        return transactionRepository
                .findByStatusOrderByTransactionDateDesc(TransactionStatus.PENDING_APPROVAL)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public TransactionResponse approve(UUID transactionId, UUID adminId) {
        Transaction tx = getTransactionOrThrow(transactionId);
        if (tx.getStatus() != TransactionStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Transaction is not pending approval: " + transactionId);
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));

        tx.setStatus(TransactionStatus.APPROVED);
        tx.setApprovedBy(admin);
        tx.setApprovalDate(LocalDateTime.now());
        Transaction saved = transactionRepository.save(tx);

        emailService.sendApprovalNotification(
                tx.getUser().getEmail(), transactionId,
                tx.getConvertedAmount(), tx.getToCurrency());

        logService.log("TRANSACTION_APPROVED", "TRANSACTION", adminId,
                null, Map.of("transactionId", transactionId.toString()));

        return toResponse(saved);
    }

    @Transactional
    public TransactionResponse reject(UUID transactionId, UUID adminId, String reason) {
        Transaction tx = getTransactionOrThrow(transactionId);
        if (tx.getStatus() != TransactionStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Transaction is not pending approval: " + transactionId);
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));

        tx.setStatus(TransactionStatus.REJECTED);
        tx.setApprovedBy(admin);
        tx.setApprovalDate(LocalDateTime.now());
        Transaction saved = transactionRepository.save(tx);

        emailService.sendRejectionNotification(tx.getUser().getEmail(), transactionId, reason);

        logService.log("TRANSACTION_REJECTED", "TRANSACTION", adminId,
                null, Map.of(
                        "transactionId", transactionId.toString(),
                        "reason", reason != null ? reason : ""
                ));

        return toResponse(saved);
    }

    private Transaction getTransactionOrThrow(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id));
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .transactionId(tx.getTransactionId())
                .userId(tx.getUser().getUserId())
                .fromCurrency(tx.getFromCurrency())
                .toCurrency(tx.getToCurrency())
                .amount(tx.getAmount())
                .approvalThreshold(appSettingService.getApprovalThreshold())
                .convertedAmount(tx.getConvertedAmount())
                .rate(tx.getRate())
                .transactionDate(tx.getTransactionDate())
                .status(tx.getStatus().name())
                .approvedBy(tx.getApprovedBy() != null ? tx.getApprovedBy().getUserId() : null)
                .approvalDate(tx.getApprovalDate())
                .build();
    }
}
