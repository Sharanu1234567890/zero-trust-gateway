package com.ztgateway.chaos;

import com.ztgateway.config.AppConfig;
import com.ztgateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Chaos engineering service — intentionally introduces failures to test resilience.
 * DISABLED by default. Enable via configuration only in test/staging environments.
 */
@Service
public class ChaosExperimentService {

    private static final Logger log = LoggerFactory.getLogger(ChaosExperimentService.class);

    private final boolean enabled;
    private final LatencyInjector latencyInjector;
    private final ErrorInjector errorInjector;

    public ChaosExperimentService(AppConfig appConfig,
                                   LatencyInjector latencyInjector,
                                   ErrorInjector errorInjector) {
        this.enabled = appConfig.getChaos().isEnabled();
        this.latencyInjector = latencyInjector;
        this.errorInjector = errorInjector;

        if (enabled) {
            log.warn("CHAOS ENGINEERING IS ENABLED — DO NOT USE IN PRODUCTION");
        }
    }

    /**
     * Apply chaos experiments to the request pipeline.
     * Returns Mono.empty() if no chaos applies, or Mono.error() to simulate failure.
     */
    public Mono<Void> maybeApplyChaos(RequestContext context) {
        if (!enabled) return Mono.empty();

        // Check for error injection first
        if (errorInjector.shouldInjectError()) {
            log.info("[CHAOS] Injecting error for request={}", context.getRequestId());
            return Mono.error(new RuntimeException("Chaos: simulated upstream failure"));
        }

        // Apply latency injection
        Duration delay = latencyInjector.getDelay();
        if (!delay.isZero()) {
            log.info("[CHAOS] Injecting {}ms delay for request={}", delay.toMillis(), context.getRequestId());
            return Mono.delay(delay).then();
        }

        return Mono.empty();
    }

    public boolean isEnabled() { return enabled; }
}
