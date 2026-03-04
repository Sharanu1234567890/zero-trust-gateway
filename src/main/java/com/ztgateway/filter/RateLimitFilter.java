package com.ztgateway.filter;

import com.ztgateway.config.AppConfig;
import com.ztgateway.core.FilterOrder;
import com.ztgateway.core.GatewayFilter;
import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import com.ztgateway.ratelimit.RateLimitStrategy;
import com.ztgateway.ratelimit.SlidingWindowRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Checkpoint 3 — Rate Limit Filter
 * - Uses sliding window algorithm (not fixed window)
 * - Different limits based on user tier/role
 * - Identified by userId if authenticated, otherwise by IP
 */
@Component
public class RateLimitFilter implements GatewayFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final SlidingWindowRateLimiter rateLimiter;
    private final int windowSeconds;

    public RateLimitFilter(SlidingWindowRateLimiter rateLimiter, AppConfig appConfig) {
        this.rateLimiter = rateLimiter;
        this.windowSeconds = appConfig.getRateLimit().getWindowSizeSeconds();
    }

    @Override
    public FilterOrder getOrder() { return FilterOrder.RATE_LIMIT; }

    @Override
    public String getName() { return "RateLimitFilter"; }

    @Override
    public Mono<FilterResult> apply(RequestContext context) {
        // Determine identifier: userId if authenticated, otherwise IP
        String identifier = context.getUserId() != null ? context.getUserId() : context.getClientIp();

        // Determine rate limit strategy based on roles
        RateLimitStrategy strategy = RateLimitStrategy.fromRoles(context.getRoles());
        int maxRequests = strategy.getRequestsPerMinute();

        return rateLimiter.isAllowed(identifier, maxRequests, windowSeconds)
                .map(allowed -> {
                    if (!allowed) {
                        log.warn("Rate limit exceeded for identifier={} limit={}/{}s",
                                identifier, maxRequests, windowSeconds);
                        return FilterResult.rateLimited(getName());
                    }
                    return FilterResult.allow(getName());
                });
    }
}
