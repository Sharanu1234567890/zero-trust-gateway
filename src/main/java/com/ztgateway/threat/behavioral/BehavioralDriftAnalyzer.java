package com.ztgateway.threat.behavioral;

import com.ztgateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Analyzes behavioral drift — how much a request deviates from the user's
 * established behavior profile.
 */
@Service
public class BehavioralDriftAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(BehavioralDriftAnalyzer.class);

    private final BehaviorProfileStore profileStore;

    public BehavioralDriftAnalyzer(BehaviorProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    public record DriftReport(
            double overallDrift,    // 0.0 (no drift) to 1.0 (total anomaly)
            boolean userAgentDrift,
            boolean temporalDrift,
            boolean routeDrift,
            boolean geoDrift
    ) {}

    /**
     * Analyze the request for behavioral drift from the user's profile.
     */
    public DriftReport analyze(RequestContext context) {
        String userId = context.getUserId();
        if (userId == null || !profileStore.hasProfile(userId)) {
            // No profile yet — no drift measurable
            return new DriftReport(0.0, false, false, false, false);
        }

        BehaviorProfileStore.UserProfile profile = profileStore.getProfile(userId);
        double driftScore = 0.0;
        int factors = 0;

        // User-Agent drift
        boolean uaDrift = false;
        String currentUa = context.getUserAgent();
        if (currentUa != null && !profile.typicalUserAgents().isEmpty()) {
            uaDrift = profile.typicalUserAgents().stream().noneMatch(currentUa::contains);
            if (uaDrift) driftScore += 1.0;
            factors++;
        }

        // Temporal drift
        boolean tempDrift = false;
        int currentHour = ZonedDateTime.now(ZoneOffset.UTC).getHour();
        if (!profile.typicalHours().isEmpty()) {
            tempDrift = !profile.typicalHours().contains(currentHour);
            if (tempDrift) driftScore += 1.0;
            factors++;
        }

        // Route drift
        boolean rtDrift = false;
        Set<String> routes = profile.typicalRoutes();
        if (!routes.isEmpty()) {
            String normalizedPath = context.getPath().replaceAll("/[0-9a-fA-F-]{8,}", "/{id}");
            rtDrift = !routes.contains(normalizedPath);
            if (rtDrift) driftScore += 1.0;
            factors++;
        }

        // Geo drift
        boolean gDrift = false;
        String currentCountry = context.getCountryCode();
        if (currentCountry != null && profile.lastKnownCountry() != null) {
            gDrift = !currentCountry.equals(profile.lastKnownCountry());
            if (gDrift) driftScore += 1.0;
            factors++;
        }

        double overall = factors > 0 ? driftScore / factors : 0.0;

        if (overall > 0.5) {
            log.warn("High behavioral drift for user={}: overall={:.2f} ua={} temporal={} route={} geo={}",
                    userId, overall, uaDrift, tempDrift, rtDrift, gDrift);
        }

        return new DriftReport(overall, uaDrift, tempDrift, rtDrift, gDrift);
    }
}
