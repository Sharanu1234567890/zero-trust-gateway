package com.ztgateway.threat.features;

import com.ztgateway.model.FeatureVector;
import com.ztgateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Orchestrates all individual feature extractors to build a complete FeatureVector
 * from the RequestContext.
 */
@Component
public class FeatureExtractor {

    private static final Logger log = LoggerFactory.getLogger(FeatureExtractor.class);

    private final RequestVelocityFeature velocityFeature;
    private final HeaderFingerprintFeature headerFeature;
    private final PayloadEntropyFeature entropyFeature;
    private final TemporalFeature temporalFeature;
    private final RouteGraphFeature routeFeature;

    public FeatureExtractor(
            RequestVelocityFeature velocityFeature,
            HeaderFingerprintFeature headerFeature,
            PayloadEntropyFeature entropyFeature,
            TemporalFeature temporalFeature,
            RouteGraphFeature routeFeature) {
        this.velocityFeature = velocityFeature;
        this.headerFeature = headerFeature;
        this.entropyFeature = entropyFeature;
        this.temporalFeature = temporalFeature;
        this.routeFeature = routeFeature;
    }

    /**
     * Extract all features from the request context into a FeatureVector.
     */
    public Mono<FeatureVector> extract(RequestContext context) {
        String userId = context.getUserId() != null ? context.getUserId() : context.getClientIp();

        // Execute independent feature extractions in parallel
        Mono<float[]> velocityMono = velocityFeature.extract(userId);
        Mono<Float> headerMono = headerFeature.extract(userId, context.getHeaders());
        Mono<Float> entropyMono = Mono.fromCallable(() -> entropyFeature.extract(context.getBodyAsString()));
        Mono<float[]> temporalMono = Mono.fromCallable(() -> temporalFeature.extract(userId));
        Mono<Float> routeMono = routeFeature.extract(userId, context.getPath());

        return Mono.zip(velocityMono, headerMono, entropyMono, temporalMono, routeMono)
                .map(tuple -> {
                    float[] velocity = tuple.getT1();     // [req1s, req10s]
                    float headerFp = tuple.getT2();        // header fingerprint similarity
                    float entropy = tuple.getT3();          // normalized payload entropy
                    float[] temporal = tuple.getT4();       // [hourNorm, isTypicalHour]
                    float isNewRoute = tuple.getT5();       // 1.0 or 0.0

                    float userAgentMatch = headerFp > 0.7f ? 1.0f : 0.0f;

                    float[] features = new float[FeatureVector.DIMENSION];
                    features[FeatureVector.IDX_REQUESTS_1S] = velocity[0];
                    features[FeatureVector.IDX_REQUESTS_10S] = velocity[1];
                    features[FeatureVector.IDX_USER_AGENT_MATCH] = userAgentMatch;
                    features[FeatureVector.IDX_HOUR_OF_DAY_NORM] = temporal[0];
                    features[FeatureVector.IDX_IS_TYPICAL_HOUR] = temporal[1];
                    features[FeatureVector.IDX_PAYLOAD_ENTROPY] = entropy;
                    features[FeatureVector.IDX_IS_NEW_ROUTE] = isNewRoute;
                    features[FeatureVector.IDX_HEADER_FINGERPRINT] = headerFp;

                    FeatureVector fv = new FeatureVector(features);
                    log.debug("Extracted features for {}: {}", userId, fv);
                    return fv;
                });
    }
}
