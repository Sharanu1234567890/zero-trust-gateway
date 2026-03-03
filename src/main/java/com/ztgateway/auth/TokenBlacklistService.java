package com.ztgateway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Manages revoked/blacklisted JWT tokens in Redis.
 * When a user logs out, their token JTI is added here.
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String PREFIX = "token:blacklist:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public TokenBlacklistService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Blacklist a token by its JTI. TTL should match the token's remaining lifetime.
     */
    public Mono<Boolean> blacklist(String jti, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(PREFIX + jti, "revoked", ttl)
                .doOnSuccess(ok -> log.info("Blacklisted token jti={}", jti));
    }

    /**
     * Check if a token JTI is blacklisted.
     */
    public Mono<Boolean> isBlacklisted(String jti) {
        return redisTemplate.hasKey(PREFIX + jti);
    }
}
