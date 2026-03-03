package com.ztgateway.filter;

import com.ztgateway.circuit.CircuitBreakerManager;
import com.ztgateway.config.AppConfig;
import com.ztgateway.core.FilterOrder;
import com.ztgateway.core.GatewayFilter;
import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Checkpoint 6 — Circuit Breaker Filter
 * - Checks if the target service is healthy before forwarding
 * - If circuit is OPEN, returns fallback immediately (fail fast)
 * - Protects the system from cascade failures
 */
@Component
public class CircuitBreakerFilter implements GatewayFilter {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerFilter.class);

    private final CircuitBreakerManager circuitBreakerManager;
    private final AppConfig appConfig;

    public CircuitBreakerFilter(CircuitBreakerManager circuitBreakerManager, AppConfig appConfig) {
        this.circuitBreakerManager = circuitBreakerManager;
        this.appConfig = appConfig;
    }

    @Override
    public FilterOrder getOrder() { return FilterOrder.CIRCUIT_BREAKER; }

    @Override
    public String getName() { return "CircuitBreakerFilter"; }

    @Override
    public Mono<FilterResult> apply(RequestContext context) {
        // Resolve which service this request targets
        Optional<AppConfig.RouteConfig> route = resolveRoute(context.getPath());

        if (route.isEmpty()) {
            return Mono.just(FilterResult.block(getName(), 404,
                    "No route found for path: " + context.getPath()));
        }

        AppConfig.RouteConfig routeConfig = route.get();
        context.setTargetServiceId(routeConfig.getId());
        context.setTargetUrl(routeConfig.getTargetUrl());

        // Check circuit breaker state
        if (!circuitBreakerManager.isRequestAllowed(routeConfig.getId())) {
            CircuitBreakerManager.CircuitState state = circuitBreakerManager.getState(routeConfig.getId());
            log.warn("Circuit OPEN for service={} state={} failures={}",
                    routeConfig.getId(), state.getState(), state.getFailureCount());
            return Mono.just(FilterResult.circuitOpen(getName(), routeConfig.getId()));
        }

        return Mono.just(FilterResult.allow(getName()));
    }

    private Optional<AppConfig.RouteConfig> resolveRoute(String path) {
        return appConfig.getRoutes().stream()
                .filter(r -> path.startsWith(r.getPathPrefix()))
                .findFirst();
    }
}
