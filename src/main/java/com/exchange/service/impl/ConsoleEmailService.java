package com.exchange.service.impl;

import com.exchange.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@Profile("local")
public class ConsoleEmailService implements EmailService {

    @Override
    public void sendOtp(String to, String otp) {
        log.info("╔══════════════════════════════════════════════");
        log.info("║ [OTP EMAIL]  To: {}",  to);
        log.info("║ Subject: Your verification code");
        log.info("║ OTP: {}  (valid for 5 minutes)", otp);
        log.info("╚══════════════════════════════════════════════");
    }

    @Override
    public void sendApprovalRequiredNotification(String adminEmail, UUID transactionId,
            String userEmail, BigDecimal amount, String fromCurrency, String toCurrency) {
        log.info("╔══════════════════════════════════════════════");
        log.info("║ [APPROVAL REQUIRED]  To: {}", adminEmail);
        log.info("║ Transaction: {}", transactionId);
        log.info("║ User: {}  Amount: {} {} → {}", userEmail, amount, fromCurrency, toCurrency);
        log.info("╚══════════════════════════════════════════════");
    }

    @Override
    public void sendApprovalNotification(String userEmail, UUID transactionId,
            BigDecimal convertedAmount, String toCurrency) {
        log.info("╔══════════════════════════════════════════════");
        log.info("║ [TRANSACTION APPROVED]  To: {}", userEmail);
        log.info("║ Transaction: {}  Converted: {} {}", transactionId, convertedAmount, toCurrency);
        log.info("╚══════════════════════════════════════════════");
    }

    @Override
    public void sendRejectionNotification(String userEmail, UUID transactionId, String reason) {
        log.info("╔══════════════════════════════════════════════");
        log.info("║ [TRANSACTION REJECTED]  To: {}", userEmail);
        log.info("║ Transaction: {}  Reason: {}", transactionId, reason);
        log.info("╚══════════════════════════════════════════════");
    }
}
