package com.ztgateway.circuit;

import com.ztgateway.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages circuit breaker state for each downstream service.
 * States: CLOSED (normal) → OPEN (failing) → HALF_OPEN (testing recovery)
 */
@Service
public class CircuitBreakerManager {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerManager.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public static class CircuitState {
        private volatile State state = State.CLOSED;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private volatile Instant lastFailureAt;
        private volatile Instant openedAt;

        public State getState() { return state; }
        public int getFailureCount() { return failureCount.get(); }
        public int getSuccessCount() { return successCount.get(); }
        public Instant getLastFailureAt() { return lastFailureAt; }
        public Instant getOpenedAt() { return openedAt; }
    }

    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final long halfOpenTimeoutMs;
    private final int successThreshold;

    public CircuitBreakerManager(AppConfig appConfig) {
        this.failureThreshold = appConfig.getCircuitBreaker().getFailureThreshold();
        this.halfOpenTimeoutMs = appConfig.getCircuitBreaker().getHalfOpenTimeoutMs();
        this.successThreshold = appConfig.getCircuitBreaker().getSuccessThreshold();
    }

    /**
     * Check if the circuit allows requests to pass through.
     */
    public boolean isRequestAllowed(String serviceName) {
        CircuitState circuit = circuits.computeIfAbsent(serviceName, k -> new CircuitState());

        switch (circuit.state) {
            case CLOSED:
                return true;

            case OPEN:
                // Check if enough time has passed to try half-open
                if (circuit.openedAt != null &&
                    Instant.now().toEpochMilli() - circuit.openedAt.toEpochMilli() > halfOpenTimeoutMs) {
                    circuit.state = State.HALF_OPEN;
                    circuit.successCount.set(0);
                    log.info("Circuit for {} transitioning to HALF_OPEN", serviceName);
                    return true;
                }
                return false;

            case HALF_OPEN:
                return true;

            default:
                return true;
        }
    }

    /**
     * Record a successful request to a service.
     */
    public void recordSuccess(String serviceName) {
        CircuitState circuit = circuits.computeIfAbsent(serviceName, k -> new CircuitState());

        if (circuit.state == State.HALF_OPEN) {
            int successes = circuit.successCount.incrementAndGet();
            if (successes >= successThreshold) {
                circuit.state = State.CLOSED;
                circuit.failureCount.set(0);
                circuit.successCount.set(0);
                log.info("Circuit for {} CLOSED (recovered after {} successes)", serviceName, successes);
            }
        } else if (circuit.state == State.CLOSED) {
            // Reset failure count on success
            circuit.failureCount.set(0);
        }
    }

    /**
     * Record a failed request to a service.
     */
    public void recordFailure(String serviceName) {
        CircuitState circuit = circuits.computeIfAbsent(serviceName, k -> new CircuitState());
        circuit.lastFailureAt = Instant.now();

        if (circuit.state == State.HALF_OPEN) {
            // Any failure in half-open immediately re-opens
            circuit.state = State.OPEN;
            circuit.openedAt = Instant.now();
            log.warn("Circuit for {} re-OPENED (failed during half-open)", serviceName);
        } else if (circuit.state == State.CLOSED) {
            int failures = circuit.failureCount.incrementAndGet();
            if (failures >= failureThreshold) {
                circuit.state = State.OPEN;
                circuit.openedAt = Instant.now();
                log.warn("Circuit for {} OPENED after {} failures", serviceName, failures);
            }
        }
    }

    /**
     * Force-reset a circuit to CLOSED state.
     */
    public void reset(String serviceName) {
        CircuitState circuit = circuits.get(serviceName);
        if (circuit != null) {
            circuit.state = State.CLOSED;
            circuit.failureCount.set(0);
            circuit.successCount.set(0);
            log.info("Circuit for {} manually RESET to CLOSED", serviceName);
        }
    }

    public CircuitState getState(String serviceName) {
        return circuits.computeIfAbsent(serviceName, k -> new CircuitState());
    }

    public Map<String, CircuitState> getAllStates() {
        return Map.copyOf(circuits);
    }
}
