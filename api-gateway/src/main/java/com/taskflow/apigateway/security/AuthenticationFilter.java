package com.taskflow.apigateway.security;

import com.taskflow.apigateway.repository.BlacklistedTokenRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // Added
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets; // Added
import java.util.List;

/**
 * Centralized authentication filter for the API Gateway.
 * Intercepts requests, validates JWT, checks blacklist, adds user headers,
 * and permits or denies the request based on authentication status.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationFilter implements GlobalFilter {

    private final JwtUtil jwtUtil;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    // Defines endpoints accessible without authentication.
    private final List<String> publicEndpoints = List.of(
            "/auth/login", // User login
            "/users"       // User registration (POST only, checked by method in isPublicEndpoint)
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Allow public endpoints
        if (isPublicEndpoint(request)) { // Pass request to check method if needed
            log.debug("Permitting public endpoint request: {} {}", request.getMethod(), path);
            return chain.filter(exchange);
        }

        // Check for Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for protected path: {}", path);
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        try {
            // Validate token signature and expiry
            if (!jwtUtil.validateToken(token)) {
                log.warn("Invalid token signature or expiry for path: {}", path);
                return onError(exchange, "Invalid token", HttpStatus.UNAUTHORIZED);
            }

            // Check blacklist and add headers
            Claims claims = jwtUtil.extractAllClaims(token);
            String signature = token.substring(token.lastIndexOf('.') + 1);

            return blacklistedTokenRepository.findById(signature)
                    .flatMap(blacklistedToken -> {
                        // Token is blacklisted - REJECT
                        log.warn("Token signature [{}] is blacklisted (logged out) for path: {}", signature, path);
                        return onError(exchange, "Token has been invalidated", HttpStatus.UNAUTHORIZED);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        // Token NOT blacklisted - Proceed and add headers
                        log.debug("Token signature [{}] not blacklisted. Adding headers and forwarding...", signature);
                        return addHeadersAndProceed(exchange, chain, claims);
                    }));

        } catch (Exception e) {
            // Catch errors during token validation/parsing
            log.error("Token processing error for path: {}", path, e);
            return onError(exchange, "Token validation error", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Checks if the request targets a defined public endpoint.
     * Considers both path and potentially HTTP method.
     */
    private boolean isPublicEndpoint(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        // Exact match for login
        if (path.equals("/auth/login")) {
            return true;
        }
        // Allow ONLY POST requests to /users for registration
        if (path.equals("/users") && request.getMethod() == org.springframework.http.HttpMethod.POST) {
             return true;
        }
        // All other endpoints require authentication
        return false;
    }

    /**
     * Extracts user info from claims, adds them as headers, and continues the filter chain.
     */
    private Mono<Void> addHeadersAndProceed(ServerWebExchange exchange, GatewayFilterChain chain, Claims claims) {
        String userId = claims.getSubject();
        String userRole = claims.get("role", String.class);

        // Log extracted values (DEBUG level recommended)
        log.debug("Adding headers -> X-User-Id: {}, X-User-Role: {}", userId, userRole);

        if (userRole == null) {
             log.warn("User role claim ('role') is missing or null in JWT for user ID: {}. Downstream authorization might fail.", userId);
             // Decide if missing role is critical:
             // return onError(exchange, "User role missing in token", HttpStatus.UNAUTHORIZED);
        }

        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", (userId != null) ? userId : "UNKNOWN") // Add header, provide default if null
                .header("X-User-Role", (userRole != null) ? userRole : "UNKNOWN") // Add header, provide default if null
                .build();

        ServerWebExchange modifiedExchange = exchange.mutate().request(modifiedRequest).build();

        return chain.filter(modifiedExchange);
    }


    /**
     * Helper to build a standard JSON error response and terminate the request.
     */
    private Mono<Void> onError(ServerWebExchange exchange, String errorMsg, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        String errorJson = String.format("{\"status\": %d, \"error\": \"%s\"}", httpStatus.value(), errorMsg);
        DataBuffer buffer = response.bufferFactory().wrap(errorJson.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}