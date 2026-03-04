package com.ztgateway.shadow;

import com.ztgateway.config.AppConfig;
import com.ztgateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Silently copies blocked/suspicious requests to a shadow analysis service.
 * This is fire-and-forget — it never affects the original request flow.
 */
@Service
public class ShadowRouter {

    private static final Logger log = LoggerFactory.getLogger(ShadowRouter.class);

    private final boolean enabled;
    private final String targetUrl;
    private final WebClient webClient;

    public ShadowRouter(AppConfig appConfig, WebClient webClient) {
        this.enabled = appConfig.getShadow().isEnabled();
        this.targetUrl = appConfig.getShadow().getTargetUrl();
        this.webClient = webClient;
    }

    /**
     * Send a copy of the request to the shadow service for analysis.
     * Fire-and-forget: errors are logged but never propagated.
     */
    public void shadowCopy(RequestContext context) {
        if (!enabled || targetUrl == null) return;

        Mono.defer(() -> webClient.post()
                        .uri(targetUrl + "/shadow/ingest")
                        .bodyValue(buildShadowPayload(context))
                        .retrieve()
                        .toBodilessEntity()
                        .then())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> log.debug("Shadow copy sent for request={}", context.getRequestId()),
                        error -> log.warn("Shadow copy failed for request={}: {}",
                                context.getRequestId(), error.getMessage())
                );
    }

    private java.util.Map<String, Object> buildShadowPayload(RequestContext context) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("requestId", context.getRequestId());
        payload.put("clientIp", context.getClientIp());
        payload.put("method", context.getMethod().name());
        payload.put("path", context.getPath());
        payload.put("userId", context.getUserId());
        payload.put("countryCode", context.getCountryCode());
        payload.put("userAgent", context.getUserAgent());
        payload.put("body", context.getBodyAsString());
        if (context.getThreatScore() != null) {
            payload.put("threatScore", context.getThreatScore().score());
            payload.put("threatExplanation", context.getThreatScore().explanation());
        }
        payload.put("filterResults", context.getFilterResults().stream()
                .map(fr -> java.util.Map.of(
                        "filter", fr.filterName(),
                        "decision", fr.decision().name(),
                        "reason", fr.reason()))
                .toList());
        return payload;
    }

    public boolean isEnabled() { return enabled; }
}
