package com.taskflow.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * API Gateway Security config:
 * - Disable CSRF (JWT-based)
 * - Provide CORS configuration for browser clients
 * - PermitAll at security-level so the Gateway GlobalFilter handles JWT validation / 401s
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            // Use our CORS source and disable CSRF (JWT)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            // Disable default login forms & http-basic
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

            // IMPORTANT: allow exchange through. AuthenticationFilter (GlobalFilter) will enforce JWT.
            // If you prefer stricter rules you can .pathMatchers(...).permitAll() and .anyExchange().authenticated()
            // but be sure the AuthenticationFilter is executed earlier than security blocking.
            .authorizeExchange(ex -> ex.anyExchange().permitAll());

        return http.build();
    }

    /**
     * CORS config for browser clients (Next.js running on :3000)
     * Exposes Authorization header and allows preflight.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();

        // change origin in production
        cors.setAllowedOrigins(List.of("http://localhost:3000"));
        cors.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS","PATCH"));
        cors.setAllowedHeaders(List.of("Authorization","Content-Type","X-Requested-With","X-User-Id","X-User-Role"));
        cors.setExposedHeaders(List.of("Authorization","X-User-Id","X-User-Role"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}