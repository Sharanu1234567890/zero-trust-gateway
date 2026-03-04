package com.ztgateway.threat.engine;

import com.ztgateway.model.FeatureVector;
import com.ztgateway.model.RequestContext;
import com.ztgateway.model.ThreatScore;
import com.ztgateway.threat.behavioral.BehavioralDriftAnalyzer;
import com.ztgateway.threat.features.FeatureExtractor;
import com.ztgateway.threat.inference.OnnxInferenceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Orchestrates threat scoring: extracts features, runs inference,
 * combines with behavioral drift, and produces a final ThreatScore.
 */
@Service
public class ThreatScoringEngine {

    private static final Logger log = LoggerFactory.getLogger(ThreatScoringEngine.class);

    private final FeatureExtractor featureExtractor;
    private final OnnxInferenceEngine inferenceEngine;
    private final BehavioralDriftAnalyzer driftAnalyzer;

    public ThreatScoringEngine(FeatureExtractor featureExtractor,
                                OnnxInferenceEngine inferenceEngine,
                                BehavioralDriftAnalyzer driftAnalyzer) {
        this.featureExtractor = featureExtractor;
        this.inferenceEngine = inferenceEngine;
        this.driftAnalyzer = driftAnalyzer;
    }

    /**
     * Score the request for threat level.
     */
    public Mono<ThreatScore> score(RequestContext context) {
        return featureExtractor.extract(context)
                .map(featureVector -> {
                    context.setFeatureVector(featureVector);

                    // ML-based or rule-based scoring
                    float mlScore = inferenceEngine.score(featureVector);

                    // Behavioral drift analysis
                    BehavioralDriftAnalyzer.DriftReport drift = driftAnalyzer.analyze(context);

                    // Combine: 70% ML score + 30% drift
                    double combinedScore = (mlScore * 0.7) + (drift.overallDrift() * 100 * 0.3);
                    combinedScore = Math.min(100, Math.max(0, combinedScore));

                    ThreatScore threatScore = buildThreatScore(combinedScore, mlScore, drift);
                    context.setThreatScore(threatScore);

                    log.debug("[{}] Threat score: combined={:.1f} ml={:.1f} drift={:.2f}",
                            context.getRequestId(), combinedScore, mlScore, drift.overallDrift());

                    return threatScore;
                });
    }

    private ThreatScore buildThreatScore(double score, float mlScore,
                                          BehavioralDriftAnalyzer.DriftReport drift) {
        StringBuilder explanation = new StringBuilder();
        explanation.append(String.format("ML=%.1f, drift=%.2f", mlScore, drift.overallDrift()));

        if (drift.userAgentDrift()) explanation.append(", UA-anomaly");
        if (drift.temporalDrift()) explanation.append(", time-anomaly");
        if (drift.routeDrift()) explanation.append(", route-anomaly");
        if (drift.geoDrift()) explanation.append(", geo-anomaly");

        if (score >= 80) {
            return ThreatScore.dangerous(score, explanation.toString());
        } else if (score >= 60) {
            return ThreatScore.suspicious(score, explanation.toString());
        } else {
            return ThreatScore.safe(score);
        }
    }
}
