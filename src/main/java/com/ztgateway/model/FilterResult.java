package com.ztgateway.model;

import java.time.Instant;
import java.util.Map;

public record FilterResult(
        String filterName,
        DecisionType decision,
        int httpStatus,
        String reason,
        Map<String, Object> metadata,
        Instant timestamp
) {
    public static FilterResult allow(String filterName) {
        return new FilterResult(filterName, DecisionType.ALLOW, 200, "Passed", Map.of(), Instant.now());
    }

    public static FilterResult block(String filterName, int httpStatus, String reason) {
        return new FilterResult(filterName, DecisionType.BLOCK, httpStatus, reason, Map.of(), Instant.now());
    }

    public static FilterResult block(String filterName, int httpStatus, String reason, Map<String, Object> metadata) {
        return new FilterResult(filterName, DecisionType.BLOCK, httpStatus, reason, metadata, Instant.now());
    }

    public static FilterResult challenge(String filterName, String reason) {
        return new FilterResult(filterName, DecisionType.CHALLENGE, 428, reason, Map.of(), Instant.now());
    }

    public static FilterResult challenge(String filterName, String reason, Map<String, Object> metadata) {
        return new FilterResult(filterName, DecisionType.CHALLENGE, 428, reason, metadata, Instant.now());
    }

    public static FilterResult rateLimited(String filterName) {
        return new FilterResult(filterName, DecisionType.RATE_LIMITED, 429, "Too Many Requests", Map.of(), Instant.now());
    }

    public static FilterResult circuitOpen(String filterName, String serviceName) {
        return new FilterResult(filterName, DecisionType.CIRCUIT_OPEN, 503,
                "Service unavailable: " + serviceName, Map.of("service", serviceName), Instant.now());
    }

    public boolean isAllowed() {
        return decision == DecisionType.ALLOW;
    }
}
