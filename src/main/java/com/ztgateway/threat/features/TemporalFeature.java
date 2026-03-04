package com.ztgateway.threat.features;

import com.ztgateway.threat.behavioral.BehaviorProfileStore;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Extracts temporal features: hour of day and whether it matches the user's typical pattern.
 */
@Component
public class TemporalFeature {

    private final BehaviorProfileStore profileStore;

    public TemporalFeature(BehaviorProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    /**
     * Returns [normalizedHourOfDay, isTypicalHour].
     */
    public float[] extract(String userId) {
        int currentHour = ZonedDateTime.now(ZoneOffset.UTC).getHour();
        float hourNorm = currentHour / 24.0f;

        Set<Integer> typicalHours = profileStore.getTypicalHours(userId);
        float isTypical = typicalHours.isEmpty() ? 1.0f : // no profile yet, assume typical
                (typicalHours.contains(currentHour) ? 1.0f : 0.0f);

        return new float[] { hourNorm, isTypical };
    }
}
