package com.exchange.service;

import com.exchange.dto.request.ConversionRequest;
import com.exchange.dto.response.TransactionResponse;
import com.exchange.model.Transaction;
import com.exchange.model.User;
import com.exchange.model.enums.Role;
import com.exchange.model.enums.TransactionStatus;
import com.exchange.repository.TransactionRepository;
import com.exchange.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private EmailService emailService;
    @Mock private LogService logService;
    @Mock private AppSettingService appSettingService;

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(transactionService, "adminEmail", "admin@example.com");

        testUser = User.builder()
                .userId(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .role(Role.USER)
                .isActive(true)
                .build();

        adminUser = User.builder()
                .userId(UUID.randomUUID())
                .username("admin")
                .email("admin@example.com")
                .role(Role.ADMIN)
                .isActive(true)
                .build();
    }

    @Test
    void convert_belowThreshold_approvedImmediately() {
        ConversionRequest req = new ConversionRequest();
        req.setFromCurrency("USD");
        req.setToCurrency("EUR");
        req.setAmount(new BigDecimal("50.00"));

        when(appSettingService.getApprovalThreshold()).thenReturn(new BigDecimal("100"));
        when(exchangeRateService.getRate("USD", "EUR")).thenReturn(new BigDecimal("0.92"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "transactionId", UUID.randomUUID());
            return t;
        });

        TransactionResponse result = transactionService.convert(req, testUser);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getConvertedAmount()).isEqualByComparingTo("46.00");
        verifyNoInteractions(emailService);
    }

    @Test
    void convert_aboveThreshold_pendingApproval() {
        ConversionRequest req = new ConversionRequest();
        req.setFromCurrency("USD");
        req.setToCurrency("EUR");
        req.setAmount(new BigDecimal("500.00"));

        when(appSettingService.getApprovalThreshold()).thenReturn(new BigDecimal("100"));
        when(exchangeRateService.getRate("USD", "EUR")).thenReturn(new BigDecimal("0.92"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "transactionId", UUID.randomUUID());
            return t;
        });

        TransactionResponse result = transactionService.convert(req, testUser);

        assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
        verify(emailService).sendApprovalRequiredNotification(
                eq("admin@example.com"), any(), eq(testUser.getEmail()),
                eq(new BigDecimal("500.00")), eq("USD"), eq("EUR"));
    }

    @Test
    void convert_exactlyAtThreshold_pendingApproval() {
        ConversionRequest req = new ConversionRequest();
        req.setFromCurrency("USD");
        req.setToCurrency("GBP");
        req.setAmount(new BigDecimal("100.00")); // exactly at threshold

        when(appSettingService.getApprovalThreshold()).thenReturn(new BigDecimal("100"));
        when(exchangeRateService.getRate("USD", "GBP")).thenReturn(new BigDecimal("0.79"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "transactionId", UUID.randomUUID());
            return t;
        });

        TransactionResponse result = transactionService.convert(req, testUser);

        assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void approve_pendingTransaction_approvesAndNotifies() {
        UUID txId = UUID.randomUUID();
        Transaction tx = Transaction.builder()
                .user(testUser)
                .fromCurrency("USD")
                .toCurrency("EUR")
                .amount(new BigDecimal("200.00"))
                .convertedAmount(new BigDecimal("184.00"))
                .rate(new BigDecimal("0.92"))
                .status(TransactionStatus.PENDING_APPROVAL)
                .build();
        ReflectionTestUtils.setField(tx, "transactionId", txId);

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(userRepository.findById(adminUser.getUserId())).thenReturn(Optional.of(adminUser));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse result = transactionService.approve(txId, adminUser.getUserId());

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(emailService).sendApprovalNotification(
                eq(testUser.getEmail()), eq(txId), any(), eq("EUR"));
    }

    @Test
    void reject_pendingTransaction_rejectsAndNotifies() {
        UUID txId = UUID.randomUUID();
        Transaction tx = Transaction.builder()
                .user(testUser)
                .fromCurrency("USD")
                .toCurrency("EUR")
                .amount(new BigDecimal("200.00"))
                .convertedAmount(new BigDecimal("184.00"))
                .rate(new BigDecimal("0.92"))
                .status(TransactionStatus.PENDING_APPROVAL)
                .build();
        ReflectionTestUtils.setField(tx, "transactionId", txId);

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(userRepository.findById(adminUser.getUserId())).thenReturn(Optional.of(adminUser));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse result = transactionService.reject(txId, adminUser.getUserId(), "Suspicious activity");

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        verify(emailService).sendRejectionNotification(
                eq(testUser.getEmail()), eq(txId), eq("Suspicious activity"));
    }
}
