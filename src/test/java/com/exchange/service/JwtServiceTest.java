package com.exchange.service;

import com.exchange.model.User;
import com.exchange.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();
        // Leave keys blank — JwtService auto-generates an RSA pair for local dev
        ReflectionTestUtils.setField(jwtService, "privateKeyBase64", "");
        ReflectionTestUtils.setField(jwtService, "publicKeyBase64", "");
        ReflectionTestUtils.setField(jwtService, "expiration", 900_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604_800_000L);
        ReflectionTestUtils.setField(jwtService, "issuer", "test-issuer");
        jwtService.init();

        testUser = User.builder()
                .userId(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$2a$12$hash")
                .role(Role.USER)
                .isActive(true)
                .build();
    }

    @Test
    void generateAccessToken_returnsNonBlankToken() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(token).isNotBlank();
    }

    @Test
    void extractUsername_returnsSubject() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.extractUsername(token)).isEqualTo(testUser.getUsername());
    }

    @Test
    void isTokenValid_validTokenAndMatchingUser_returnsTrue() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.isTokenValid(token, testUser)).isTrue();
    }

    @Test
    void isTokenValid_wrongUsername_returnsFalse() {
        String token = jwtService.generateAccessToken(testUser);

        User anotherUser = User.builder()
                .userId(UUID.randomUUID())
                .username("otheruser")
                .email("other@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .isActive(true)
                .build();

        assertThat(jwtService.isTokenValid(token, anotherUser)).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() throws Exception {
        JwtService shortLivedService = new JwtService();
        ReflectionTestUtils.setField(shortLivedService, "privateKeyBase64", "");
        ReflectionTestUtils.setField(shortLivedService, "publicKeyBase64", "");
        ReflectionTestUtils.setField(shortLivedService, "expiration", -1000L); // already expired
        ReflectionTestUtils.setField(shortLivedService, "refreshExpiration", 604_800_000L);
        ReflectionTestUtils.setField(shortLivedService, "issuer", "test-issuer");
        shortLivedService.init();

        String expiredToken = shortLivedService.generateAccessToken(testUser);
        assertThat(jwtService.isTokenValid(expiredToken, testUser)).isFalse();
    }

    @Test
    void isTokenValid_tamperedToken_returnsFalse() {
        String token = jwtService.generateAccessToken(testUser);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.isTokenValid(tampered, testUser)).isFalse();
    }

    @Test
    void generateRefreshToken_isValidToken() {
        String token = jwtService.generateRefreshToken(testUser);
        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo(testUser.getUsername());
    }

    @Test
    void getExpiration_returnsConfiguredValue() {
        assertThat(jwtService.getExpiration()).isEqualTo(900_000L);
    }
}
