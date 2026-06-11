package com.exchange.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    @Value("${app.jwt.expiration:900000}")
    private long expiration;

    @Value("${app.jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @Value("${app.jwt.issuer:currency-exchange-service}")
    private String issuer;

    @Value("${app.jwt.private-key:}")
    private String privateKeyBase64;

    @Value("${app.jwt.public-key:}")
    private String publicKeyBase64;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @jakarta.annotation.PostConstruct
    public void init() throws Exception {
        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
            log.warn("JWT keys not configured — auto-generating RSA key pair for local dev");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            privateKey = pair.getPrivate();
            publicKey = pair.getPublic();
            log.info("RSA keys generated. Public key (base64): {}",
                    Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        } else if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            throw new IllegalStateException("JWT_PUBLIC_KEY must be set when JWT_PRIVATE_KEY is provided");
        } else {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            byte[] privBytes = decodeKey(privateKeyBase64);
            byte[] pubBytes = decodeKey(publicKeyBase64);
            privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            publicKey = factory.generatePublic(new X509EncodedKeySpec(pubBytes));
        }
    }

    public String generateAccessToken(UserDetails user) {
        return buildToken(new HashMap<>(), user, expiration);
    }

    public String generateRefreshToken(UserDetails user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return buildToken(claims, user, refreshExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails user, long tokenExpiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(user.getUsername())
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + tokenExpiration))
                .signWith(privateKey)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails user) {
        try {
            final String username = extractUsername(token);
            return username.equals(user.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public long getExpiration() {
        return expiration;
    }

    /**
     * Decodes a key that may be in one of these formats:
     * 1. Bare base64 DER bytes (e.g. "MIIEvAIBADANBg...") — recommended for EB env vars
     * 2. PEM block with headers (e.g. "-----BEGIN PRIVATE KEY-----\nMIIEv...")
     * 3. Base64-encoded PEM block (e.g. the entire PEM file base64'd)
     */
    private byte[] decodeKey(String key) {
        String trimmed = key.trim();

        // Case 2: raw PEM with visible headers
        if (trimmed.startsWith("-----")) {
            return extractPemBody(trimmed);
        }

        // Attempt outer base64 decode
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(trimmed.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT key is not valid base64 and not a PEM block", e);
        }

        // Case 3: the decoded bytes are themselves a PEM text (base64-of-PEM)
        String decodedStr = new String(decoded, java.nio.charset.StandardCharsets.UTF_8).trim();
        if (decodedStr.startsWith("-----")) {
            return extractPemBody(decodedStr);
        }

        // Case 1: decoded bytes are already raw DER
        return decoded;
    }

    private byte[] extractPemBody(String pem) {
        StringBuilder sb = new StringBuilder();
        for (String line : pem.split("\\r?\\n")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("-----")) {
                sb.append(trimmedLine);
            }
        }
        return Base64.getDecoder().decode(sb.toString());
    }
}
