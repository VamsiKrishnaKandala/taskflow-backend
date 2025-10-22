package com.taskflow.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utility class for handling JWT (JSON Web Token) operations
 * specifically for the API Gateway.
 *
 * This class is only responsible for VALIDATING and PARSING tokens.
 */
@Component
@Slf4j
public class JwtUtil {

    private final SecretKey secretKey;

    /**
     * Constructor that injects the JWT secret from application.properties.
     *
     * @param secret The secret string used for signing (must match User Service).
     */
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        // Create a secure SecretKey object from the string
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("JwtUtil initialized in API Gateway");
    }

    /**
     * Extracts all claims from a token.
     * This method also validates the token's signature.
     *
     * @param token The JWT string.
     * @return The Claims object.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Checks if a token is expired.
     *
     * @param token The JWT string.
     * @return true if the token is expired, false otherwise.
     */
    private boolean isTokenExpired(String token) {
        try {
            return extractAllClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            log.warn("Error checking token expiration: {}", e.getMessage());
            return true; // Assume expired if we can't parse it
        }
    }

    /**
     * Validates a token.
     * Checks if the signature is correct and if it's expired.
     *
     * @param token The JWT string.
     * @return true if the token is valid, false otherwise.
     */
    public boolean validateToken(String token) {
        try {
            // Parsing the token with the secret key automatically validates the signature.
            // If it fails (e.g., wrong signature), it throws an exception.
            extractAllClaims(token);
            
            // If parsing succeeds, we just check for expiration.
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
}