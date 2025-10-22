package com.taskflow.apigateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.taskflow.apigateway.repository.BlacklistedTokenRepository;

import io.jsonwebtoken.Claims;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Centralized authentication filter for the API Gateway.
 * This global filter intercepts every request, validates the JWT,
 * and either permits or denies the request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationFilter implements GlobalFilter {

    private final JwtUtil jwtUtil;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    // List of public endpoints that do not require authentication
    private final List<String> publicEndpoints = List.of(
            "/auth/login",
            "/users" // This is for POST (registration)
    );

    /**
     * The main filter logic.
     *
     * @param exchange The current server request/response exchange.
     * @param chain    The filter chain to pass control to.
     * @return A Mono<Void> indicating completion.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. Check if the request is for a public endpoint
        if (isPublicEndpoint(path)) {
            log.debug("Request to public endpoint: {}. Permitting.", path);
            return chain.filter(exchange);
        }

        // 2. Get the Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // 3. Check if header is missing or doesn't start with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for: {}", path);
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }

        // 4. Extract the token
        String token = authHeader.substring(7); // Remove "Bearer " prefix

        // 5. Validate the token
        try {
            if (jwtUtil.validateToken(token)) {
                log.debug("Token validated successfully for: {}", path);
                
                Claims claims = jwtUtil.extractAllClaims(token); // Need claims to get signature indirectly
                String signature = token.substring(token.lastIndexOf('.') + 1); // Extract signature manually

                return blacklistedTokenRepository.findById(signature)
                        .flatMap(blacklistedToken -> {
                            // Token FOUND in blacklist - REJECT
                            log.warn("Token is blacklisted for path: {}", path);
                            return onError(exchange, "Token has been invalidated (logged out)", HttpStatus.UNAUTHORIZED);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            // Token NOT FOUND in blacklist - PROCEED
                            log.debug("Token not found in blacklist. Proceeding for path: {}", path);
                            
                            // Optional: Add user info headers
                            // exchange.getRequest().mutate()
                            //         .header("X-User-Id", claims.getSubject())
                            //         .header("X-User-Role", (String) claims.get("role"))
                            //         .build();
                                    
                            return chain.filter(exchange);
                        }));
            } else {
                log.warn("Invalid token received for: {}", path);
                return onError(exchange, "Invalid token", HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            log.error("Token validation error for: {}", path, e);
            return onError(exchange, "Token validation error", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Helper method to check if a path is public.
     *
     * @param path The request path.
     * @return true if the endpoint is public, false otherwise.
     */
    private boolean isPublicEndpoint(String path) {
        // We use startsWith to allow for /users (POST) and /users/some-id (GET)
        // Note: Our POST /users rule is fine, but GET /users/{id} will be blocked
        // This is correct. Let's refine this to be more exact.
        
        // Exact match for login
        if (path.equals("/auth/login")) {
            return true;
        }
        
        // Exact match for user registration
        if (path.equals("/users")) {
             return true;
        }

        // All other endpoints are considered secured
        return false;
    }

    /**
     * Helper method to build a 401 Unauthorized error response.
     *
     * @param exchange The ServerWebExchange.
     * @param err      The error message.
     * @param httpStatus The HTTP status.
     * @return A Mono<Void> to terminate the request.
     */
    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        
        // We create a simple JSON error response
        String errorJson = "{\"status\": " + httpStatus.value() + ", \"error\": \"" + err + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(errorJson.getBytes());
        
        return response.writeWith(Mono.just(buffer));
    }
}