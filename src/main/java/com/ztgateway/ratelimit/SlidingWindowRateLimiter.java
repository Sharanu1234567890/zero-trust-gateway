package com.ztgateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import org.springframework.data.domain.Range;

import java.time.Instant;
import java.util.List;

/**
 * Sliding window rate limiter backed by Redis sorted sets.
 * Uses a Lua script for atomic check-and-increment.
 *
 * Algorithm:
 *   - Each request is added to a sorted set with score = current timestamp
 *   - Expired entries (older than window) are removed
 *   - Count of remaining entries determines if limit is exceeded
 */
@Service
public class SlidingWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);
    private static final String KEY_PREFIX = "ratelimit:sliding:";

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * Lua script for atomic sliding window rate limiting.
     * KEYS[1] = rate limit key
     * ARGV[1] = current timestamp (ms)
     * ARGV[2] = window start timestamp (ms)
     * ARGV[3] = max allowed requests
     * ARGV[4] = TTL for the key in seconds
     *
     * Returns: 1 if allowed, 0 if rate limited
     */
    private static final String RATE_LIMIT_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowStart = tonumber(ARGV[2])
            local maxRequests = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            
            -- Remove expired entries
            redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)
            
            -- Count current entries
            local currentCount = redis.call('ZCARD', key)
            
            if currentCount < maxRequests then
                -- Add current request
                redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))
                redis.call('EXPIRE', key, ttl)
                return 1
            else
                return 0
            end
            """;

    private final RedisScript<Long> script;

    public SlidingWindowRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = RedisScript.of(RATE_LIMIT_SCRIPT, Long.class);
    }

    /**
     * Check if the request is allowed under the rate limit.
     *
     * @param identifier user ID or IP address
     * @param maxRequests maximum requests allowed in the window
     * @param windowSeconds window size in seconds
     * @return Mono<Boolean> true if allowed, false if rate limited
     */
    public Mono<Boolean> isAllowed(String identifier, int maxRequests, int windowSeconds) {
        String key = KEY_PREFIX + identifier;
        long now = Instant.now().toEpochMilli();
        long windowStart = now - (windowSeconds * 1000L);

        return redisTemplate.execute(script,
                        List.of(key),
                        List.of(
                                String.valueOf(now),
                                String.valueOf(windowStart),
                                String.valueOf(maxRequests),
                                String.valueOf(windowSeconds + 10) // TTL slightly longer than window
                        ))
                .next()
                .map(result -> result == 1L)
                .defaultIfEmpty(true) // If Redis is unavailable, allow (fail-open for rate limiting)
                .onErrorResume(e -> {
                    log.error("Rate limiter error for {}: {} — failing open", identifier, e.getMessage());
                    return Mono.just(true);
                });
    }

    /**
     * Get the current request count for an identifier within the window.
     */
    public Mono<Long> getCurrentCount(String identifier, int windowSeconds) {
        String key = KEY_PREFIX + identifier;
        long windowStart = Instant.now().toEpochMilli() - (windowSeconds * 1000L);

        return redisTemplate.opsForZSet()
                .count(key, Range.closed((double) windowStart, Double.MAX_VALUE))
                .defaultIfEmpty(0L);
    }
}
