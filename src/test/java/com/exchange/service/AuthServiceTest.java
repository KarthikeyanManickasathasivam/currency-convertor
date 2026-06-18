package com.exchange.service;

import com.exchange.dto.request.LoginRequest;
import com.exchange.dto.request.MfaVerifyRequest;
import com.exchange.dto.request.RegisterRequest;
import com.exchange.dto.response.AuthResponse;
import com.exchange.dto.response.UserResponse;
import com.exchange.exception.DuplicateResourceException;
import com.exchange.exception.InvalidOtpException;
import com.exchange.model.User;
import com.exchange.model.enums.Role;
import com.exchange.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private OtpService otpService;
    @Mock private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "mfaBypassEmails", List.of());

        testUser = User.builder()
                .userId(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$2a$12$hashed")
                .role(Role.USER)
                .isActive(true)
                .build();
    }

    @Test
    void register_success() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        req.setPassword("Password1!");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("$2a$12$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u = User.builder()
                    .userId(UUID.randomUUID())
                    .username(u.getDisplayUsername())
                    .email(u.getEmail())
                    .passwordHash(u.getPasswordHash())
                    .role(Role.USER)
                    .isActive(true)
                    .build();
            return u;
        });

        UserResponse result = authService.register(req);

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getUsername()).isEqualTo("newuser");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateEmail_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("another");
        req.setEmail("test@example.com");
        req.setPassword("Password1!");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void login_success_sendsOtp() {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("correct-password");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correct-password", testUser.getPasswordHash())).thenReturn(true);
        when(otpService.generateAndStore("test@example.com")).thenReturn("123456");

        authService.login(req);

        verify(otpService).generateAndStore("test@example.com");
        verify(emailService).sendOtp("test@example.com", "123456");
    }

    @Test
    void login_wrongPassword_throws() {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("wrong-password");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong-password", testUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verifyMfa_success_returnsJwt() {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setEmail("test@example.com");
        req.setOtp("123456");

        when(otpService.verify("test@example.com", "123456")).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(testUser)).thenReturn("jwt-token-abc");
        when(jwtService.getExpiration()).thenReturn(900000L);

        AuthResponse result = authService.verifyMfa(req);

        assertThat(result.getAccessToken()).isEqualTo("jwt-token-abc");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(result.getRole()).isEqualTo("USER");
    }

    @Test
    void verifyMfa_invalidOtp_throws() {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setEmail("test@example.com");
        req.setOtp("000000");

        when(otpService.verify("test@example.com", "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyMfa(req))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid or expired OTP");
    }
}
