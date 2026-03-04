package com.ztgateway.threat.behavioral;

import com.ztgateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Updates user behavior profiles based on successful (non-blocked) requests.
 * Only updates when a request passes all filters — we don't learn from attacks.
 */
@Service
public class ProfileUpdater {

    private static final Logger log = LoggerFactory.getLogger(ProfileUpdater.class);
    private static final int MAX_USER_AGENTS = 10;
    private static final int MAX_ROUTES = 100;

    private final BehaviorProfileStore store;

    public ProfileUpdater(BehaviorProfileStore store) {
        this.store = store;
    }

    /**
     * Update the user's behavior profile with data from this request.
     */
    public void update(RequestContext context) {
        String userId = context.getUserId();
        if (userId == null) return;

        BehaviorProfileStore.UserProfile existing = store.getProfile(userId);
        int currentHour = ZonedDateTime.now(ZoneOffset.UTC).getHour();
        String normalizedPath = context.getPath().replaceAll("/[0-9a-fA-F-]{8,}", "/{id}");

        if (existing == null) {
            // Create new profile
            Set<String> userAgents = new HashSet<>();
            if (context.getUserAgent() != null) userAgents.add(context.getUserAgent());

            Set<Integer> hours = new HashSet<>();
            hours.add(currentHour);

            Set<String> routes = new HashSet<>();
            routes.add(normalizedPath);

            store.updateProfile(userId, new BehaviorProfileStore.UserProfile(
                    userAgents, hours, routes, 1.0,
                    context.getClientIp(), context.getCountryCode()
            ));
            log.debug("Created behavior profile for user={}", userId);
        } else {
            // Update existing profile
            Set<String> userAgents = new HashSet<>(existing.typicalUserAgents());
            if (context.getUserAgent() != null && userAgents.size() < MAX_USER_AGENTS) {
                userAgents.add(context.getUserAgent());
            }

            Set<Integer> hours = new HashSet<>(existing.typicalHours());
            hours.add(currentHour);

            Set<String> routes = new HashSet<>(existing.typicalRoutes());
            if (routes.size() < MAX_ROUTES) {
                routes.add(normalizedPath);
            }

            store.updateProfile(userId, new BehaviorProfileStore.UserProfile(
                    userAgents, hours, routes,
                    existing.avgRequestRate() * 0.9 + 0.1, // exponential moving average
                    context.getClientIp(),
                    context.getCountryCode() != null ? context.getCountryCode() : existing.lastKnownCountry()
            ));
        }
    }
}
