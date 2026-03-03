package com.ztgateway.threat.engine;

import com.ztgateway.config.AppConfig;
import com.ztgateway.model.DecisionType;
import com.ztgateway.model.FilterResult;
import com.ztgateway.model.ThreatScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Makes the final decision based on the threat score:
 *   0-60   → ALLOW
 *   60-80  → CHALLENGE (step-up auth, CAPTCHA)
 *   80-100 → BLOCK + shadow copy
 */
@Service
public class DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngine.class);

    private final int lowThreshold;
    private final int mediumThreshold;

    public DecisionEngine(AppConfig appConfig) {
        this.lowThreshold = appConfig.getThreat().getLowThreshold();
        this.mediumThreshold = appConfig.getThreat().getMediumThreshold();
    }

    public FilterResult decide(ThreatScore threatScore) {
        double score = threatScore.score();

        if (score >= mediumThreshold) {
            log.warn("BLOCK: Threat score {:.1f} exceeds threshold {}", score, mediumThreshold);
            return FilterResult.block("ThreatFilter", 403,
                    "Request blocked by threat analysis",
                    Map.of("score", score, "explanation", threatScore.explanation()));
        }

        if (score >= lowThreshold) {
            log.info("CHALLENGE: Threat score {:.1f} in suspicious range [{}, {})",
                    score, lowThreshold, mediumThreshold);
            return FilterResult.challenge("ThreatFilter",
                    "Additional verification required",
                    Map.of("score", score, "explanation", threatScore.explanation()));
        }

        return FilterResult.allow("ThreatFilter");
    }
}
