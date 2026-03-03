package com.ztgateway.model;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carries all request data through the filter chain.
 * Mutable attribute bag allows filters to enrich context for downstream filters.
 */
public final class RequestContext {

    private final String requestId;
    private final Instant receivedAt;
    private final String clientIp;
    private final HttpMethod method;
    private final String path;
    private final HttpHeaders headers;
    private final byte[] body;
    private final Map<String, Object> attributes;
    private final List<FilterResult> filterResults;

    // Populated by AuthFilter
    private String userId;
    private List<String> roles;

    // Populated by GeoFenceFilter
    private String countryCode;
    private String city;
    private Double latitude;
    private Double longitude;

    // Populated by ThreatFilter
    private ThreatScore threatScore;
    private FeatureVector featureVector;

    // Target route info
    private String targetServiceId;
    private String targetUrl;

    public RequestContext(String clientIp, HttpMethod method, String path,
                          HttpHeaders headers, byte[] body) {
        this.requestId = UUID.randomUUID().toString();
        this.receivedAt = Instant.now();
        this.clientIp = clientIp;
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
        this.attributes = new ConcurrentHashMap<>();
        this.filterResults = new java.util.concurrent.CopyOnWriteArrayList<>();
        this.roles = List.of();
    }

    public String getRequestId() { return requestId; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getClientIp() { return clientIp; }
    public HttpMethod getMethod() { return method; }
    public String getPath() { return path; }
    public HttpHeaders getHeaders() { return headers; }
    public byte[] getBody() { return body; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public ThreatScore getThreatScore() { return threatScore; }
    public void setThreatScore(ThreatScore threatScore) { this.threatScore = threatScore; }

    public FeatureVector getFeatureVector() { return featureVector; }
    public void setFeatureVector(FeatureVector featureVector) { this.featureVector = featureVector; }

    public String getTargetServiceId() { return targetServiceId; }
    public void setTargetServiceId(String targetServiceId) { this.targetServiceId = targetServiceId; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public void setAttribute(String key, Object value) { attributes.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object val = attributes.get(key);
        if (val == null) return null;
        if (!type.isInstance(val)) {
            throw new ClassCastException("Attribute '" + key + "' is " + val.getClass().getName()
                    + ", expected " + type.getName());
        }
        return (T) val;
    }

    public void addFilterResult(FilterResult result) { filterResults.add(result); }
    public List<FilterResult> getFilterResults() { return List.copyOf(filterResults); }

    public String getBodyAsString() {
        return body != null ? new String(body, java.nio.charset.StandardCharsets.UTF_8) : "";
    }

    public String getAuthorizationToken() {
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    public String getUserAgent() {
        return headers.getFirst(HttpHeaders.USER_AGENT);
    }
}
