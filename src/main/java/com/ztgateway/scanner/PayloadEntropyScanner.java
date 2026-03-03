package com.ztgateway.scanner;

import org.springframework.stereotype.Component;

/**
 * Calculates Shannon entropy of payloads.
 * High entropy can indicate encoded/encrypted attack payloads.
 */
@Component
public class PayloadEntropyScanner {

    // Threshold: payloads above this entropy are considered suspicious
    private static final double ENTROPY_THRESHOLD = 5.5;

    public record EntropyResult(double entropy, boolean suspicious) {
        public static EntropyResult of(double entropy, double threshold) {
            return new EntropyResult(entropy, entropy > threshold);
        }
    }

    /**
     * Calculate Shannon entropy of the given payload.
     * Max possible entropy for byte data is 8.0 (each byte equally likely).
     */
    public EntropyResult analyze(String payload) {
        if (payload == null || payload.isEmpty()) {
            return new EntropyResult(0.0, false);
        }
        return analyze(payload, ENTROPY_THRESHOLD);
    }

    public EntropyResult analyze(String payload, double threshold) {
        if (payload == null || payload.isEmpty()) {
            return new EntropyResult(0.0, false);
        }

        double entropy = calculateEntropy(payload);
        return EntropyResult.of(entropy, threshold);
    }

    /**
     * Shannon entropy: H = -sum(p(x) * log2(p(x))) for each unique character.
     */
    public double calculateEntropy(String data) {
        if (data == null || data.isEmpty()) return 0.0;

        int[] freq = new int[256];
        for (int i = 0; i < data.length(); i++) {
            freq[data.charAt(i) & 0xFF]++;
        }

        double entropy = 0.0;
        double len = data.length();
        for (int count : freq) {
            if (count == 0) continue;
            double p = count / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    /**
     * Normalized entropy (0.0 - 1.0).
     */
    public double normalizedEntropy(String data) {
        double maxEntropy = 8.0; // maximum for byte data
        return calculateEntropy(data) / maxEntropy;
    }
}
