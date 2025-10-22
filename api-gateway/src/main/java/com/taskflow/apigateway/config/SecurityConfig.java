package com.taskflow.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configures the default Spring Security filter chain in the Gateway.
 * We disable all default security (CSRF, login pages) so that our
 * custom AuthenticationFilter is the only thing managing security.
 */
@Configuration
@EnableWebFluxSecurity
@Slf4j
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        log.info("Configuring gateway security... Disabling CSRF and default auth.");
        
        return http
                // 1. Disable CSRF - This is the fix for your 403 error
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                
                // 2. Disable default login/logout pages
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                
                // 3. Disable HTTP Basic auth
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                
                // 4. Tell this filter chain to PERMIT ALL requests
                // This is critical. It lets requests pass through to our
                // *custom* AuthenticationFilter, which will handle the 401s.
                .authorizeExchange(exchange -> exchange
                        .anyExchange().permitAll()
                )
                .build();
    }
}