package com.ztgateway.circuit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Periodically checks open circuits and forces recovery attempts
 * if a service has been in OPEN state for too long.
 */
@Service
public class AutoRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AutoRecoveryService.class);
    private static final Duration MAX_OPEN_DURATION = Duration.ofMinutes(10);

    private final CircuitBreakerManager circuitBreakerManager;

    public AutoRecoveryService(CircuitBreakerManager circuitBreakerManager) {
        this.circuitBreakerManager = circuitBreakerManager;
    }

    /**
     * Every 30 seconds, check if any circuits have been open too long
     * and force them to half-open to test recovery.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void autoRecover() {
        Map<String, CircuitBreakerManager.CircuitState> states = circuitBreakerManager.getAllStates();

        for (Map.Entry<String, CircuitBreakerManager.CircuitState> entry : states.entrySet()) {
            CircuitBreakerManager.CircuitState state = entry.getValue();
            if (state.getState() == CircuitBreakerManager.State.OPEN && state.getOpenedAt() != null) {
                Duration openDuration = Duration.between(state.getOpenedAt(), Instant.now());
                if (openDuration.compareTo(MAX_OPEN_DURATION) > 0) {
                    log.info("Auto-recovering circuit for {} (open for {})", entry.getKey(), openDuration);
                    circuitBreakerManager.reset(entry.getKey());
                }
            }
        }
    }
}
