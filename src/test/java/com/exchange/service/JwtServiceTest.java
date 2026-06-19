package com.exchange.service;

import com.exchange.model.User;
import com.exchange.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
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

    // ── decodeKey branch coverage ─────────────────────────────────────────

    private KeyPair freshRsaKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private String toPem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----";
    }

    @Test
    void init_withRawPemHeaders_parsesKeysAndGeneratesValidToken() throws Exception {
        KeyPair pair = freshRsaKeyPair();
        String privatePem = toPem("PRIVATE KEY", pair.getPrivate().getEncoded());
        String publicPem  = toPem("PUBLIC KEY",  pair.getPublic().getEncoded());

        JwtService svc = new JwtService();
        ReflectionTestUtils.setField(svc, "privateKeyBase64", privatePem);
        ReflectionTestUtils.setField(svc, "publicKeyBase64",  publicPem);
        ReflectionTestUtils.setField(svc, "expiration",        900_000L);
        ReflectionTestUtils.setField(svc, "refreshExpiration", 604_800_000L);
        ReflectionTestUtils.setField(svc, "issuer",            "test");
        svc.init();

        String token = svc.generateAccessToken(testUser);
        assertThat(svc.extractUsername(token)).isEqualTo(testUser.getUsername());
        assertThat(svc.isTokenValid(token, testUser)).isTrue();
    }

    @Test
    void init_withBareDerBase64_parsesKeysAndGeneratesValidToken() throws Exception {
        KeyPair pair = freshRsaKeyPair();
        String privateDer = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String publicDer  = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        JwtService svc = new JwtService();
        ReflectionTestUtils.setField(svc, "privateKeyBase64", privateDer);
        ReflectionTestUtils.setField(svc, "publicKeyBase64",  publicDer);
        ReflectionTestUtils.setField(svc, "expiration",        900_000L);
        ReflectionTestUtils.setField(svc, "refreshExpiration", 604_800_000L);
        ReflectionTestUtils.setField(svc, "issuer",            "test");
        svc.init();

        String token = svc.generateAccessToken(testUser);
        assertThat(svc.isTokenValid(token, testUser)).isTrue();
    }

    @Test
    void init_withBase64EncodedPem_parsesKeysAndGeneratesValidToken() throws Exception {
        KeyPair pair = freshRsaKeyPair();
        // Wrap entire PEM block in another layer of base64 (case 3 in decodeKey)
        String privatePem    = toPem("PRIVATE KEY", pair.getPrivate().getEncoded());
        String publicPem     = toPem("PUBLIC KEY",  pair.getPublic().getEncoded());
        String privateB64Pem = Base64.getEncoder().encodeToString(privatePem.getBytes());
        String publicB64Pem  = Base64.getEncoder().encodeToString(publicPem.getBytes());

        JwtService svc = new JwtService();
        ReflectionTestUtils.setField(svc, "privateKeyBase64", privateB64Pem);
        ReflectionTestUtils.setField(svc, "publicKeyBase64",  publicB64Pem);
        ReflectionTestUtils.setField(svc, "expiration",        900_000L);
        ReflectionTestUtils.setField(svc, "refreshExpiration", 604_800_000L);
        ReflectionTestUtils.setField(svc, "issuer",            "test");
        svc.init();

        String token = svc.generateAccessToken(testUser);
        assertThat(svc.isTokenValid(token, testUser)).isTrue();
    }

    @Test
    void init_privateKeySetButPublicKeyMissing_throwsIllegalState() {
        JwtService svc = new JwtService();
        ReflectionTestUtils.setField(svc, "privateKeyBase64", "some-non-blank-key");
        ReflectionTestUtils.setField(svc, "publicKeyBase64",  "");
        ReflectionTestUtils.setField(svc, "expiration",        900_000L);
        ReflectionTestUtils.setField(svc, "refreshExpiration", 604_800_000L);
        ReflectionTestUtils.setField(svc, "issuer",            "test");

        assertThatThrownBy(svc::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PUBLIC_KEY");
    }
}
