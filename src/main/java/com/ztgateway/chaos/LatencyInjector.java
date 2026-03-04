package com.ztgateway.chaos;

import com.ztgateway.config.AppConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Injects artificial latency into request processing for chaos testing.
 */
@Component
public class LatencyInjector {

    private final long latencyMs;

    public LatencyInjector(AppConfig appConfig) {
        this.latencyMs = appConfig.getChaos().getLatencyMs();
    }

    public Duration getDelay() {
        if (latencyMs <= 0) return Duration.ZERO;
        // Add some jitter: latency +/- 20%
        double jitter = 0.8 + (Math.random() * 0.4);
        return Duration.ofMillis((long) (latencyMs * jitter));
    }
}
