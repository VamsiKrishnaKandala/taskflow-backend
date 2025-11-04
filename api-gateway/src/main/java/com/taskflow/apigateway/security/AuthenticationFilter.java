package com.taskflow.apigateway.security;

import com.taskflow.apigateway.repository.BlacklistedTokenRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationFilter implements GlobalFilter {

    private final JwtUtil jwtUtil;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/auth/login",
            "/users" // but only POST allowed; method check below
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // Allow preflight immediately
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }

        // Allow public endpoints
        if (path.equals("/auth/login") || (path.equals("/users") && HttpMethod.POST.equals(method))) {
            log.debug("Gateway: allowing public endpoint {} {}", method, path);
            return chain.filter(exchange);
        }

        // Check Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Gateway: Missing or invalid Authorization header for {}", path);
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            if (!jwtUtil.validateToken(token)) {
                log.warn("Gateway: Invalid/expired token for {}", path);
                return writeError(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT");
            }

            Claims claims = jwtUtil.extractAllClaims(token);
            String signature = token.substring(token.lastIndexOf('.') + 1);

            return blacklistedTokenRepository.findById(signature)
                    .flatMap(blk -> {
                        log.warn("Gateway: token signature {} is blacklisted", signature);
                        return writeError(exchange, HttpStatus.UNAUTHORIZED, "Token has been invalidated");
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        // attach headers and proceed
                        String userId = claims.getSubject();
                        String userRole = claims.get("role", String.class);

                        ServerHttpRequest mutated = exchange.getRequest().mutate()
                                .header("X-User-Id", userId != null ? userId : "UNKNOWN")
                                .header("X-User-Role", userRole != null ? userRole : "UNKNOWN")
                                .build();

                        return chain.filter(exchange.mutate().request(mutated).build());
                    }));

        } catch (Exception e) {
            log.error("Gateway: token processing error for {}: {}", path, e.getMessage(), e);
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "Token validation error");
        }
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        String body = String.format("{\"status\": %d, \"error\": \"%s\"}", status.value(), msg);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
