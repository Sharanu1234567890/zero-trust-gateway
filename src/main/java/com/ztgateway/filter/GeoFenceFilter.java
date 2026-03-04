package com.ztgateway.filter;

import com.ztgateway.core.FilterOrder;
import com.ztgateway.core.GatewayFilter;
import com.ztgateway.geo.CountryBlocklistService;
import com.ztgateway.geo.GeoIpLookupService;
import com.ztgateway.geo.ImpossibleTravelDetector;
import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Checkpoint 1 — GeoFence Filter
 * - Resolves client IP to country
 * - Checks country blocklist
 * - Detects impossible travel
 */
@Component
public class GeoFenceFilter implements GatewayFilter {

    private static final Logger log = LoggerFactory.getLogger(GeoFenceFilter.class);

    private final GeoIpLookupService geoIpService;
    private final CountryBlocklistService blocklistService;
    private final ImpossibleTravelDetector travelDetector;

    public GeoFenceFilter(GeoIpLookupService geoIpService,
                          CountryBlocklistService blocklistService,
                          ImpossibleTravelDetector travelDetector) {
        this.geoIpService = geoIpService;
        this.blocklistService = blocklistService;
        this.travelDetector = travelDetector;
    }

    @Override
    public FilterOrder getOrder() { return FilterOrder.GEO_FENCE; }

    @Override
    public String getName() { return "GeoFenceFilter"; }

    @Override
    public Mono<FilterResult> apply(RequestContext context) {
        // Resolve IP to location
        var geoOpt = geoIpService.lookup(context.getClientIp());

        if (geoOpt.isEmpty()) {
            log.debug("Could not resolve geo for IP={}", context.getClientIp());
            return Mono.just(FilterResult.allow(getName()));
        }

        GeoIpLookupService.GeoLocation geo = geoOpt.get();
        context.setCountryCode(geo.countryCode());
        context.setCity(geo.city());
        context.setLatitude(geo.latitude());
        context.setLongitude(geo.longitude());

        // Check country blocklist
        if (blocklistService.isBlocked(geo.countryCode())) {
            log.warn("Blocked request from country={} ip={}", geo.countryCode(), context.getClientIp());
            return Mono.just(FilterResult.block(getName(), 403,
                    "Request origin blocked",
                    Map.of("country", geo.countryCode())));
        }

        // Check impossible travel (only if we have a userId — set by previous auth or from header)
        String userId = context.getUserId();
        if (userId != null && geo.latitude() != 0.0 && geo.longitude() != 0.0) {
            return travelDetector.checkAndUpdate(userId, geo.latitude(), geo.longitude())
                    .map(isImpossible -> {
                        if (isImpossible) {
                            log.warn("Impossible travel detected for user={} ip={} country={}",
                                    userId, context.getClientIp(), geo.countryCode());
                            return FilterResult.block(getName(), 403,
                                    "Impossible travel detected",
                                    Map.of("country", geo.countryCode(), "city", geo.city()));
                        }
                        return FilterResult.allow(getName());
                    });
        }

        return Mono.just(FilterResult.allow(getName()));
    }
}
