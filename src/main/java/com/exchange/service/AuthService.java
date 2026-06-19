package com.exchange.service;

import com.exchange.dto.request.LoginRequest;
import com.exchange.dto.request.MfaVerifyRequest;
import com.exchange.dto.request.RegisterRequest;
import com.exchange.dto.response.AuthResponse;
import com.exchange.dto.response.UserResponse;
import com.exchange.exception.DuplicateResourceException;
import com.exchange.exception.InvalidOtpException;
import com.exchange.exception.ResourceNotFoundException;
import com.exchange.model.User;
import com.exchange.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final EmailService emailService;

    @Value("${app.mfa.bypass-emails:}")
    private List<String> mfaBypassEmails;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: {}", saved.getEmail());
        return toResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isActive()) {
            throw new BadCredentialsException("Account is disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (mfaBypassEmails.contains(request.getEmail())) {
            log.warn("MFA bypassed for {} (POC mode) — issuing token directly", request.getEmail());
            otpService.storeOtp(request.getEmail(), "000000");
            return AuthResponse.builder()
                    .accessToken(jwtService.generateAccessToken(user))
                    .tokenType("Bearer")
                    .expiresIn(jwtService.getExpiration())
                    .role(user.getRole().name())
                    .build();
        }

        String otp = otpService.generateAndStore(request.getEmail());
        emailService.sendOtp(request.getEmail(), otp);
        log.info("MFA OTP sent to: {}", request.getEmail());
        return null;
    }

    public AuthResponse verifyMfa(MfaVerifyRequest request) {
        if (!mfaBypassEmails.contains(request.getEmail())
                && !otpService.verify(request.getEmail(), request.getOtp())) {
            throw new InvalidOtpException("Invalid or expired OTP");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String accessToken = jwtService.generateAccessToken(user);
        log.info("JWT issued for: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpiration())
                .role(user.getRole().name())
                .build();
    }

    public String generateRefreshToken(User user) {
        return jwtService.generateRefreshToken(user);
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new InvalidOtpException("Invalid or expired refresh token");
        }

        String email = jwtService.extractUsername(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new InvalidOtpException("Invalid or expired refresh token");
        }

        String accessToken = jwtService.generateAccessToken(user);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpiration())
                .role(user.getRole().name())
                .build();
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getDisplayUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
