package com.ztgateway.config;

import com.ztgateway.threat.inference.OnnxInferenceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelConfig {                                                                                                

    private static final Logger log = LoggerFactory.getLogger(ModelConfig.class);

    @Bean
    public OnnxInferenceEngine onnxInferenceEngine(AppConfig appConfig) {
        String modelPath = appConfig.getThreat().getModelPath();
        if (modelPath == null || modelPath.isBlank()) {
            log.warn("No ONNX model path configured — using rule-based fallback scoring");
            return new OnnxInferenceEngine(null);
        }
        log.info("Loading ONNX model from: {}", modelPath);
        return new OnnxInferenceEngine(modelPath);
    }
}
