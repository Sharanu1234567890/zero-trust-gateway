package com.ztgateway.auth;

import com.ztgateway.config.AppConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Validates JWT tokens: signature, expiration, issuer.
 */
@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    private final SecretKey signingKey;
    private final String issuer;

    public JwtValidator(AppConfig appConfig) {
        this.signingKey = Keys.hmacShaKeyFor(
                appConfig.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.issuer = appConfig.getJwt().getIssuer();
    }

    public record TokenData(String jti, String userId, List<String> roles) {}

    /**
     * Parse and validate the JWT. Returns empty if invalid.
     */
    public Optional<TokenData> validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String jti = claims.getId();

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            if (roles == null) {
                roles = List.of();
            }

            return Optional.of(new TokenData(jti, userId, roles));
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
            return Optional.empty();
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
