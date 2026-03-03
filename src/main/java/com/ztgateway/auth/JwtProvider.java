package com.ztgateway.auth;

import com.ztgateway.config.AppConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Creates signed JWT tokens for authenticated users.
 */
@Component
public class JwtProvider {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final String issuer;

    public JwtProvider(AppConfig appConfig) {
        this.signingKey = Keys.hmacShaKeyFor(
                appConfig.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = appConfig.getJwt().getExpirationMs();
        this.issuer = appConfig.getJwt().getIssuer();
    }

    public String generateToken(String userId, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .issuer(issuer)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    public SecretKey getSigningKey() {
        return signingKey;
    }
}
