package com.exchange.controller;

import com.exchange.dto.request.LoginRequest;
import com.exchange.dto.request.MfaVerifyRequest;
import com.exchange.dto.request.RegisterRequest;
import com.exchange.dto.response.AuthResponse;
import com.exchange.dto.response.UserResponse;
import com.exchange.model.User;
import com.exchange.repository.UserRepository;
import com.exchange.service.AuthService;
import com.exchange.service.JwtService;
import com.exchange.service.LogService;
import com.exchange.service.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@Tag(name = "Authentication", description = "Register, login, MFA, token refresh")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    private final AuthService authService;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;
    private final LogService logService;
    private final UserRepository userRepository;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        UserResponse user = authService.register(request);
        logService.log("USER_REGISTERED", "AUTH", user.getUserId(),
                getIp(httpRequest), Map.of("email", user.getEmail()));
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @Operation(summary = "Login — sends OTP to registered email, or returns token directly for MFA-bypass accounts")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthResponse auth = authService.login(request);
        logService.log("LOGIN_ATTEMPT", "AUTH", null,
                getIp(httpRequest), Map.of("email", request.getEmail()));
        if (auth != null) {
            userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
                String refreshToken = authService.generateRefreshToken(user);
                httpResponse.addCookie(buildRefreshCookie(refreshToken));
            });
            return ResponseEntity.ok(auth);
        }
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Verify MFA OTP — returns JWT access token and sets refresh cookie")
    @PostMapping("/mfa/verify")
    public ResponseEntity<AuthResponse> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletResponse response,
            HttpServletRequest httpRequest) {
        AuthResponse auth = authService.verifyMfa(request);

        // Load user to generate refresh token and set HTTP-only cookie
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String refreshToken = authService.generateRefreshToken(user);
            Cookie cookie = buildRefreshCookie(refreshToken);
            response.addCookie(cookie);
        });

        logService.log("MFA_VERIFIED", "AUTH", null,
                getIp(httpRequest), Map.of("email", request.getEmail()));
        return ResponseEntity.ok(auth);
    }

    @Operation(summary = "Refresh access token using refresh cookie")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    @Operation(summary = "Logout — blacklists current token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal UserDetails user) {
        String token = extractBearerToken(request);
        if (token != null) {
            long expiry = jwtService.extractExpiration(token).getTime() - System.currentTimeMillis();
            blacklistService.blacklist(token, Math.max(expiry, 0));
        }
        clearRefreshCookie(response);
        logService.log("LOGOUT", "AUTH", null, getIp(request),
                Map.of("email", user != null ? user.getUsername() : "unknown"));
        return ResponseEntity.noContent().build();
    }

    private Cookie buildRefreshCookie(String token) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) (jwtService.getExpiration() / 1000 * 48)); // 7-day TTL
        return cookie;
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String getIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
