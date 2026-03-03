package com.ztgateway.monitoring;

import com.ztgateway.model.DecisionType;
import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects and exposes Prometheus metrics for the gateway.
 */
@Service
public class MetricsCollector {

    private final MeterRegistry registry;
    private final Map<String, Counter> filterCounters = new ConcurrentHashMap<>();
    private final Timer requestTimer;
    private final Counter totalRequests;
    private final Counter blockedRequests;
    private final Counter challengedRequests;
    private final Counter allowedRequests;

    public MetricsCollector(MeterRegistry registry) {
        this.registry = registry;

        this.requestTimer = Timer.builder("gateway.request.duration")
                .description("Total request processing time")
                .register(registry);

        this.totalRequests = Counter.builder("gateway.requests.total")
                .description("Total requests received")
                .register(registry);

        this.blockedRequests = Counter.builder("gateway.requests.blocked")
                .description("Total requests blocked")
                .register(registry);

        this.challengedRequests = Counter.builder("gateway.requests.challenged")
                .description("Total requests challenged")
                .register(registry);

        this.allowedRequests = Counter.builder("gateway.requests.allowed")
                .description("Total requests allowed")
                .register(registry);
    }

    public void recordRequest(RequestContext context, FilterResult finalResult) {
        totalRequests.increment();

        Duration duration = Duration.between(context.getReceivedAt(), Instant.now());
        requestTimer.record(duration);

        switch (finalResult.decision()) {
            case BLOCK -> blockedRequests.increment();
            case CHALLENGE -> challengedRequests.increment();
            case ALLOW -> allowedRequests.increment();
            case RATE_LIMITED -> getOrCreateCounter("filter.rate_limited", "RateLimitFilter").increment();
            case CIRCUIT_OPEN -> getOrCreateCounter("filter.circuit_open", "CircuitBreakerFilter").increment();
        }
    }

    public void recordFilterDecision(String filterName, DecisionType decision) {
        String key = "filter." + filterName + "." + decision.name().toLowerCase();
        getOrCreateCounter(key, filterName).increment();
    }

    public void recordThreatScore(double score) {
        registry.summary("gateway.threat.score").record(score);
    }

    public void recordUpstreamLatency(String serviceId, Duration duration) {
        Timer.builder("gateway.upstream.latency")
                .tag("service", serviceId)
                .register(registry)
                .record(duration);
    }

    private Counter getOrCreateCounter(String key, String filterName) {
        return filterCounters.computeIfAbsent(key, k ->
                Counter.builder("gateway." + k)
                        .tag("filter", filterName)
                        .register(registry));
    }
}
