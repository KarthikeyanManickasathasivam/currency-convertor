package com.exchange.controller;

import com.exchange.dto.response.DashboardStatsResponse;
import com.exchange.dto.response.TransactionResponse;
import com.exchange.dto.response.UserResponse;
import com.exchange.filter.JwtAuthFilter;
import com.exchange.filter.RateLimitFilter;
import com.exchange.model.Log;
import com.exchange.model.User;
import com.exchange.model.enums.Role;
import com.exchange.repository.AppSettingRepository;
import com.exchange.repository.LogRepository;
import com.exchange.repository.TransactionRepository;
import com.exchange.repository.UserRepository;
import com.exchange.service.AppSettingService;
import com.exchange.service.JwtService;
import com.exchange.service.LogService;
import com.exchange.service.TokenBlacklistService;
import com.exchange.service.TransactionService;
import com.exchange.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AdminControllerOperationsTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean TransactionService transactionService;
    @MockBean AppSettingService appSettingService;
    @MockBean AppSettingRepository appSettingRepository;
    @MockBean LogRepository logRepository;
    @MockBean UserRepository userRepository;
    @MockBean TransactionRepository transactionRepository;
    @MockBean JwtService jwtService;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean LogService logService;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean RateLimitFilter rateLimitFilter;

    private User adminUser;
    private UsernamePasswordAuthenticationToken adminAuth;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .userId(UUID.randomUUID())
                .username("admin")
                .email("admin@example.com")
                .role(Role.ADMIN)
                .isActive(true)
                .build();
        adminAuth = new UsernamePasswordAuthenticationToken(
                adminUser, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private RequestPostProcessor withAdmin() {
        return request -> {
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(adminAuth);
            SecurityContextHolder.setContext(ctx);
            return request;
        };
    }

    // ── Dashboard ──────────────────────────────────────────────────────────

    @Test
    void getDashboard_returns200WithStats() throws Exception {
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countActive()).thenReturn(8L);
        when(transactionRepository.countAll()).thenReturn(50L);
        when(transactionRepository.countByStatus(any())).thenReturn(3L);
        when(transactionRepository.findTopFromCurrencies(any()))
                .thenReturn(List.of(new Object[]{"USD"}, new Object[]{"EUR"}));

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.activeUsers").value(8))
                .andExpect(jsonPath("$.totalTransactions").value(50))
                .andExpect(jsonPath("$.pendingApprovals").value(3))
                .andExpect(jsonPath("$.topFromCurrencies[0]").value("USD"));
    }

    // ── User Management ────────────────────────────────────────────────────

    @Test
    void listUsers_returns200WithPage() throws Exception {
        UserResponse user = UserResponse.builder()
                .userId(UUID.randomUUID()).username("alice")
                .email("alice@example.com").role("USER").isActive(true).build();

        when(userService.getAllUsers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"));
    }

    @Test
    void getUser_existingId_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse user = UserResponse.builder()
                .userId(id).username("alice").email("alice@example.com")
                .role("USER").isActive(true).build();

        when(userService.getUserById(id)).thenReturn(user);

        mockMvc.perform(get("/api/admin/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id.toString()));
    }

    @Test
    void deactivateUser_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(userService).deactivateUser(id);

        mockMvc.perform(delete("/api/admin/users/" + id))
                .andExpect(status().isNoContent());

        verify(userService).deactivateUser(id);
    }

    // ── Transaction Approvals ─────────────────────────────────────────────

    @Test
    void listAllTransactions_returns200WithPage() throws Exception {
        TransactionResponse tx = TransactionResponse.builder()
                .transactionId(UUID.randomUUID()).fromCurrency("USD")
                .toCurrency("EUR").amount(new BigDecimal("200.00"))
                .status("PENDING_APPROVAL").build();

        when(transactionService.getAllTransactions(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/admin/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    void listPending_returns200WithList() throws Exception {
        TransactionResponse tx = TransactionResponse.builder()
                .transactionId(UUID.randomUUID()).fromCurrency("USD")
                .toCurrency("EUR").amount(new BigDecimal("500.00"))
                .status("PENDING_APPROVAL").build();

        when(transactionService.getPendingApprovals()).thenReturn(List.of(tx));

        mockMvc.perform(get("/api/admin/transactions/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    void approveTransaction_returns200() throws Exception {
        UUID txId = UUID.randomUUID();
        TransactionResponse approved = TransactionResponse.builder()
                .transactionId(txId).status("APPROVED").build();

        when(transactionService.approve(eq(txId), any())).thenReturn(approved);

        mockMvc.perform(post("/api/admin/transactions/" + txId + "/approve")
                        .with(withAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectTransaction_returns200() throws Exception {
        UUID txId = UUID.randomUUID();
        TransactionResponse rejected = TransactionResponse.builder()
                .transactionId(txId).status("REJECTED").build();

        when(transactionService.reject(eq(txId), any(), any())).thenReturn(rejected);

        mockMvc.perform(post("/api/admin/transactions/" + txId + "/reject")
                        .with(withAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Suspicious activity"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void rejectTransaction_noBody_returns200() throws Exception {
        UUID txId = UUID.randomUUID();
        TransactionResponse rejected = TransactionResponse.builder()
                .transactionId(txId).status("REJECTED").build();

        when(transactionService.reject(eq(txId), any(), isNull())).thenReturn(rejected);

        mockMvc.perform(post("/api/admin/transactions/" + txId + "/reject")
                        .with(withAdmin()))
                .andExpect(status().isOk());
    }

    // ── Logs ──────────────────────────────────────────────────────────────

    @Test
    void getLogs_returns200WithPage() throws Exception {
        Log log = Log.builder()
                .logId(1L).event("USER_REGISTERED").eventType("AUTH")
                .timestamp(LocalDateTime.now()).build();

        when(logRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].event").value("USER_REGISTERED"));
    }

    @Test
    void getLogsByType_returns200WithPage() throws Exception {
        Log log = Log.builder()
                .logId(2L).event("LOGIN_ATTEMPT").eventType("AUTH")
                .timestamp(LocalDateTime.now()).build();

        when(logRepository.findByEventTypeOrderByTimestampDesc(eq("AUTH"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/logs/type/AUTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("AUTH"));
    }
}
