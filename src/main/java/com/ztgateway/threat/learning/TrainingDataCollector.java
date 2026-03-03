package com.ztgateway.threat.learning;

import com.ztgateway.model.FeatureVector;
import com.ztgateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Collects labeled training data from live traffic for model retraining.
 * Stores feature vectors with their outcomes (blocked/allowed) in Redis
 * for periodic batch export.
 */
@Service
public class TrainingDataCollector {

    private static final Logger log = LoggerFactory.getLogger(TrainingDataCollector.class);
    private static final String KEY_PREFIX = "training:data:";
    private static final Duration TTL = Duration.ofDays(7);

    private final ReactiveStringRedisTemplate redisTemplate;

    public TrainingDataCollector(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Record a labeled training sample.
     *
     * @param context      the request context
     * @param wasBlocked   whether the request was ultimately blocked
     * @param wasAttack    whether this was confirmed as an attack (from shadow analysis, manual review, etc.)
     */
    public Mono<Void> record(RequestContext context, boolean wasBlocked, boolean wasAttack) {
        FeatureVector fv = context.getFeatureVector();
        if (fv == null) return Mono.empty();

        String key = KEY_PREFIX + context.getRequestId();
        float[] features = fv.toModelInput()[0];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < features.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(features[i]);
        }
        String featureStr = sb.toString();

        String value = String.format("%s|%s|%s|%s",
                featureStr,
                wasBlocked ? "1" : "0",
                wasAttack ? "1" : "0",
                context.getThreatScore() != null ? String.valueOf(context.getThreatScore().score()) : "0");

        return redisTemplate.opsForValue().set(key, value, TTL)
                .doOnSuccess(ok -> log.debug("Recorded training sample for request={}", context.getRequestId()))
                .then();
    }
}
