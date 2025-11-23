package com.chat.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Service
 * 
 * Responsibility: Generates and validates JWT tokens for authentication.
 * Does NOT: Handle user lookup, password validation, authorization logic.
 * 
 * Distributed Systems Concept: Stateless authentication via cryptographically signed tokens.
 * Each service instance can independently validate tokens without central session store,
 * enabling horizontal scaling and eliminating single point of failure.
 * 
 * Algorithm: HS256 (HMAC-SHA256 symmetric key signing).
 * POC: Static secret from application.properties.
 * Production: Secret from environment variable, consider RS256 for key rotation.
 * 
 * See: specs/001-ubiquitous-messaging-platform/authentication-jwt-research.md Decision 1, 2
 */
@Service
public class JwtService {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    
    /**
     * JWT secret key (HS256 requires >= 256 bits / 32 characters).
     * POC: Configured in application.properties (jwt.secret).
     * Production: MUST load from environment variable (e.g., ${JWT_SECRET}).
     */
    @Value("${jwt.secret}")
    private String secretKey;
    
    /**
     * Token expiration time in milliseconds.
     * Default: 86400000 (24 hours)
     */
    @Value("${jwt.expiration}")
    private Long expirationTime;
    
    /**
     * Generates JWT token for authenticated user.
     * 
     * Token Structure:
     * - Header: {"alg": "HS256", "typ": "JWT"}
     * - Payload: {"sub": userId, "username": username, "role": role, "iat": timestamp, "exp": timestamp}
     * - Signature: HMACSHA256(base64UrlEncode(header) + "." + base64UrlEncode(payload), secret)
     * 
     * @param userId   User's unique identifier (UUID)
     * @param username User's login name
     * @param role     User's authorization role (ROLE_USER, ROLE_ADMIN)
     * @return JWT token string
     */
    public String generateToken(String userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("role", role);
        
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);
        
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)  // "sub" claim = user ID
                .setIssuedAt(now)    // "iat" claim = issued at
                .setExpiration(expiryDate)  // "exp" claim = expires at
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
        
        logger.debug("Generated JWT token for user: {} (expires: {})", username, expiryDate);
        return token;
    }
    
    /**
     * Validates JWT token signature and expiration.
     * 
     * Validation Steps:
     * 1. Parse token (verifies base64 encoding)
     * 2. Verify signature using secret key (prevents tampering)
     * 3. Check expiration timestamp (rejects expired tokens)
     * 
     * @param token JWT token string
     * @return true if token is valid (signature correct + not expired)
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            logger.warn("JWT token expired: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            logger.warn("Invalid JWT signature: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extracts user ID from JWT token.
     * 
     * @param token JWT token string
     * @return User ID from "sub" claim, or null if token invalid
     */
    public String extractUserId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            logger.warn("Failed to extract user ID from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts username from JWT token.
     * 
     * @param token JWT token string
     * @return Username from "username" claim, or null if token invalid
     */
    public String extractUsername(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("username", String.class);
        } catch (Exception e) {
            logger.warn("Failed to extract username from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts role from JWT token.
     * 
     * @param token JWT token string
     * @return Role from "role" claim, or null if token invalid
     */
    public String extractRole(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("role", String.class);
        } catch (Exception e) {
            logger.warn("Failed to extract role from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks if token is expired.
     * 
     * @param token JWT token string
     * @return true if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;  // Consider invalid tokens as expired
        }
    }
    
    /**
     * Extracts all claims from JWT token.
     * 
     * @param token JWT token string
     * @return Claims object containing all token claims
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    /**
     * Generates signing key from secret string.
     * 
     * POC: Uses configured secret from application.properties.
     * Production: Load from environment variable for security.
     * 
     * @return SecretKey for HS256 signing
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
