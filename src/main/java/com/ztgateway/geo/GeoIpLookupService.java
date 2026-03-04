package com.ztgateway.geo;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.ztgateway.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;

/**
 * Resolves IP addresses to geographic locations using MaxMind GeoIP2.
 * Falls back to a simple lookup when the database is not available.
 */
@Service
public class GeoIpLookupService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpLookupService.class);

    private final AppConfig appConfig;
    private DatabaseReader dbReader;
    private boolean available = false;

    public GeoIpLookupService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @PostConstruct
    public void init() {
        String path = appConfig.getGeo().getMaxmindDbPath();
        if (path != null && !path.isBlank()) {
            try {
                File dbFile = new File(path);
                if (dbFile.exists()) {
                    dbReader = new DatabaseReader.Builder(dbFile).build();
                    available = true;
                    log.info("GeoIP database loaded from: {}", path);
                } else {
                    log.warn("GeoIP database file not found at: {} — using fallback", path);
                }
            } catch (IOException e) {
                log.error("Failed to load GeoIP database: {}", e.getMessage());
            }
        } else {
            log.info("No GeoIP database configured — using fallback geo resolution");
        }
    }

    public record GeoLocation(
            String countryCode,
            String city,
            double latitude,
            double longitude
    ) {}

    /**
     * Lookup the geographic location for an IP address.
     */
    public Optional<GeoLocation> lookup(String ipAddress) {
        if (!available || dbReader == null) {
            return fallbackLookup(ipAddress);
        }
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            CityResponse response = dbReader.city(addr);
            return Optional.of(new GeoLocation(
                    response.getCountry().getIsoCode(),
                    response.getCity().getName(),
                    response.getLocation().getLatitude(),
                    response.getLocation().getLongitude()
            ));
        } catch (IOException | GeoIp2Exception e) {
            log.debug("GeoIP lookup failed for {}: {}", ipAddress, e.getMessage());
            return fallbackLookup(ipAddress);
        }
    }

    /**
     * Simple fallback for when the GeoIP database is unavailable.
     * Returns UNKNOWN country, which will NOT be blocked by the blocklist
     * (we only block known bad countries, not unknown ones).
     */
    private Optional<GeoLocation> fallbackLookup(String ipAddress) {
        // Private/loopback IPs
        if (ipAddress.startsWith("127.") || ipAddress.startsWith("10.") ||
            ipAddress.startsWith("192.168.") || ipAddress.startsWith("172.") ||
            "0:0:0:0:0:0:0:1".equals(ipAddress) || "::1".equals(ipAddress)) {
            return Optional.of(new GeoLocation("LOCAL", "localhost", 0.0, 0.0));
        }
        return Optional.of(new GeoLocation("UNKNOWN", "unknown", 0.0, 0.0));
    }

    public boolean isAvailable() {
        return available;
    }
}
