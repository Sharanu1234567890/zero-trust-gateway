package com.ztgateway.threat.features;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Tracks which routes a user has visited. Returns 1.0 if this is a new route
 * (never visited before), 0.0 if it's a known route.
 */
@Component
public class RouteGraphFeature {

    private static final String PREFIX = "routes:";
    private static final Duration TTL = Duration.ofDays(30);

    private final ReactiveStringRedisTemplate redisTemplate;

    public RouteGraphFeature(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if this route is new for the user, then record it.
     * Returns 1.0f for new routes, 0.0f for known routes.
     */
    public Mono<Float> extract(String userId, String path) {
        String key = PREFIX + userId;
        String normalizedPath = normalizePath(path);

        return redisTemplate.opsForSet().isMember(key, normalizedPath)
                .flatMap(isMember -> {
                    float result = Boolean.TRUE.equals(isMember) ? 0.0f : 1.0f;
                    // Add route to the user's known set
                    return redisTemplate.opsForSet().add(key, normalizedPath)
                            .then(redisTemplate.expire(key, TTL))
                            .thenReturn(result);
                })
                .onErrorReturn(0.0f); // Fail open: assume known route
    }

    /**
     * Normalize path by removing IDs/UUIDs to group similar routes.
     * /api/payments/12345/status → /api/payments/{id}/status
     */
    private String normalizePath(String path) {
        return path.replaceAll("/[0-9a-fA-F-]{8,}", "/{id}");
    }
}
