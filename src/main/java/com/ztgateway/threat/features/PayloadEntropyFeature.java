package com.ztgateway.threat.features;

import com.ztgateway.scanner.PayloadEntropyScanner;
import org.springframework.stereotype.Component;

/**
 * Extracts normalized payload entropy as a feature for the threat model.
 */
@Component
public class PayloadEntropyFeature {

    private final PayloadEntropyScanner entropyScanner;

    public PayloadEntropyFeature(PayloadEntropyScanner entropyScanner) {
        this.entropyScanner = entropyScanner;
    }

    /**
     * Returns normalized entropy (0.0 - 1.0) for the payload.
     */
    public float extract(String payload) {
        if (payload == null || payload.isEmpty()) {
            return 0.0f;
        }
        return (float) entropyScanner.normalizedEntropy(payload);
    }
}
