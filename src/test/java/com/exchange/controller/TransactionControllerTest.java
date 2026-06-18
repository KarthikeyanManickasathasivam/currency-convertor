package com.exchange.controller;

import com.exchange.dto.request.ConversionRequest;
import com.exchange.dto.response.TransactionResponse;
import com.exchange.filter.JwtAuthFilter;
import com.exchange.filter.RateLimitFilter;
import com.exchange.model.User;
import com.exchange.model.enums.Role;
import com.exchange.service.JwtService;
import com.exchange.service.LogService;
import com.exchange.service.TokenBlacklistService;
import com.exchange.service.TransactionService;
import com.exchange.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.AfterEach;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class TransactionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TransactionService transactionService;
    @MockBean JwtService jwtService;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean LogService logService;
    @MockBean UserRepository userRepository;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean RateLimitFilter rateLimitFilter;

    private User testUser;
    private UsernamePasswordAuthenticationToken userAuth;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .role(Role.USER)
                .isActive(true)
                .build();
        userAuth = new UsernamePasswordAuthenticationToken(
                testUser, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // With addFilters=false the SecurityContextHolderFilter never runs, so
    // @AuthenticationPrincipal reads SecurityContextHolder directly. We set
    // it as a RequestPostProcessor so it is populated on the same thread before
    // the DispatcherServlet processes the request.
    private RequestPostProcessor withUser(UsernamePasswordAuthenticationToken auth) {
        return request -> {
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);
            return request;
        };
    }

    @Test
    void convert_validRequest_returns201() throws Exception {
        ConversionRequest req = new ConversionRequest();
        req.setFromCurrency("USD");
        req.setToCurrency("EUR");
        req.setAmount(new BigDecimal("50.00"));

        TransactionResponse response = TransactionResponse.builder()
                .transactionId(UUID.randomUUID())
                .userId(testUser.getUserId())
                .fromCurrency("USD")
                .toCurrency("EUR")
                .amount(new BigDecimal("50.00"))
                .convertedAmount(new BigDecimal("46.00"))
                .rate(new BigDecimal("0.92"))
                .status("APPROVED")
                .build();

        when(transactionService.convert(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/transactions")
                        .with(withUser(userAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromCurrency").value("USD"))
                .andExpect(jsonPath("$.toCurrency").value("EUR"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.convertedAmount").value(46.00));
    }

    @Test
    void convert_missingAmount_returns400() throws Exception {
        ConversionRequest req = new ConversionRequest();
        req.setFromCurrency("USD");
        req.setToCurrency("EUR");
        // amount intentionally omitted

        mockMvc.perform(post("/api/transactions")
                        .with(withUser(userAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void convert_invalidCurrencyCode_returns400() throws Exception {
        ConversionRequest req = new ConversionRequest();
        req.setFromCurrency("usd"); // lowercase — fails @Pattern([A-Z]{3})
        req.setToCurrency("EUR");
        req.setAmount(new BigDecimal("50.00"));

        mockMvc.perform(post("/api/transactions")
                        .with(withUser(userAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyTransactions_returns200WithPage() throws Exception {
        TransactionResponse tx = TransactionResponse.builder()
                .transactionId(UUID.randomUUID())
                .userId(testUser.getUserId())
                .fromCurrency("USD")
                .toCurrency("EUR")
                .amount(new BigDecimal("50.00"))
                .status("APPROVED")
                .build();

        when(transactionService.getTransactionsForUser(any(), any()))
                .thenReturn(new PageImpl<>(List.of(tx), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/transactions")
                        .with(withUser(userAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"));
    }
}
