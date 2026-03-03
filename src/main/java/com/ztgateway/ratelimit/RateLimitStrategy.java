package com.ztgateway.ratelimit;

/**
 * Defines different rate limiting strategies per user tier.
 */
public enum RateLimitStrategy {

    DEFAULT(60, 10),
    PREMIUM(300, 50),
    INTERNAL(1000, 200),
    RESTRICTED(10, 2);

    private final int requestsPerMinute;
    private final int burstSize;

    RateLimitStrategy(int requestsPerMinute, int burstSize) {
        this.requestsPerMinute = requestsPerMinute;
        this.burstSize = burstSize;
    }

    public int getRequestsPerMinute() { return requestsPerMinute; }
    public int getBurstSize() { return burstSize; }

    /**
     * Resolve strategy from user roles.
     */
    public static RateLimitStrategy fromRoles(java.util.List<String> roles) {
        if (roles == null || roles.isEmpty()) return DEFAULT;
        if (roles.contains("INTERNAL_SERVICE")) return INTERNAL;
        if (roles.contains("PREMIUM")) return PREMIUM;
        if (roles.contains("RESTRICTED")) return RESTRICTED;
        return DEFAULT;
    }
}
