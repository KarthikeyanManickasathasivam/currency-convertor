package com.exchange.exception;

import com.exchange.controller.ExchangeRateController;
import com.exchange.filter.JwtAuthFilter;
import com.exchange.filter.RateLimitFilter;
import com.exchange.repository.UserRepository;
import com.exchange.service.ExchangeRateService;
import com.exchange.service.JwtService;
import com.exchange.service.LogService;
import com.exchange.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExchangeRateController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ExchangeRateService rateService;
    @MockBean JwtService jwtService;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean LogService logService;
    @MockBean UserRepository userRepository;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean RateLimitFilter rateLimitFilter;

    // Convenience: GET /api/rates/{from}/{to} calls rateService.getRateResponse(...)
    // We use it as the trigger for every exception type.

    @Test
    void resourceNotFound_returns404WithMessage() throws Exception {
        when(rateService.getRateResponse("USD", "EUR"))
                .thenThrow(new ResourceNotFoundException("Exchange rate not found"));

        mockMvc.perform(get("/api/rates/USD/EUR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Exchange rate not found"));
    }

    @Test
    void duplicateResource_returns409() throws Exception {
        when(rateService.getRateResponse("USD", "EUR"))
                .thenThrow(new DuplicateResourceException("Rate already exists"));

        mockMvc.perform(get("/api/rates/USD/EUR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidOtp_returns401() throws Exception {
        when(rateService.getRateResponse("USD", "EUR"))
                .thenThrow(new InvalidOtpException("Invalid or expired OTP"));

        mockMvc.perform(get("/api/rates/USD/EUR"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
    }

    @Test
    void externalApiException_returns503() throws Exception {
        when(rateService.getRateResponse("USD", "EUR"))
                .thenThrow(new ExternalApiException("Primary API down"));

        mockMvc.perform(get("/api/rates/USD/EUR"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Exchange rate service temporarily unavailable"));
    }

    @Test
    void transactionNotFound_returns404() throws Exception {
        when(rateService.getRateResponse("USD", "EUR"))
                .thenThrow(new TransactionNotFoundException("Transaction not found: abc"));

        mockMvc.perform(get("/api/rates/USD/EUR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found: abc"));
    }

    @Test
    void disabledException_returns403() throws Exception {
        when(rateService.getRateResponse("USD", "EUR"))
                .thenThrow(new DisabledException("Account is disabled"));

        mockMvc.perform(get("/api/rates/USD/EUR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is disabled"));
    }

    @Test
    void accessDeniedException_returns403() throws Exception {
        when(rateService.getRateResponse("USD", "EUR"))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/rates/USD/EUR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void genericException_returns500() throws Exception {
        when(rateService.getRateResponse("USD", "EUR"))
                .thenThrow(new RuntimeException("Unexpected internal failure"));

        mockMvc.perform(get("/api/rates/USD/EUR"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void errorResponse_containsPathAndTimestamp() throws Exception {
        when(rateService.getRateResponse("USD", "EUR"))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/api/rates/USD/EUR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/rates/USD/EUR"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
