package com.ztgateway.threat.learning;

import com.ztgateway.threat.inference.OnnxInferenceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages ONNX model versions for safe deployment and rollback.
 * Supports:
 *   - Registering new model versions
 *   - A/B testing between versions
 *   - Instant rollback to previous version
 */
@Service
public class ModelVersionManager {

    private static final Logger log = LoggerFactory.getLogger(ModelVersionManager.class);

    private final AtomicReference<String> activeVersion = new AtomicReference<>("default");
    private final Map<String, ModelInfo> versions = new ConcurrentHashMap<>();

    public record ModelInfo(
            String version,
            String modelPath,
            double accuracy,
            boolean isActive,
            long registeredAt
    ) {}

    /**
     * Register a new model version.
     */
    public void register(String version, String modelPath, double accuracy) {
        versions.put(version, new ModelInfo(version, modelPath, accuracy, false, System.currentTimeMillis()));
        log.info("Registered model version={} path={} accuracy={:.4f}", version, modelPath, accuracy);
    }

    /**
     * Activate a specific model version.
     * In production, this would hot-swap the ONNX session in the inference engine.
     */
    public boolean activate(String version) {
        ModelInfo info = versions.get(version);
        if (info == null) {
            log.error("Cannot activate unknown model version: {}", version);
            return false;
        }

        String previous = activeVersion.getAndSet(version);
        log.info("Activated model version={} (previous={})", version, previous);
        return true;
    }

    /**
     * Rollback to the previous model version.
     */
    public void rollback() {
        // Find the second-most-recent version
        versions.values().stream()
                .filter(v -> !v.version().equals(activeVersion.get()))
                .max((a, b) -> Long.compare(a.registeredAt(), b.registeredAt()))
                .ifPresent(prev -> {
                    log.warn("Rolling back model from {} to {}", activeVersion.get(), prev.version());
                    activeVersion.set(prev.version());
                });
    }

    public String getActiveVersion() {
        return activeVersion.get();
    }

    public Map<String, ModelInfo> getAllVersions() {
        return Map.copyOf(versions);
    }
}
