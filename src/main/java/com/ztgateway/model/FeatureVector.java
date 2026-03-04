package com.ztgateway.model;

import java.util.Arrays;

/**
 * Immutable feature vector for ML threat scoring.
 * Features:
 *   [0] requestsLast1s   - request count in last 1 second
 *   [1] requestsLast10s  - request count in last 10 seconds
 *   [2] userAgentMatch   - 1.0 if matches typical user-agent, 0.0 otherwise
 *   [3] hourOfDayNorm    - normalized hour of day (0.0 - 1.0)
 *   [4] isTypicalHour    - 1.0 if request at typical hour, 0.0 otherwise
 *   [5] payloadEntropy   - Shannon entropy of payload (0.0 - 8.0, normalized)
 *   [6] isNewRoute       - 1.0 if user has never visited this route, 0.0 otherwise
 *   [7] headerFingerprint - similarity score to known fingerprint (0.0 - 1.0)
 */
public record FeatureVector(float[] features) {

    public static final int DIMENSION = 8;

    public static final int IDX_REQUESTS_1S = 0;
    public static final int IDX_REQUESTS_10S = 1;
    public static final int IDX_USER_AGENT_MATCH = 2;
    public static final int IDX_HOUR_OF_DAY_NORM = 3;
    public static final int IDX_IS_TYPICAL_HOUR = 4;
    public static final int IDX_PAYLOAD_ENTROPY = 5;
    public static final int IDX_IS_NEW_ROUTE = 6;
    public static final int IDX_HEADER_FINGERPRINT = 7;

    public FeatureVector {
        if (features.length != DIMENSION) {
            throw new IllegalArgumentException(
                    "Feature vector must have " + DIMENSION + " dimensions, got " + features.length);
        }
    }

    public float get(int index) {
        return features[index];
    }

    public float[][] toModelInput() {
        return new float[][] { Arrays.copyOf(features, features.length) };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeatureVector that)) return false;
        return Arrays.equals(features, that.features);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(features);
    }

    @Override
    public String toString() {
        return "FeatureVector" + Arrays.toString(features);
    }
}
