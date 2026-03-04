package com.ztgateway.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains the set of blocked countries. Backed by Redis with an in-memory cache.
 */
@Service
public class CountryBlocklistService {

    private static final Logger log = LoggerFactory.getLogger(CountryBlocklistService.class);
    private static final String REDIS_KEY = "geo:blocked_countries";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Set<String> localCache = ConcurrentHashMap.newKeySet();

    public CountryBlocklistService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        // Seed with OFAC-sanctioned countries
        localCache.addAll(Set.of("KP", "IR", "SY"));
        // Attempt to load from Redis (non-blocking, fire-and-forget)
        redisTemplate.opsForSet().members(REDIS_KEY)
                .doOnNext(localCache::add)
                .subscribe(
                        code -> {},
                        err -> log.warn("Could not load blocked countries from Redis: {}", err.getMessage())
                );
        log.info("Country blocklist initialized with {} entries", localCache.size());
    }

    public boolean isBlocked(String countryCode) {
        if (countryCode == null) return false;
        return localCache.contains(countryCode.toUpperCase());
    }

    public Mono<Boolean> addCountry(String countryCode) {
        String code = countryCode.toUpperCase();
        localCache.add(code);
        return redisTemplate.opsForSet().add(REDIS_KEY, code)
                .map(added -> added > 0)
                .doOnSuccess(ok -> log.info("Added {} to country blocklist", code));
    }

    public Mono<Boolean> removeCountry(String countryCode) {
        String code = countryCode.toUpperCase();
        localCache.remove(code);
        return redisTemplate.opsForSet().remove(REDIS_KEY, code)
                .map(removed -> removed > 0)
                .doOnSuccess(ok -> log.info("Removed {} from country blocklist", code));
    }

    public Set<String> getBlockedCountries() {
        return Set.copyOf(localCache);
    }
}
