package com.ztgateway.chaos;

import com.ztgateway.config.AppConfig;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Randomly injects errors based on configured error rate for chaos testing.
 */
@Component
public class ErrorInjector {

    private final double errorRate;

    public ErrorInjector(AppConfig appConfig) {
        this.errorRate = appConfig.getChaos().getErrorRate();
    }

    /**
     * Returns true if an error should be injected for this request.
     */
    public boolean shouldInjectError() {
        if (errorRate <= 0.0) return false;
        return ThreadLocalRandom.current().nextDouble() < errorRate;
    }
}
