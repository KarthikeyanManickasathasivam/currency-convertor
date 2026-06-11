package com.exchange.service.impl;

import com.exchange.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@Profile("aws")
@RequiredArgsConstructor
public class SesEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:no-reply@currency-exchange.com}")
    private String fromAddress;

    @Async
    @Override
    public void sendOtp(String to, String otp) {
        send(to, "Your Currency Exchange Verification Code",
                "Your one-time verification code is: " + otp + "\n\nThis code expires in 5 minutes.");
    }

    @Async
    @Override
    public void sendApprovalRequiredNotification(String adminEmail, UUID transactionId,
            String userEmail, BigDecimal amount, String fromCurrency, String toCurrency) {
        send(adminEmail, "Transaction Pending Approval — " + transactionId,
                String.format("A transaction requires your approval.%n%nTransaction ID: %s%nUser: %s%nAmount: %s %s → %s",
                        transactionId, userEmail, amount, fromCurrency, toCurrency));
    }

    @Async
    @Override
    public void sendApprovalNotification(String userEmail, UUID transactionId,
            BigDecimal convertedAmount, String toCurrency) {
        send(userEmail, "Transaction Approved — " + transactionId,
                String.format("Your transaction has been approved.%n%nTransaction ID: %s%nConverted Amount: %s %s",
                        transactionId, convertedAmount, toCurrency));
    }

    @Async
    @Override
    public void sendRejectionNotification(String userEmail, UUID transactionId, String reason) {
        send(userEmail, "Transaction Rejected — " + transactionId,
                String.format("Your transaction has been rejected.%n%nTransaction ID: %s%nReason: %s",
                        transactionId, reason));
    }

    private void send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
