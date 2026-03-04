package com.ztgateway.filter;

import com.ztgateway.auth.JwtValidator;
import com.ztgateway.auth.RbacService;
import com.ztgateway.auth.TokenBlacklistService;
import com.ztgateway.core.FilterOrder;
import com.ztgateway.core.GatewayFilter;
import com.ztgateway.model.FilterResult;
import com.ztgateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checkpoint 2 — Auth Filter
 * - Extracts and validates JWT from Authorization header
 * - Checks token blacklist (revoked tokens)
 * - Extracts userId and roles into RequestContext
 * - Performs RBAC check
 */
@Component
public class AuthFilter implements GatewayFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    // Paths that don't require authentication
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/health", "/actuator", "/api/auth/login", "/api/auth/register", "/api/auth/token"
    );

    private final JwtValidator jwtValidator;
    private final TokenBlacklistService blacklistService;
    private final RbacService rbacService;

    public AuthFilter(JwtValidator jwtValidator,
                      TokenBlacklistService blacklistService,
                      RbacService rbacService) {
        this.jwtValidator = jwtValidator;
        this.blacklistService = blacklistService;
        this.rbacService = rbacService;
    }

    @Override
    public FilterOrder getOrder() { return FilterOrder.AUTH; }

    @Override
    public String getName() { return "AuthFilter"; }

    @Override
    public Mono<FilterResult> apply(RequestContext context) {
        // Skip auth for public paths
        if (isPublicPath(context.getPath())) {
            return Mono.just(FilterResult.allow(getName()));
        }

        // Extract token
        String token = context.getAuthorizationToken();
        if (token == null) {
            return Mono.just(FilterResult.block(getName(), 401,
                    "Missing Authorization header"));
        }

        // Validate JWT signature, expiry, issuer
        Optional<JwtValidator.TokenData> tokenDataOpt = jwtValidator.validate(token);
        if (tokenDataOpt.isEmpty()) {
            return Mono.just(FilterResult.block(getName(), 401,
                    "Invalid or expired token"));
        }

        JwtValidator.TokenData tokenData = tokenDataOpt.get();

        // Check blacklist
        return blacklistService.isBlacklisted(tokenData.jti())
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.warn("Blacklisted token used: jti={} userId={}", tokenData.jti(), tokenData.userId());
                        return Mono.just(FilterResult.block(getName(), 401,
                                "Token has been revoked"));
                    }

                    // Set user info in context
                    context.setUserId(tokenData.userId());
                    context.setRoles(tokenData.roles());

                    // RBAC check
                    if (!rbacService.isAuthorized(context.getPath(), tokenData.roles())) {
                        log.warn("RBAC denied: user={} roles={} path={}",
                                tokenData.userId(), tokenData.roles(), context.getPath());
                        return Mono.just(FilterResult.block(getName(), 403,
                                "Insufficient permissions",
                                Map.of("requiredPath", context.getPath())));
                    }

                    return Mono.just(FilterResult.allow(getName()));
                });
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
