package com.ztgateway.monitoring;

import com.ztgateway.config.KafkaConfig;
import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes gateway events to Kafka for real-time monitoring and analytics.
 * All publishing is fire-and-forget — failures are logged but never block the request.
 */
@Service
public class GatewayEventProducer {

    private static final Logger log = LoggerFactory.getLogger(GatewayEventProducer.class);

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;
    private volatile boolean kafkaAvailable = true;

    public GatewayEventProducer(
            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
            KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish a filter execution event.
     */
    public void publishFilterEvent(RequestContext context, FilterResult result, long durationMs) {
        if (!kafkaAvailable) return;

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "FILTER_EXECUTION");
        event.put("requestId", context.getRequestId());
        event.put("timestamp", Instant.now().toString());
        event.put("clientIp", context.getClientIp());
        event.put("userId", context.getUserId());
        event.put("method", context.getMethod().name());
        event.put("path", context.getPath());
        event.put("filterName", result.filterName());
        event.put("decision", result.decision().name());
        event.put("reason", result.reason());
        event.put("durationMs", durationMs);

        sendAsync(KafkaConfig.TOPIC_GATEWAY_EVENTS, context.getRequestId(), event);
    }

    /**
     * Publish a threat score event for analytics.
     */
    public void publishThreatScore(RequestContext context) {
        if (!kafkaAvailable || context.getThreatScore() == null) return;

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "THREAT_SCORE");
        event.put("requestId", context.getRequestId());
        event.put("timestamp", Instant.now().toString());
        event.put("userId", context.getUserId());
        event.put("clientIp", context.getClientIp());
        event.put("path", context.getPath());
        event.put("score", context.getThreatScore().score());
        event.put("decision", context.getThreatScore().decision().name());
        event.put("explanation", context.getThreatScore().explanation());

        sendAsync(KafkaConfig.TOPIC_THREAT_SCORES, context.getRequestId(), event);
    }

    /**
     * Publish a circuit breaker state change event.
     */
    public void publishCircuitBreakerEvent(String serviceId, String newState, String reason) {
        if (!kafkaAvailable) return;

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CIRCUIT_BREAKER_STATE_CHANGE");
        event.put("serviceId", serviceId);
        event.put("newState", newState);
        event.put("reason", reason);
        event.put("timestamp", Instant.now().toString());

        sendAsync(KafkaConfig.TOPIC_CIRCUIT_BREAKER, serviceId, event);
    }

    /**
     * Publish a complete request audit event.
     */
    public void publishAuditEvent(RequestContext context, FilterResult finalResult, long totalDurationMs) {
        if (!kafkaAvailable) return;

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "REQUEST_AUDIT");
        event.put("requestId", context.getRequestId());
        event.put("timestamp", Instant.now().toString());
        event.put("clientIp", context.getClientIp());
        event.put("userId", context.getUserId());
        event.put("method", context.getMethod().name());
        event.put("path", context.getPath());
        event.put("countryCode", context.getCountryCode());
        event.put("finalDecision", finalResult.decision().name());
        event.put("finalReason", finalResult.reason());
        event.put("httpStatus", finalResult.httpStatus());
        event.put("totalDurationMs", totalDurationMs);

        if (context.getThreatScore() != null) {
            event.put("threatScore", context.getThreatScore().score());
        }

        event.put("filterResults", context.getFilterResults().stream()
                .map(fr -> Map.of(
                        "filter", fr.filterName(),
                        "decision", fr.decision().name(),
                        "reason", fr.reason()))
                .toList());

        sendAsync(KafkaConfig.TOPIC_AUDIT_LOG, context.getRequestId(), event);
    }

    private void sendAsync(String topic, String key, Map<String, Object> event) {
        try {
            kafkaTemplate.send(topic, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.debug("Kafka publish failed for topic={}: {}", topic, ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            // Kafka not available — disable to avoid repeated errors
            if (kafkaAvailable) {
                log.warn("Kafka unavailable, disabling event publishing: {}", e.getMessage());
                kafkaAvailable = false;
            }
        }
    }
}
