package com.exchange.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface EmailService {
    void sendOtp(String to, String otp);
    void sendApprovalRequiredNotification(String adminEmail, UUID transactionId, String userEmail, BigDecimal amount, String fromCurrency, String toCurrency);
    void sendApprovalNotification(String userEmail, UUID transactionId, BigDecimal convertedAmount, String toCurrency);
    void sendRejectionNotification(String userEmail, UUID transactionId, String reason);
}
