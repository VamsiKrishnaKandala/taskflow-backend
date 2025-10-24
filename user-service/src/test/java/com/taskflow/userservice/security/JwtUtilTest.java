package com.taskflow.userservice.security;

import com.taskflow.userservice.model.Role;
import com.taskflow.userservice.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtUtil.
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private User testUser;
    private final String testSecret = "TestSecretKeyForJwtUtilWhichIsDefinitelyLongEnoughAndSecure12345";
    private final long testExpirationMs = TimeUnit.HOURS.toMillis(1); // 1 hour expiration

    @BeforeEach
    void setUp() {
        // Instantiate JwtUtil directly with test values
        jwtUtil = new JwtUtil(testSecret, testExpirationMs);

        // Create a sample user for generating tokens
        testUser = User.builder()
                .id("user-test-123")
                .name("Test User")
                .email("test@example.com")
                .role(Role.ROLE_EMPLOYEE)
                .password("hashedPassword") // Not used by JwtUtil
                .build();
    }

    @Nested
    @DisplayName("Token Generation Tests")
    class GenerationTests {

        @Test
        @DisplayName("Should generate a non-empty token")
        void generateToken_ReturnsNonEmptyString() {
            String token = jwtUtil.generateToken(testUser);
            assertNotNull(token);
            assertFalse(token.isEmpty());
            assertTrue(token.split("\\.").length == 3, "Token should have 3 parts");
        }

        @Test
        @DisplayName("Generated token should contain correct claims")
        void generateToken_ContainsCorrectClaims() {
            String token = jwtUtil.generateToken(testUser);
            Claims claims = jwtUtil.extractAllClaims(token); // Use extractAllClaims from the SAME instance

            assertEquals(testUser.getId(), claims.getSubject(), "Subject (user ID) should match");
            assertEquals(testUser.getRole().name(), claims.get("role", String.class), "Role claim should match");
            assertEquals(testUser.getName(), claims.get("name", String.class), "Name claim should match");
        }

        @Test
        @DisplayName("Generated token should have correct expiration")
        void generateToken_HasCorrectExpiration() {
            long nowMillis = System.currentTimeMillis();
            String token = jwtUtil.generateToken(testUser);
            Date expirationDate = jwtUtil.extractExpiration(token);

            assertNotNull(expirationDate);
            long expectedExpiryMillis = nowMillis + testExpirationMs;
            // Allow a small delta (e.g., 5 seconds) for execution time variance
            long deltaMillis = 5000;
            assertTrue(Math.abs(expirationDate.getTime() - expectedExpiryMillis) < deltaMillis,
                       "Expiration time should be approximately " + testExpirationMs + "ms from now");
            assertTrue(expirationDate.getTime() > nowMillis, "Expiration should be in the future");
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should validate a correctly generated token")
        void validateToken_ValidToken() {
            String token = jwtUtil.generateToken(testUser);
            assertTrue(jwtUtil.validateToken(token), "Valid token should validate successfully");
        }

        @Test
        @DisplayName("Should fail validation for an expired token")
        void validateToken_ExpiredToken() throws InterruptedException {
            // Generate token with very short expiration (e.g., 1ms)
            JwtUtil shortExpiryUtil = new JwtUtil(testSecret, 1);
            String expiredToken = shortExpiryUtil.generateToken(testUser);

            // Wait for slightly longer than expiration
            Thread.sleep(50); // Wait 50ms

            assertFalse(jwtUtil.validateToken(expiredToken), "Expired token should fail validation");
        }

        @Test
        @DisplayName("Should fail validation for token signed with different secret")
        void validateToken_WrongSecret() {
            String differentSecret = "AnotherSecretKeyWhichIsDifferentAndAlsoLongEnoughMaybe12345";
            JwtUtil otherUtil = new JwtUtil(differentSecret, testExpirationMs);
            String tokenFromOther = otherUtil.generateToken(testUser);

            // Validate using the original jwtUtil instance
            assertFalse(jwtUtil.validateToken(tokenFromOther), "Token signed with different secret should fail");
        }

        @Test
        @DisplayName("Should fail validation for malformed token")
        void validateToken_MalformedToken() {
            String malformedToken = "this.is.not.a.valid.token";
            assertFalse(jwtUtil.validateToken(malformedToken), "Malformed token should fail validation");
        }

         @Test
        @DisplayName("Should fail validation for empty or null token")
        void validateToken_EmptyOrNull() {
            assertFalse(jwtUtil.validateToken(""), "Empty token should fail validation");
            assertFalse(jwtUtil.validateToken(null), "Null token should fail validation");
        }
    }

    @Nested
    @DisplayName("Claim Extraction Tests")
    class ExtractionTests {

        @Test
        @DisplayName("Should extract correct user ID (subject)")
        void extractUserId_Success() {
            String token = jwtUtil.generateToken(testUser);
            assertEquals(testUser.getId(), jwtUtil.extractUserId(token));
        }

        @Test
        @DisplayName("Should extract correct custom 'role' claim")
        void extractClaim_RoleSuccess() {
             String token = jwtUtil.generateToken(testUser);
             String extractedRole = jwtUtil.extractClaim(token, claims -> claims.get("role", String.class));
             assertEquals(testUser.getRole().name(), extractedRole);
        }

         @Test
        @DisplayName("Should extract correct expiration date")
        void extractExpiration_Success() {
            String token = jwtUtil.generateToken(testUser);
            Date expiration = jwtUtil.extractExpiration(token);
            assertNotNull(expiration);
            assertTrue(expiration.after(new Date()), "Expiration should be a future date");
        }

        @Test
        @DisplayName("Should extract correct signature")
        void extractSignature_Success() {
             String token = jwtUtil.generateToken(testUser);
             String signature = jwtUtil.extractSignature(token);
             assertNotNull(signature);
             assertFalse(signature.isEmpty());
             // Basic check, not verifying cryptographic correctness here
             assertTrue(signature.matches("^[A-Za-z0-9_-]+$"), "Signature should be Base64URL characters");
        }

         @Test
        @DisplayName("extractSignature should return null for invalid format")
        void extractSignature_InvalidFormat() {
             String invalidToken = "part1.part2"; // Only two parts
             assertNull(jwtUtil.extractSignature(invalidToken));
             assertNull(jwtUtil.extractSignature(""));
             assertNull(jwtUtil.extractSignature(null));
        }

         @Test
        @DisplayName("Should extract correct expiration as LocalDateTime")
        void extractExpirationAsLocalDateTime_Success() {
            String token = jwtUtil.generateToken(testUser);
            LocalDateTime expiration = jwtUtil.extractExpirationAsLocalDateTime(token);
            assertNotNull(expiration);
            assertTrue(expiration.isAfter(LocalDateTime.now()), "Expiration should be in the future");
        }
    }
}