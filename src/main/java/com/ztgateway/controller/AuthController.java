package com.ztgateway.controller;

import com.ztgateway.auth.JwtProvider;
import com.ztgateway.auth.TokenBlacklistService;
import com.ztgateway.auth.JwtValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Authentication endpoints for token issuance and revocation.
 * In production, this would integrate with a real user store / IdP.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtProvider jwtProvider;
    private final JwtValidator jwtValidator;
    private final TokenBlacklistService blacklistService;

    public AuthController(JwtProvider jwtProvider,
                          JwtValidator jwtValidator,
                          TokenBlacklistService blacklistService) {
        this.jwtProvider = jwtProvider;
        this.jwtValidator = jwtValidator;
        this.blacklistService = blacklistService;
    }

    /**
     * Issue a JWT token. In production, this would validate credentials first.
     */
    @PostMapping("/token")
    public Mono<ResponseEntity<Map<String, Object>>> issueToken(@RequestBody Map<String, Object> request) {
        String userId = (String) request.get("userId");
        if (userId == null || userId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "userId is required")));
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) request.getOrDefault("roles", List.of("USER"));

        String token = jwtProvider.generateToken(userId, roles);

        return Mono.just(ResponseEntity.ok(Map.of(
                "token", token,
                "type", "Bearer",
                "userId", userId,
                "roles", roles
        )));
    }

    /**
     * Revoke a token (logout). Adds the token's JTI to the blacklist.
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Bearer token required")));
        }

        String token = authHeader.substring(7);
        var tokenData = jwtValidator.validate(token);

        if (tokenData.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid token")));
        }

        String jti = tokenData.get().jti();
        // Blacklist for 1 hour (should match token lifetime)
        return blacklistService.blacklist(jti, Duration.ofHours(1))
                .map(ok -> ResponseEntity.ok(Map.of("status", "logged out", "jti", jti)));
    }
}
