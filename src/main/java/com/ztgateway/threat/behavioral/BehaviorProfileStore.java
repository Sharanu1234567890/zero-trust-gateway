package com.ztgateway.threat.behavioral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for user behavior profiles.
 * In production, this would be backed by a database or Redis.
 */
@Service
public class BehaviorProfileStore {

    private static final Logger log = LoggerFactory.getLogger(BehaviorProfileStore.class);

    // userId -> profile data
    private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();

    public record UserProfile(
            Set<String> typicalUserAgents,
            Set<Integer> typicalHours,
            Set<String> typicalRoutes,
            double avgRequestRate,
            String lastKnownIp,
            String lastKnownCountry
    ) {}

    public Set<Integer> getTypicalHours(String userId) {
        UserProfile profile = profiles.get(userId);
        return profile != null ? profile.typicalHours() : Set.of();
    }

    public Set<String> getTypicalUserAgents(String userId) {
        UserProfile profile = profiles.get(userId);
        return profile != null ? profile.typicalUserAgents() : Set.of();
    }

    public Set<String> getTypicalRoutes(String userId) {
        UserProfile profile = profiles.get(userId);
        return profile != null ? profile.typicalRoutes() : Set.of();
    }

    public UserProfile getProfile(String userId) {
        return profiles.get(userId);
    }

    public void updateProfile(String userId, UserProfile profile) {
        profiles.put(userId, profile);
        log.debug("Updated behavior profile for user={}", userId);
    }

    public boolean hasProfile(String userId) {
        return profiles.containsKey(userId);
    }
}
