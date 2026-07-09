package com.example.ecomserver.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Optional shared-secret guard for the MCP endpoint.
 *
 * <p>When {@code ecom.security.api-key} (env {@code ECOM_SECURITY_API_KEY}) is set, every
 * request except the actuator health check must present the key, either as
 * {@code Authorization: Bearer <key>} or {@code X-API-Key: <key>}. When the property is
 * blank (the default) the filter is a no-op, so local development and tests need no config.
 *
 * <p>This is a deliberately minimal alternative to full OAuth2 - enough to stop an
 * unauthenticated public URL from being wide open. For multi-client or user-scoped access,
 * replace this with a Spring Security OAuth2 resource server.
 */
@Component
public class McpApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpApiKeyAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final String apiKey;
    private final boolean enabled;

    public McpApiKeyAuthFilter(@Value("${ecom.security.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = !this.apiKey.isEmpty();
        if (enabled) {
            log.info("MCP API key auth ENABLED - requests must send the configured key.");
        } else {
            log.warn("MCP API key auth DISABLED (ecom.security.api-key not set) - the MCP "
                    + "endpoint is unauthenticated. Set ECOM_SECURITY_API_KEY before exposing it publicly.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // No key configured -> never enforce. Health check must stay open for load balancers.
        return !enabled || request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isAuthorized(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer realm=\"mcp\"");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\",\"message\":"
                + "\"Missing or invalid API key. Send 'Authorization: Bearer <key>' or 'X-API-Key: <key>'.\"}");
    }

    private boolean isAuthorized(HttpServletRequest request) {
        String presented = extractKey(request);
        return presented != null && constantTimeEquals(presented, apiKey);
    }

    private String extractKey(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return auth.substring(BEARER_PREFIX.length()).trim();
        }
        String apiKeyHeader = request.getHeader("X-API-Key");
        return apiKeyHeader == null ? null : apiKeyHeader.trim();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
