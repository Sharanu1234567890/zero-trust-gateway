package com.ztgateway.model;

import java.time.Instant;

public record ThreatScore(
        double score,
        DecisionType decision,
        String explanation,
        Instant computedAt
) {
    public static ThreatScore safe(double score) {
        return new ThreatScore(score, DecisionType.ALLOW, "Normal behavior", Instant.now());
    }

    public static ThreatScore suspicious(double score, String explanation) {
        return new ThreatScore(score, DecisionType.CHALLENGE, explanation, Instant.now());
    }

    public static ThreatScore dangerous(double score, String explanation) {
        return new ThreatScore(score, DecisionType.BLOCK, explanation, Instant.now());
    }
}
