package com.ztgateway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Handles OAuth2 token exchange flows (authorization code, client credentials).
 * In production, this would integrate with an IdP like Keycloak, Auth0, Okta, etc.
 */
@Component
public class OAuth2Handler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2Handler.class);

    private final JwtProvider jwtProvider;

    public OAuth2Handler(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * Exchange an authorization code for a gateway JWT.
     * In a real system, this validates the code with the IdP first.
     */
    public Mono<Map<String, String>> exchangeAuthorizationCode(String code, String redirectUri) {
        // In production: validate code with IdP, get user info, then issue our JWT
        log.info("OAuth2 code exchange requested (code={})", code);
        return Mono.error(new UnsupportedOperationException(
                "OAuth2 code exchange requires IdP configuration. " +
                "Configure your IdP (Keycloak/Auth0/Okta) and implement the token exchange."));
    }

    /**
     * Client credentials flow — for service-to-service auth.
     */
    public Mono<Map<String, String>> clientCredentials(String clientId, String clientSecret) {
        log.info("OAuth2 client credentials requested (clientId={})", clientId);
        return Mono.error(new UnsupportedOperationException(
                "OAuth2 client credentials flow requires IdP configuration."));
    }
}
