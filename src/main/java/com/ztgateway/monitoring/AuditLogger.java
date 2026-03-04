package com.ztgateway.monitoring;

import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Structured audit logging for all gateway decisions.
 * Writes to a dedicated audit logger for compliance and forensics.
 */
@Service
public class AuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    /**
     * Log the complete request audit trail.
     */
    public void logRequest(RequestContext context, FilterResult finalResult) {
        long durationMs = Duration.between(context.getReceivedAt(), Instant.now()).toMillis();

        auditLog.info("request_id={} client_ip={} user_id={} method={} path={} " +
                        "country={} decision={} status={} reason={} threat_score={} " +
                        "duration_ms={} filters_executed={}",
                context.getRequestId(),
                context.getClientIp(),
                context.getUserId() != null ? context.getUserId() : "anonymous",
                context.getMethod(),
                context.getPath(),
                context.getCountryCode() != null ? context.getCountryCode() : "unknown",
                finalResult.decision(),
                finalResult.httpStatus(),
                finalResult.reason(),
                context.getThreatScore() != null ?
                        String.format("%.1f", context.getThreatScore().score()) : "N/A",
                durationMs,
                context.getFilterResults().size());
    }

    /**
     * Log a security-relevant event (blocked requests, threat detections, etc.)
     */
    public void logSecurityEvent(String eventType, RequestContext context, String details) {
        auditLog.warn("SECURITY event={} request_id={} client_ip={} user_id={} path={} details={}",
                eventType,
                context.getRequestId(),
                context.getClientIp(),
                context.getUserId(),
                context.getPath(),
                details);
    }
}
