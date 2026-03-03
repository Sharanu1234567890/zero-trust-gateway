package com.ztgateway.geo;

import com.ztgateway.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Detects impossible travel: if a user was in City A X minutes ago
 * and is now in City B, and the distance/time implies faster-than-possible travel.
 */
@Service
public class ImpossibleTravelDetector {

    private static final Logger log = LoggerFactory.getLogger(ImpossibleTravelDetector.class);
    private static final String PREFIX = "geo:lastlogin:";
    private static final Duration HISTORY_TTL = Duration.ofHours(24);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final double thresholdKmh;

    public ImpossibleTravelDetector(ReactiveStringRedisTemplate redisTemplate, AppConfig appConfig) {
        this.redisTemplate = redisTemplate;
        this.thresholdKmh = appConfig.getGeo().getImpossibleTravelThresholdKmh();
    }

    public record LoginRecord(double latitude, double longitude, long epochMillis) {
        public String serialize() {
            return latitude + ":" + longitude + ":" + epochMillis;
        }

        public static LoginRecord deserialize(String raw) {
            String[] parts = raw.split(":");
            return new LoginRecord(
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Long.parseLong(parts[2])
            );
        }
    }

    /**
     * Check if the user's current location implies impossible travel.
     * Returns true if travel is impossible (should block).
     */
    public Mono<Boolean> checkAndUpdate(String userId, double latitude, double longitude) {
        String key = PREFIX + userId;
        long now = Instant.now().toEpochMilli();

        return redisTemplate.opsForValue().get(key)
                .flatMap(raw -> {
                    LoginRecord last = LoginRecord.deserialize(raw);
                    double distanceKm = haversineKm(last.latitude(), last.longitude(), latitude, longitude);
                    double hoursBetween = (now - last.epochMillis()) / 3_600_000.0;

                    if (hoursBetween <= 0.001) {
                        // Less than ~3.6 seconds apart — same session, allow
                        return updateRecord(key, latitude, longitude, now).thenReturn(false);
                    }

                    double speedKmh = distanceKm / hoursBetween;

                    if (speedKmh > thresholdKmh && distanceKm > 50) {
                        log.warn("Impossible travel detected for user={}: {}km in {:.2f}h = {:.0f}km/h",
                                userId, (int) distanceKm, hoursBetween, speedKmh);
                        return Mono.just(true);
                    }

                    return updateRecord(key, latitude, longitude, now).thenReturn(false);
                })
                .switchIfEmpty(updateRecord(key, latitude, longitude, now).thenReturn(false));
    }

    private Mono<Boolean> updateRecord(String key, double lat, double lon, long epochMs) {
        String record = new LoginRecord(lat, lon, epochMs).serialize();
        return redisTemplate.opsForValue().set(key, record, HISTORY_TTL);
    }

    /**
     * Haversine formula to calculate distance between two points on Earth.
     */
    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
