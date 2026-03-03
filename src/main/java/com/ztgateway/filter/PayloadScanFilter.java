package com.ztgateway.filter;

import com.ztgateway.core.FilterOrder;
import com.ztgateway.core.GatewayFilter;
import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import com.ztgateway.scanner.PayloadEntropyScanner;
import com.ztgateway.scanner.SqlInjectionDetector;
import com.ztgateway.scanner.XssDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Checkpoint 4 — Payload Scan Filter
 * - Scans request body for SQL injection patterns
 * - Scans for XSS attack patterns
 * - Calculates entropy to detect encoded/encrypted attack payloads
 */
@Component
public class PayloadScanFilter implements GatewayFilter {

    private static final Logger log = LoggerFactory.getLogger(PayloadScanFilter.class);

    private final SqlInjectionDetector sqlDetector;
    private final XssDetector xssDetector;
    private final PayloadEntropyScanner entropyScanner;

    public PayloadScanFilter(SqlInjectionDetector sqlDetector,
                              XssDetector xssDetector,
                              PayloadEntropyScanner entropyScanner) {
        this.sqlDetector = sqlDetector;
        this.xssDetector = xssDetector;
        this.entropyScanner = entropyScanner;
    }

    @Override
    public FilterOrder getOrder() { return FilterOrder.PAYLOAD_SCAN; }

    @Override
    public String getName() { return "PayloadScanFilter"; }

    @Override
    public Mono<FilterResult> apply(RequestContext context) {
        String body = context.getBodyAsString();

        // Skip scanning for empty bodies (GET, DELETE, etc.)
        if (body == null || body.isBlank()) {
            return Mono.just(FilterResult.allow(getName()));
        }

        // Also scan query parameters from the path
        String path = context.getPath();
        String fullScanTarget = body;
        if (path.contains("?")) {
            fullScanTarget = body + " " + path.substring(path.indexOf("?") + 1);
        }

        // SQL Injection scan
        SqlInjectionDetector.ScanResult sqlResult = sqlDetector.scan(fullScanTarget);
        if (sqlResult.detected()) {
            log.warn("SQL injection detected from ip={} user={}: pattern={}",
                    context.getClientIp(), context.getUserId(), sqlResult.pattern());
            return Mono.just(FilterResult.block(getName(), 400,
                    "Malicious payload detected: SQL injection",
                    Map.of("type", "SQL_INJECTION", "pattern", sqlResult.pattern())));
        }

        // XSS scan
        XssDetector.ScanResult xssResult = xssDetector.scan(fullScanTarget);
        if (xssResult.detected()) {
            log.warn("XSS detected from ip={} user={}: pattern={}",
                    context.getClientIp(), context.getUserId(), xssResult.pattern());
            return Mono.just(FilterResult.block(getName(), 400,
                    "Malicious payload detected: XSS",
                    Map.of("type", "XSS", "pattern", xssResult.pattern())));
        }

        // Entropy scan — high entropy might indicate encoded attack
        PayloadEntropyScanner.EntropyResult entropyResult = entropyScanner.analyze(body);
        if (entropyResult.suspicious()) {
            log.warn("Suspicious payload entropy={:.2f} from ip={} user={}",
                    entropyResult.entropy(), context.getClientIp(), context.getUserId());
            // Don't block on entropy alone — just flag it. ThreatFilter will incorporate this.
            context.setAttribute("payload.entropy.suspicious", true);
            context.setAttribute("payload.entropy.value", entropyResult.entropy());
        }

        return Mono.just(FilterResult.allow(getName()));
    }
}
