package com.ztgateway.controller;

import com.ztgateway.circuit.CircuitBreakerManager;
import com.ztgateway.circuit.DependencyHealthMonitor;
import com.ztgateway.geo.CountryBlocklistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin endpoints for managing the gateway at runtime.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final CircuitBreakerManager circuitBreakerManager;
    private final DependencyHealthMonitor healthMonitor;
    private final CountryBlocklistService countryBlocklist;

    public AdminController(CircuitBreakerManager circuitBreakerManager,
                           DependencyHealthMonitor healthMonitor,
                           CountryBlocklistService countryBlocklist) {
        this.circuitBreakerManager = circuitBreakerManager;
        this.healthMonitor = healthMonitor;
        this.countryBlocklist = countryBlocklist;
    }

    @GetMapping("/circuits")
    public ResponseEntity<Map<String, Object>> getCircuits() {
        Map<String, Object> result = new HashMap<>();
        circuitBreakerManager.getAllStates().forEach((name, state) ->
                result.put(name, Map.of(
                        "state", state.getState().name(),
                        "failures", state.getFailureCount(),
                        "lastFailure", state.getLastFailureAt() != null ?
                                state.getLastFailureAt().toString() : "never"
                ))
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/circuits/{service}/reset")
    public ResponseEntity<Map<String, String>> resetCircuit(@PathVariable String service) {
        circuitBreakerManager.reset(service);
        return ResponseEntity.ok(Map.of("status", "reset", "service", service));
    }

    @GetMapping("/health/services")
    public ResponseEntity<Map<String, Boolean>> getServiceHealth() {
        return ResponseEntity.ok(healthMonitor.getAllHealth());
    }

    @GetMapping("/geo/blocklist")
    public ResponseEntity<Map<String, Object>> getBlocklist() {
        return ResponseEntity.ok(Map.of("blockedCountries", countryBlocklist.getBlockedCountries()));
    }

    @PostMapping("/geo/blocklist/{countryCode}")
    public Mono<ResponseEntity<Map<String, String>>> addToBlocklist(@PathVariable String countryCode) {
        return countryBlocklist.addCountry(countryCode)
                .map(ok -> ResponseEntity.ok(Map.of("status", "added", "country", countryCode)));
    }

    @DeleteMapping("/geo/blocklist/{countryCode}")
    public Mono<ResponseEntity<Map<String, String>>> removeFromBlocklist(@PathVariable String countryCode) {
        return countryBlocklist.removeCountry(countryCode)
                .map(ok -> ResponseEntity.ok(Map.of("status", "removed", "country", countryCode)));
    }
}
