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
    // Define a standard 5-second tolerance for clock drift
    private static final int CLOCK_SKEW_SECONDS = 5;

    /**
     * Constructor that injects the JWT secret from application.properties.
     *
     * @param secret The secret string used for signing (must match User Service).
     */
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        // Create a secure SecretKey object from the string
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("JwtUtil initialized in API Gateway with clock skew tolerance: {} seconds", CLOCK_SKEW_SECONDS);
    }

    /**
     * Extracts all claims from a token.
     * This method validates the token's signature and expiration with tolerance.
     *
     * @param token The JWT string.
     * @return The Claims object.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                // ✅ FIX: Add clock skew tolerance
                .setAllowedClockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Checks if a token is expired.
     * Note: This function is less critical as Jwts.parserBuilder handles expiration.
     *
     * @param token The JWT string.
     * @return true if the token is expired, false otherwise.
     */
    private boolean isTokenExpired(String token) {
        try {
            // Rely on extractAllClaims to handle parsing and expiration with skew.
            // If extractAllClaims succeeds, it's not expired within the tolerance.
            extractAllClaims(token); 
            return false;
        } catch (Exception e) {
            // Catches ExpiredJwtException.
            log.warn("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Validates a token.
     * Checks if the signature is correct and if it's expired (using skew tolerance).
     *
     * @param token The JWT string.
     * @return true if the token is valid, false otherwise.
     */
    public boolean validateToken(String token) {
        try {
            // Parsing the claims now performs signature validation AND expiration check
            // with the configured 5-second tolerance. If it succeeds, the token is valid.
            extractAllClaims(token);
            return true; // Token is valid
        } catch (Exception e) {
            // Catches ExpiredJwtException, SignatureException, MalformedJwtException, etc.
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
}