package com.ztgateway.auth;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Role-Based Access Control. Maps routes to required roles.
 */
@Service
public class RbacService {

    // Route prefix -> set of allowed roles. Empty set = any authenticated user.
    private static final Map<String, Set<String>> ROUTE_ROLES = Map.of(
            "/api/payments", Set.of("ADMIN", "PAYMENT_OPERATOR"),
            "/api/users", Set.of("ADMIN", "USER_MANAGER"),
            "/api/orders", Set.of() // any authenticated user
    );

    /**
     * Check if the given roles are authorized for the given path.
     * Returns true if:
     *   - no role mapping exists for the path (open to authenticated users)
     *   - role set is empty (any authenticated user)
     *   - user has at least one of the required roles
     */
    public boolean isAuthorized(String path, List<String> roles) {
        for (Map.Entry<String, Set<String>> entry : ROUTE_ROLES.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                Set<String> requiredRoles = entry.getValue();
                if (requiredRoles.isEmpty()) {
                    return true; // any authenticated user
                }
                return roles.stream().anyMatch(requiredRoles::contains);
            }
        }
        // No explicit mapping: allow any authenticated user
        return true;
    }
}
