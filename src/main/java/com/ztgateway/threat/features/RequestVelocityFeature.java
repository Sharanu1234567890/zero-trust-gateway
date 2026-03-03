package com.ztgateway.threat.features;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Tracks request velocity per user using Redis sorted sets.
 * Returns [requestsInLast1Second, requestsInLast10Seconds].
 */
@Component
public class RequestVelocityFeature {

    private static final String PREFIX = "velocity:";
    private static final Duration KEY_TTL = Duration.ofSeconds(60);

    private final ReactiveStringRedisTemplate redisTemplate;

    public RequestVelocityFeature(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Record the current request and return velocity metrics.
     */
    public Mono<float[]> extract(String userId) {
        String key = PREFIX + userId;
        long now = Instant.now().toEpochMilli();
        double score = now;
        String member = now + ":" + Math.random();

        return redisTemplate.opsForZSet().add(key, member, score)
                .then(redisTemplate.expire(key, KEY_TTL))
                .then(Mono.zip(
                        countInWindow(key, now, 1000),    // last 1 second
                        countInWindow(key, now, 10000)    // last 10 seconds
                ))
                .map(tuple -> new float[] { tuple.getT1().floatValue(), tuple.getT2().floatValue() })
                .onErrorReturn(new float[] { 0f, 0f });
    }

    private Mono<Long> countInWindow(String key, long now, long windowMs) {
        double min = now - windowMs;
        double max = now;
        return redisTemplate.opsForZSet().count(key, Range.closed(min, max));
    }
}
