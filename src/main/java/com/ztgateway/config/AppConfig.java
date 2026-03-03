package com.ztgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "gateway")
public class AppConfig {

    private JwtConfig jwt = new JwtConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private GeoConfig geo = new GeoConfig();
    private ThreatConfig threat = new ThreatConfig();
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
    private List<RouteConfig> routes = new ArrayList<>();
    private ShadowConfig shadow = new ShadowConfig();
    private ChaosConfig chaos = new ChaosConfig();

    public JwtConfig getJwt() { return jwt; }
    public void setJwt(JwtConfig jwt) { this.jwt = jwt; }
    public RateLimitConfig getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimitConfig rateLimit) { this.rateLimit = rateLimit; }
    public GeoConfig getGeo() { return geo; }
    public void setGeo(GeoConfig geo) { this.geo = geo; }
    public ThreatConfig getThreat() { return threat; }
    public void setThreat(ThreatConfig threat) { this.threat = threat; }
    public CircuitBreakerConfig getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) { this.circuitBreaker = circuitBreaker; }
    public List<RouteConfig> getRoutes() { return routes; }
    public void setRoutes(List<RouteConfig> routes) { this.routes = routes; }
    public ShadowConfig getShadow() { return shadow; }
    public void setShadow(ShadowConfig shadow) { this.shadow = shadow; }
    public ChaosConfig getChaos() { return chaos; }
    public void setChaos(ChaosConfig chaos) { this.chaos = chaos; }

    public static class JwtConfig {
        private String secret;
        private long expirationMs = 3600000;
        private String issuer = "zero-trust-gateway";

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpirationMs() { return expirationMs; }
        public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
    }

    public static class RateLimitConfig {
        private int defaultRpm = 60;
        private int burstSize = 10;
        private int windowSizeSeconds = 60;

        public int getDefaultRpm() { return defaultRpm; }
        public void setDefaultRpm(int defaultRpm) { this.defaultRpm = defaultRpm; }
        public int getBurstSize() { return burstSize; }
        public void setBurstSize(int burstSize) { this.burstSize = burstSize; }
        public int getWindowSizeSeconds() { return windowSizeSeconds; }
        public void setWindowSizeSeconds(int windowSizeSeconds) { this.windowSizeSeconds = windowSizeSeconds; }
    }

    public static class GeoConfig {
        private String maxmindDbPath;
        private double impossibleTravelThresholdKmh = 900;

        public String getMaxmindDbPath() { return maxmindDbPath; }
        public void setMaxmindDbPath(String maxmindDbPath) { this.maxmindDbPath = maxmindDbPath; }
        public double getImpossibleTravelThresholdKmh() { return impossibleTravelThresholdKmh; }
        public void setImpossibleTravelThresholdKmh(double impossibleTravelThresholdKmh) {
            this.impossibleTravelThresholdKmh = impossibleTravelThresholdKmh;
        }
    }

    public static class ThreatConfig {
        private String modelPath;
        private int lowThreshold = 60;
        private int mediumThreshold = 80;
        private int featureWindowSeconds = 10;

        public String getModelPath() { return modelPath; }
        public void setModelPath(String modelPath) { this.modelPath = modelPath; }
        public int getLowThreshold() { return lowThreshold; }
        public void setLowThreshold(int lowThreshold) { this.lowThreshold = lowThreshold; }
        public int getMediumThreshold() { return mediumThreshold; }
        public void setMediumThreshold(int mediumThreshold) { this.mediumThreshold = mediumThreshold; }
        public int getFeatureWindowSeconds() { return featureWindowSeconds; }
        public void setFeatureWindowSeconds(int featureWindowSeconds) { this.featureWindowSeconds = featureWindowSeconds; }
    }

    public static class CircuitBreakerConfig {
        private int failureThreshold = 5;
        private long halfOpenTimeoutMs = 30000;
        private int successThreshold = 3;

        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
        public long getHalfOpenTimeoutMs() { return halfOpenTimeoutMs; }
        public void setHalfOpenTimeoutMs(long halfOpenTimeoutMs) { this.halfOpenTimeoutMs = halfOpenTimeoutMs; }
        public int getSuccessThreshold() { return successThreshold; }
        public void setSuccessThreshold(int successThreshold) { this.successThreshold = successThreshold; }
    }

    public static class RouteConfig {
        private String id;
        private String pathPrefix;
        private String targetUrl;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; }
        public String getTargetUrl() { return targetUrl; }
        public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    }

    public static class ShadowConfig {
        private boolean enabled = false;
        private String targetUrl;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTargetUrl() { return targetUrl; }
        public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    }

    public static class ChaosConfig {
        private boolean enabled = false;
        private long latencyMs = 0;
        private double errorRate = 0.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
        public double getErrorRate() { return errorRate; }
        public void setErrorRate(double errorRate) { this.errorRate = errorRate; }
    }
}
