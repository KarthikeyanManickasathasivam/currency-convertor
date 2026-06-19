package com.exchange.controller;

import com.exchange.dto.request.LoginRequest;
import com.exchange.dto.request.MfaVerifyRequest;
import com.exchange.dto.request.RegisterRequest;
import com.exchange.dto.response.AuthResponse;
import com.exchange.dto.response.UserResponse;
import com.exchange.exception.InvalidOtpException;
import com.exchange.filter.JwtAuthFilter;
import com.exchange.filter.RateLimitFilter;
import com.exchange.model.User;
import com.exchange.model.enums.Role;
import com.exchange.service.AuthService;
import com.exchange.service.JwtService;
import com.exchange.service.LogService;
import com.exchange.service.TokenBlacklistService;
import com.exchange.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean JwtService jwtService;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean LogService logService;
    @MockBean UserRepository userRepository;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean RateLimitFilter rateLimitFilter;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .role(Role.USER)
                .isActive(true)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private RequestPostProcessor withUser(User user) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return request -> {
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);
            return request;
        };
    }

    @Test
    void register_validRequest_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setEmail("test@example.com");
        req.setPassword("Password1!");

        UserResponse response = UserResponse.builder()
                .userId(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .role("USER")
                .isActive(true)
                .build();

        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setEmail("not-an-email");
        req.setPassword("Password1!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void login_validRequest_returns202() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("Password1!");

        when(authService.login(any())).thenReturn(null);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());
    }

    @Test
    void login_mfaBypassAccount_returns200WithToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("Password1!");

        AuthResponse auth = AuthResponse.builder()
                .accessToken("bypass-jwt").tokenType("Bearer").expiresIn(900000L).role("USER").build();

        when(authService.login(any())).thenReturn(auth);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(authService.generateRefreshToken(testUser)).thenReturn("refresh-token");
        when(jwtService.getExpiration()).thenReturn(900000L);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("bypass-jwt"));
    }

    @Test
    void verifyMfa_validOtp_returns200WithToken() throws Exception {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setEmail("test@example.com");
        req.setOtp("123456");

        AuthResponse auth = AuthResponse.builder()
                .accessToken("jwt-token").tokenType("Bearer").expiresIn(900000L).role("USER").build();

        when(authService.verifyMfa(any())).thenReturn(auth);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(authService.generateRefreshToken(testUser)).thenReturn("refresh-token");
        when(jwtService.getExpiration()).thenReturn(900000L);

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void verifyMfa_invalidOtp_returns401() throws Exception {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setEmail("test@example.com");
        req.setOtp("000000");

        when(authService.verifyMfa(any()))
                .thenThrow(new InvalidOtpException("Invalid or expired OTP"));

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withValidCookie_returns200WithNewToken() throws Exception {
        AuthResponse auth = AuthResponse.builder()
                .accessToken("new-access-token").tokenType("Bearer").expiresIn(900000L).role("USER").build();

        when(authService.refreshToken("valid-refresh")).thenReturn(auth);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "valid-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    void refresh_noCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withBearerToken_returns204() throws Exception {
        when(jwtService.extractExpiration(anyString()))
                .thenReturn(new Date(System.currentTimeMillis() + 900_000L));

        mockMvc.perform(post("/api/auth/logout")
                        .with(withUser(testUser))
                        .header("Authorization", "Bearer some-jwt-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_noToken_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(withUser(testUser)))
                .andExpect(status().isNoContent());
    }
}
