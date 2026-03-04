package com.ztgateway.filter;

import com.ztgateway.core.FilterOrder;
import com.ztgateway.core.GatewayFilter;
import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import com.ztgateway.monitoring.GatewayEventProducer;
import com.ztgateway.shadow.ShadowRouter;
import com.ztgateway.threat.engine.DecisionEngine;
import com.ztgateway.threat.engine.ThreatScoringEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Checkpoint 5 — Threat Filter (the smart one)
 * - Builds a feature vector from behavioral signals
 * - Runs ML inference (ONNX) or rule-based fallback
 * - Combines with behavioral drift analysis
 * - Score 0-60: ALLOW, 60-80: CHALLENGE, 80+: BLOCK + shadow copy
 */
@Component
public class ThreatFilter implements GatewayFilter {

    private static final Logger log = LoggerFactory.getLogger(ThreatFilter.class);

    private final ThreatScoringEngine scoringEngine;
    private final DecisionEngine decisionEngine;
    private final ShadowRouter shadowRouter;
    private final GatewayEventProducer eventProducer;

    public ThreatFilter(ThreatScoringEngine scoringEngine,
                        DecisionEngine decisionEngine,
                        ShadowRouter shadowRouter,
                        GatewayEventProducer eventProducer) {
        this.scoringEngine = scoringEngine;
        this.decisionEngine = decisionEngine;
        this.shadowRouter = shadowRouter;
        this.eventProducer = eventProducer;
    }

    @Override
    public FilterOrder getOrder() { return FilterOrder.THREAT; }

    @Override
    public String getName() { return "ThreatFilter"; }

    @Override
    public Mono<FilterResult> apply(RequestContext context) {
        return scoringEngine.score(context)
                .map(threatScore -> {
                    // Publish threat score event for analytics
                    eventProducer.publishThreatScore(context);

                    FilterResult result = decisionEngine.decide(threatScore);

                    // If blocked, send shadow copy for analysis
                    if (!result.isAllowed() && threatScore.score() >= 80) {
                        shadowRouter.shadowCopy(context);
                    }

                    return result;
                });
    }
}
