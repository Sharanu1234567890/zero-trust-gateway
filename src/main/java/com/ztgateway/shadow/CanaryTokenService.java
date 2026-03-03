package com.ztgateway.shadow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Generates and tracks canary tokens — unique identifiers injected into
 * responses that can detect if data has been exfiltrated.
 * If a canary token appears in an unexpected request, it means data leaked.
 */
@Service
public class CanaryTokenService {

    private static final Logger log = LoggerFactory.getLogger(CanaryTokenService.class);
    private static final String PREFIX = "canary:";
    private static final Duration TTL = Duration.ofDays(90);

    private final ReactiveStringRedisTemplate redisTemplate;

    public CanaryTokenService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generate a new canary token linked to a specific user and context.
     */
    public Mono<String> generate(String userId, String context) {
        String token = "canary-" + UUID.randomUUID();
        String value = userId + "|" + context + "|" + System.currentTimeMillis();

        return redisTemplate.opsForValue().set(PREFIX + token, value, TTL)
                .thenReturn(token)
                .doOnSuccess(t -> log.info("Generated canary token for user={}: {}", userId, t));
    }

    /**
     * Check if a value contains a known canary token. Returns the token if found.
     */
    public Mono<String> checkForCanary(String payload) {
        if (payload == null || !payload.contains("canary-")) {
            return Mono.empty();
        }

        // Extract potential canary tokens from the payload
        int idx = payload.indexOf("canary-");
        if (idx >= 0) {
            // canary-UUID is 43 chars long (6 + 1 + 36)
            int end = Math.min(idx + 43, payload.length());
            String potentialToken = payload.substring(idx, end);

            return redisTemplate.hasKey(PREFIX + potentialToken)
                    .flatMap(exists -> {
                        if (Boolean.TRUE.equals(exists)) {
                            log.error("CANARY TOKEN DETECTED in request payload: {}", potentialToken);
                            return Mono.just(potentialToken);
                        }
                        return Mono.empty();
                    });
        }
        return Mono.empty();
    }
}
