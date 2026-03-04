package com.ztgateway.core;

import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import reactor.core.publisher.Mono;

/**
 * Contract for all gateway filters. Each filter inspects the RequestContext
 * and returns a FilterResult indicating whether the request should proceed.
 */
public interface GatewayFilter {

    /**
     * @return the execution order of this filter
     */
    FilterOrder getOrder();

    /**
     * @return the human-readable name of this filter
     */
    String getName();

    /**
     * Execute this filter against the given request context.
     *
     * @param context the enriched request context
     * @return a Mono emitting the filter result
     */
    Mono<FilterResult> apply(RequestContext context);
}
