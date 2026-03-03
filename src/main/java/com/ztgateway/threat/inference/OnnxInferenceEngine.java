package com.ztgateway.threat.inference;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.ztgateway.model.FeatureVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;
import java.util.Map;

/**
 * Runs ONNX model inference inside the JVM for real-time threat scoring.
 * Falls back to rule-based scoring when no model is loaded.
 */
public class OnnxInferenceEngine {

    private static final Logger log = LoggerFactory.getLogger(OnnxInferenceEngine.class);

    private OrtEnvironment env;
    private OrtSession session;
    private final boolean modelLoaded;

    public OnnxInferenceEngine(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            this.modelLoaded = false;
            log.info("ONNX engine initialized in fallback mode (no model)");
            return;
        }

        try {
            this.env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(2);
            this.session = env.createSession(modelPath, opts);
            this.modelLoaded = true;
            log.info("ONNX model loaded successfully from: {}", modelPath);
        } catch (OrtException e) {
            throw new RuntimeException("Failed to load ONNX model from: " + modelPath, e);
        }
    }

    /**
     * Run inference on a feature vector. Returns a threat score 0-100.
     */
    public float score(FeatureVector featureVector) {
        if (!modelLoaded) {
            return ruleBasedScore(featureVector);
        }

        try {
            float[][] input = featureVector.toModelInput();
            OnnxTensor tensor = OnnxTensor.createTensor(env, input);
            OrtSession.Result result = session.run(Map.of("input", tensor));
            float[][] output = (float[][]) result.get(0).getValue();
            tensor.close();
            result.close();

            // Model outputs a single value 0-1, scale to 0-100
            float rawScore = output[0][0];
            return Math.max(0, Math.min(100, rawScore * 100));
        } catch (OrtException e) {
            log.error("ONNX inference failed, falling back to rules: {}", e.getMessage());
            return ruleBasedScore(featureVector);
        }
    }

    /**
     * Rule-based fallback scoring when no ONNX model is available.
     * Examines the feature vector dimensions and produces a weighted threat score.
     */
    private float ruleBasedScore(FeatureVector fv) {
        float score = 0;

        // High request velocity is suspicious
        float req1s = fv.get(FeatureVector.IDX_REQUESTS_1S);
        float req10s = fv.get(FeatureVector.IDX_REQUESTS_10S);
        if (req1s > 10) score += 25;
        else if (req1s > 5) score += 15;
        if (req10s > 50) score += 20;
        else if (req10s > 20) score += 10;

        // User-agent mismatch
        if (fv.get(FeatureVector.IDX_USER_AGENT_MATCH) < 0.5f) score += 15;

        // Unusual hour
        if (fv.get(FeatureVector.IDX_IS_TYPICAL_HOUR) < 0.5f) score += 10;

        // High payload entropy
        float entropy = fv.get(FeatureVector.IDX_PAYLOAD_ENTROPY);
        if (entropy > 0.8f) score += 20;
        else if (entropy > 0.6f) score += 10;

        // New route
        if (fv.get(FeatureVector.IDX_IS_NEW_ROUTE) > 0.5f) score += 10;

        // Header fingerprint drift
        if (fv.get(FeatureVector.IDX_HEADER_FINGERPRINT) < 0.5f) score += 15;

        return Math.min(100, score);
    }

    public boolean isModelLoaded() {
        return modelLoaded;
    }

    @PreDestroy
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (OrtException e) {
            log.error("Error closing ONNX session: {}", e.getMessage());
        }
    }
}
