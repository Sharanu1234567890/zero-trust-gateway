package com.ztgateway.threat.features;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * Creates a fingerprint from request headers (User-Agent, Accept, Accept-Language, etc.)
 * and compares it to the user's known fingerprint to detect anomalies.
 */
@Component
public class HeaderFingerprintFeature {

    private static final String PREFIX = "fingerprint:";
    private static final Duration TTL = Duration.ofDays(7);

    // Headers to include in fingerprint
    private static final List<String> FINGERPRINT_HEADERS = List.of(
            HttpHeaders.USER_AGENT,
            HttpHeaders.ACCEPT,
            HttpHeaders.ACCEPT_LANGUAGE,
            HttpHeaders.ACCEPT_ENCODING
    );

    private final ReactiveStringRedisTemplate redisTemplate;

    public HeaderFingerprintFeature(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Compute a fingerprint hash for the current request headers,
     * compare with stored fingerprint, and return similarity (0.0 - 1.0).
     */
    public Mono<Float> extract(String userId, HttpHeaders headers) {
        String currentFingerprint = computeFingerprint(headers);
        String key = PREFIX + userId;

        return redisTemplate.opsForValue().get(key)
                .flatMap(stored -> {
                    float similarity = stored.equals(currentFingerprint) ? 1.0f : 0.0f;
                    // Update the stored fingerprint (gradual rotation)
                    return redisTemplate.opsForValue().set(key, currentFingerprint, TTL)
                            .thenReturn(similarity);
                })
                .switchIfEmpty(
                        // First time seeing this user — store and return 1.0 (no anomaly)
                        redisTemplate.opsForValue().set(key, currentFingerprint, TTL)
                                .thenReturn(1.0f)
                )
                .onErrorReturn(1.0f); // Fail open
    }

    private String computeFingerprint(HttpHeaders headers) {
        StringBuilder sb = new StringBuilder();
        for (String headerName : FINGERPRINT_HEADERS) {
            String value = headers.getFirst(headerName);
            sb.append(headerName).append("=").append(value != null ? value : "").append("|");
        }
        return sha256(sb.toString());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
