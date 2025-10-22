package com.taskflow.userservice.security;


import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.taskflow.userservice.model.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for handling JWT (JSON Web Token) operations:
 * - Generation
 * - Validation
 * - Parsing
 */
@Component
@Slf4j
public class JwtUtil {
	private final SecretKey secretKey;
	private final long expiration;
	
	/**
     * Constructor that injects JWT properties from application.properties.
     *
     * @param secret     The secret string used for signing.
     * @param expiration The token expiration time in milliseconds.
     */
	public JwtUtil(
			@Value("${jwt.secret}")String secret,
			@Value("${jwt.expiration}") long expiration) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expiration = expiration;
		log.info("JwtUtil initialized with expiration: {}ms",expiration);
	}
	/**
     * Generates a JWT for a given User.
     *
     * @param user The User object.
     * @return A signed JWT string.
     */
	public String generateToken(User user) {
		log.debug("Generating token for user: {}",user.getEmail());;
		Map<String, Object> claims = new HashMap<>();
		claims.put("role", user.getRole().name());
		claims.put("name", user.getName());
		
		String subject = user.getId();
		
		Date now = new Date();
		Date expiryDate = new Date(now.getTime()+ expiration);
		
		return Jwts.builder()
				.setClaims(claims)
				.setSubject(subject)
				.setIssuedAt(now)
				.setExpiration(expiryDate)
				.signWith(secretKey)
				.compact();
	}
	/**
     * Extracts all claims from a token.
     *
     * @param token The JWT string.
     * @return The Claims object.
     */
	private Claims extractAllClaims(String token) {
		return Jwts.parserBuilder()
				.setSigningKey(secretKey)
				.build()
				.parseClaimsJws(token)
				.getBody();
	}
	/**
     * A generic function to extract a specific claim from a token.
     *
     * @param token          The JWT string.
     * @param claimsResolver A function to extract the desired claim.
     * @param <T>            The type of the claim.
     * @return The claim value.
     */
	public <T> T extractClaim(String token, Function<Claims, T>claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}
	/**
     * Extracts the user ID (subject) from the token.
     *
     * @param token The JWT string.
     * @return The user ID.
     */
	public String extractUserId(String token) {
		return extractClaim(token,Claims::getSubject);
	}
	/**
     * Extracts the expiration date from the token.
     *
     * @param token The JWT string.
     * @return The expiration date.
     */
	public Date extractExpiration(String token) {
		return extractClaim(token,Claims::getExpiration);
	}
	/**
     * Checks if a token is expired.
     *
     * @param token The JWT string.
     * @return true if the token is expired, false otherwise.
     */
	private boolean isTokenExpired(String token) {
		try {
			return extractExpiration(token).before(new Date());
		}catch(Exception e) {
			log.warn("Error checking token expiration: {}",e.getMessage());
			return true;
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
			extractAllClaims(token);
			return !isTokenExpired(token);
		}catch(Exception e) {
			log.warn("Invalid JWT token: {}",e.getMessage());
			return false;
		}
	}
}
