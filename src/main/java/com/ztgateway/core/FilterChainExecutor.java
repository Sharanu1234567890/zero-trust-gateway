package com.ztgateway.core;

import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import com.ztgateway.monitoring.GatewayEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * Executes the filter chain in order. If any filter returns a non-ALLOW result,
 * the chain short-circuits and returns that result immediately.
 */
@Component
public class FilterChainExecutor {

    private static final Logger log = LoggerFactory.getLogger(FilterChainExecutor.class);

    private final List<GatewayFilter> orderedFilters;
    private final GatewayEventProducer eventProducer;

    public FilterChainExecutor(List<GatewayFilter> filters, GatewayEventProducer eventProducer) {
        this.orderedFilters = filters.stream()
                .sorted(Comparator.comparingInt(f -> f.getOrder().getOrder()))
                .toList();
        this.eventProducer = eventProducer;

        log.info("Filter chain initialized with {} filters: {}", orderedFilters.size(),
                orderedFilters.stream().map(GatewayFilter::getName).toList());
    }

    /**
     * Run all filters sequentially. Short-circuit on the first non-ALLOW result.
     */
    public Mono<FilterResult> execute(RequestContext context) {
        return Flux.fromIterable(orderedFilters)
                .concatMap(filter -> {
                    long start = System.nanoTime();
                    return filter.apply(context)
                            .doOnNext(result -> {
                                long durationMs = (System.nanoTime() - start) / 1_000_000;
                                context.addFilterResult(result);
                                log.debug("[{}] filter={} decision={} duration={}ms reason={}",
                                        context.getRequestId(), filter.getName(),
                                        result.decision(), durationMs, result.reason());
                                eventProducer.publishFilterEvent(context, result, durationMs);
                            });
                })
                .takeUntil(result -> !result.isAllowed())
                .last()
                .defaultIfEmpty(FilterResult.allow("chain"));
    }
}
