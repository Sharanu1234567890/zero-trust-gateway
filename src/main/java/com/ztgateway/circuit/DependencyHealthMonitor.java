package com.ztgateway.circuit;

import com.ztgateway.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically pings downstream services to check their health.
 * Feeds results to the CircuitBreakerManager.
 */
@Service
public class DependencyHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(DependencyHealthMonitor.class);

    private final AppConfig appConfig;
    private final WebClient webClient;
    private final CircuitBreakerManager circuitBreakerManager;
    private final Map<String, Boolean> healthStatus = new ConcurrentHashMap<>();

    public DependencyHealthMonitor(AppConfig appConfig, WebClient webClient,
                                    CircuitBreakerManager circuitBreakerManager) {
        this.appConfig = appConfig;
        this.webClient = webClient;
        this.circuitBreakerManager = circuitBreakerManager;
    }

    /**
     * Ping all configured services every 15 seconds.
     */
    @Scheduled(fixedDelay = 15000, initialDelay = 5000)
    public void checkHealth() {
        for (AppConfig.RouteConfig route : appConfig.getRoutes()) {
            webClient.get()
                    .uri(route.getTargetUrl())
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(5))
                    .subscribe(
                            response -> {
                                healthStatus.put(route.getId(), true);
                                circuitBreakerManager.recordSuccess(route.getId());
                            },
                            error -> {
                                healthStatus.put(route.getId(), false);
                                log.warn("Health check failed for {}: {}", route.getId(), error.getMessage());
                                // Don't record failure here — let actual request failures drive circuit state
                            }
                    );
        }
    }

    public boolean isHealthy(String serviceId) {
        return healthStatus.getOrDefault(serviceId, true); // Assume healthy if unknown
    }

    public Map<String, Boolean> getAllHealth() {
        return Map.copyOf(healthStatus);
    }
}
