package com.ztgateway.controller;

import com.ztgateway.chaos.ChaosExperimentService;
import com.ztgateway.circuit.CircuitBreakerManager;
import com.ztgateway.core.FilterChainExecutor;
import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import com.ztgateway.monitoring.AuditLogger;
import com.ztgateway.monitoring.GatewayEventProducer;
import com.ztgateway.monitoring.MetricsCollector;
import com.ztgateway.threat.behavioral.ProfileUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The main gateway controller. Catches all incoming requests,
 * runs them through the filter chain, and proxies to upstream services.
 */
@RestController
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    private final FilterChainExecutor filterChain;
    private final WebClient webClient;
    private final CircuitBreakerManager circuitBreakerManager;
    private final MetricsCollector metricsCollector;
    private final GatewayEventProducer eventProducer;
    private final AuditLogger auditLogger;
    private final ProfileUpdater profileUpdater;
    private final ChaosExperimentService chaosService;

    public GatewayController(FilterChainExecutor filterChain,
                              WebClient webClient,
                              CircuitBreakerManager circuitBreakerManager,
                              MetricsCollector metricsCollector,
                              GatewayEventProducer eventProducer,
                              AuditLogger auditLogger,
                              ProfileUpdater profileUpdater,
                              ChaosExperimentService chaosService) {
        this.filterChain = filterChain;
        this.webClient = webClient;
        this.circuitBreakerManager = circuitBreakerManager;
        this.metricsCollector = metricsCollector;
        this.eventProducer = eventProducer;
        this.auditLogger = auditLogger;
        this.profileUpdater = profileUpdater;
        this.chaosService = chaosService;
    }

    @RequestMapping("/api/**")
    public Mono<ResponseEntity<byte[]>> proxyRequest(ServerHttpRequest request) {
        return request.getBody()
                .reduce(new byte[0], (acc, buffer) -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    byte[] combined = new byte[acc.length + bytes.length];
                    System.arraycopy(acc, 0, combined, 0, acc.length);
                    System.arraycopy(bytes, 0, combined, acc.length, bytes.length);
                    return combined;
                })
                .defaultIfEmpty(new byte[0])
                .flatMap(body -> processRequest(request, body));
    }

    private Mono<ResponseEntity<byte[]>> processRequest(ServerHttpRequest request, byte[] body) {
        // Build RequestContext
        String clientIp = extractClientIp(request);
        HttpMethod method = request.getMethod() != null ? request.getMethod() : HttpMethod.GET;
        String path = request.getURI().getPath();
        HttpHeaders headers = request.getHeaders();

        RequestContext context = new RequestContext(clientIp, method, path, headers, body);
        log.debug("[{}] Incoming {} {} from {}", context.getRequestId(), method, path, clientIp);

        // Execute filter chain
        return filterChain.execute(context)
                .flatMap(finalResult -> {
                    long totalDurationMs = Duration.between(context.getReceivedAt(), Instant.now()).toMillis();

                    // Metrics + audit
                    metricsCollector.recordRequest(context, finalResult);
                    auditLogger.logRequest(context, finalResult);
                    eventProducer.publishAuditEvent(context, finalResult, totalDurationMs);

                    // If blocked/challenged/rate-limited/circuit-open, return error response
                    if (!finalResult.isAllowed()) {
                        return Mono.just(buildErrorResponse(finalResult));
                    }

                    // All filters passed — update behavior profile
                    profileUpdater.update(context);

                    // Apply chaos experiments (if enabled)
                    return chaosService.maybeApplyChaos(context)
                            .then(forwardToUpstream(context));
                })
                .onErrorResume(e -> {
                    log.error("[{}] Gateway error: {}", context.getRequestId(), e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(("{\"error\":\"Gateway error\",\"message\":\"" + e.getMessage() + "\"}")
                                    .getBytes()));
                });
    }

    /**
     * Forward the request to the resolved upstream service.
     */
    private Mono<ResponseEntity<byte[]>> forwardToUpstream(RequestContext context) {
        String targetUrl = context.getTargetUrl();
        String targetPath = context.getPath();
        String serviceId = context.getTargetServiceId();

        if (targetUrl == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"No upstream target resolved\"}".getBytes()));
        }

        String fullUrl = targetUrl + targetPath;
        Instant upstreamStart = Instant.now();

        WebClient.RequestBodySpec requestSpec = webClient.method(context.getMethod())
                .uri(fullUrl)
                .headers(h -> {
                    // Forward relevant headers, skip hop-by-hop headers
                    context.getHeaders().forEach((name, values) -> {
                        if (!isHopByHopHeader(name)) {
                            h.put(name, values);
                        }
                    });
                    // Add gateway-specific headers
                    h.set("X-Request-Id", context.getRequestId());
                    h.set("X-Forwarded-For", context.getClientIp());
                    if (context.getUserId() != null) {
                        h.set("X-User-Id", context.getUserId());
                    }
                });

        Mono<ResponseEntity<byte[]>> responseMono;
        if (context.getBody() != null && context.getBody().length > 0) {
            responseMono = requestSpec
                    .bodyValue(context.getBody())
                    .retrieve()
                    .toEntity(byte[].class);
        } else {
            responseMono = requestSpec
                    .retrieve()
                    .toEntity(byte[].class);
        }

        return responseMono
                .doOnSuccess(response -> {
                    Duration upstreamDuration = Duration.between(upstreamStart, Instant.now());
                    metricsCollector.recordUpstreamLatency(serviceId, upstreamDuration);
                    circuitBreakerManager.recordSuccess(serviceId);
                    log.debug("[{}] Upstream {} responded {} in {}ms",
                            context.getRequestId(), serviceId,
                            response.getStatusCode(), upstreamDuration.toMillis());
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    circuitBreakerManager.recordFailure(serviceId);
                    log.error("[{}] Upstream {} error: {} {}",
                            context.getRequestId(), serviceId, e.getStatusCode(), e.getMessage());
                    return Mono.just(ResponseEntity.status(e.getStatusCode())
                            .body(e.getResponseBodyAsByteArray()));
                })
                .onErrorResume(Exception.class, e -> {
                    circuitBreakerManager.recordFailure(serviceId);
                    log.error("[{}] Upstream {} connection error: {}",
                            context.getRequestId(), serviceId, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(("{\"error\":\"Upstream unavailable\",\"service\":\"" + serviceId + "\"}")
                                    .getBytes()));
                });
    }

    private ResponseEntity<byte[]> buildErrorResponse(FilterResult result) {
        String json = String.format(
                "{\"error\":\"%s\",\"filter\":\"%s\",\"decision\":\"%s\",\"reason\":\"%s\"}",
                HttpStatus.valueOf(result.httpStatus()).getReasonPhrase(),
                result.filterName(),
                result.decision().name(),
                result.reason()
        );
        return ResponseEntity.status(result.httpStatus())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(json.getBytes());
    }

    private String extractClientIp(ServerHttpRequest request) {
        // Check X-Forwarded-For first (for proxied requests)
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        // Check X-Real-IP
        String xri = request.getHeaders().getFirst("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri;
        }
        // Fall back to remote address
        var remoteAddr = request.getRemoteAddress();
        if (remoteAddr != null) {
            return remoteAddr.getAddress().getHostAddress();
        }
        return "0.0.0.0";
    }

    private boolean isHopByHopHeader(String headerName) {
        String lower = headerName.toLowerCase();
        return lower.equals("connection") || lower.equals("keep-alive") ||
               lower.equals("transfer-encoding") || lower.equals("te") ||
               lower.equals("trailer") || lower.equals("upgrade") ||
               lower.equals("proxy-authorization") || lower.equals("proxy-authenticate") ||
               lower.equals("host");
    }
}
