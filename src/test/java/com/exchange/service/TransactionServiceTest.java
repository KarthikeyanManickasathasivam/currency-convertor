package com.exchange.service;

import com.exchange.dto.request.ConversionRequest;
import com.exchange.dto.response.TransactionResponse;
import com.exchange.exception.TransactionNotFoundException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
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

    @Test
    void convert_nonUsdFromCurrency_convertsToUsdForThresholdComparison() {
        // EUR amount; threshold comparison must convert EUR→USD first
        ConversionRequest req = new ConversionRequest();
        req.setFromCurrency("EUR");
        req.setToCurrency("GBP");
        req.setAmount(new BigDecimal("200.00")); // 200 EUR * 1.08 = 216 USD → above 100 threshold

        when(appSettingService.getApprovalThreshold()).thenReturn(new BigDecimal("100"));
        when(exchangeRateService.getRate("EUR", "GBP")).thenReturn(new BigDecimal("0.86"));
        when(exchangeRateService.getRate("EUR", "USD")).thenReturn(new BigDecimal("1.08"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "transactionId", UUID.randomUUID());
            return t;
        });

        TransactionResponse result = transactionService.convert(req, testUser);

        assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
        verify(exchangeRateService).getRate("EUR", "USD");
    }

    @Test
    void convert_nonUsdBelowThreshold_approvedImmediately() {
        ConversionRequest req = new ConversionRequest();
        req.setFromCurrency("EUR");
        req.setToCurrency("GBP");
        req.setAmount(new BigDecimal("10.00")); // 10 EUR * 1.08 = 10.80 USD → below 100

        when(appSettingService.getApprovalThreshold()).thenReturn(new BigDecimal("100"));
        when(exchangeRateService.getRate("EUR", "GBP")).thenReturn(new BigDecimal("0.86"));
        when(exchangeRateService.getRate("EUR", "USD")).thenReturn(new BigDecimal("1.08"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "transactionId", UUID.randomUUID());
            return t;
        });

        TransactionResponse result = transactionService.convert(req, testUser);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verifyNoInteractions(emailService);
    }

    @Test
    void approve_alreadyApprovedTransaction_throwsIllegalState() {
        UUID txId = UUID.randomUUID();
        Transaction tx = Transaction.builder()
                .user(testUser).fromCurrency("USD").toCurrency("EUR")
                .amount(new BigDecimal("200.00")).convertedAmount(new BigDecimal("184.00"))
                .rate(new BigDecimal("0.92")).status(TransactionStatus.APPROVED).build();
        ReflectionTestUtils.setField(tx, "transactionId", txId);

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> transactionService.approve(txId, adminUser.getUserId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending approval");
    }

    @Test
    void reject_alreadyRejectedTransaction_throwsIllegalState() {
        UUID txId = UUID.randomUUID();
        Transaction tx = Transaction.builder()
                .user(testUser).fromCurrency("USD").toCurrency("EUR")
                .amount(new BigDecimal("200.00")).convertedAmount(new BigDecimal("184.00"))
                .rate(new BigDecimal("0.92")).status(TransactionStatus.REJECTED).build();
        ReflectionTestUtils.setField(tx, "transactionId", txId);

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> transactionService.reject(txId, adminUser.getUserId(), "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending approval");
    }

    @Test
    void approve_transactionNotFound_throwsTransactionNotFound() {
        UUID txId = UUID.randomUUID();
        when(transactionRepository.findById(txId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.approve(txId, adminUser.getUserId()))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void getTransactionsForUser_returnsPaginatedResults() {
        UUID userId = testUser.getUserId();
        Transaction tx = Transaction.builder()
                .user(testUser).fromCurrency("USD").toCurrency("EUR")
                .amount(new BigDecimal("50.00")).convertedAmount(new BigDecimal("46.00"))
                .rate(new BigDecimal("0.92")).status(TransactionStatus.APPROVED).build();
        ReflectionTestUtils.setField(tx, "transactionId", UUID.randomUUID());

        when(transactionRepository.findByUserUserIdOrderByTransactionDateDesc(eq(userId), any()))
                .thenReturn(new PageImpl<>(List.of(tx)));

        var page = transactionService.getTransactionsForUser(userId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void getPendingApprovals_returnsOnlyPendingTransactions() {
        Transaction tx = Transaction.builder()
                .user(testUser).fromCurrency("USD").toCurrency("EUR")
                .amount(new BigDecimal("500.00")).convertedAmount(new BigDecimal("460.00"))
                .rate(new BigDecimal("0.92")).status(TransactionStatus.PENDING_APPROVAL).build();
        ReflectionTestUtils.setField(tx, "transactionId", UUID.randomUUID());

        when(transactionRepository.findByStatusOrderByTransactionDateDesc(TransactionStatus.PENDING_APPROVAL))
                .thenReturn(List.of(tx));

        List<TransactionResponse> result = transactionService.getPendingApprovals();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void getAllTransactions_returnsPaginatedResults() {
        Transaction tx = Transaction.builder()
                .user(testUser).fromCurrency("USD").toCurrency("EUR")
                .amount(new BigDecimal("50.00")).convertedAmount(new BigDecimal("46.00"))
                .rate(new BigDecimal("0.92")).status(TransactionStatus.APPROVED).build();
        ReflectionTestUtils.setField(tx, "transactionId", UUID.randomUUID());

        when(transactionRepository.findAllByOrderByTransactionDateDesc(any()))
                .thenReturn(new PageImpl<>(List.of(tx)));

        var page = transactionService.getAllTransactions(PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
