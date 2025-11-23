package com.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login Response DTO
 * 
 * Responsibility: Encapsulates JWT token and user info returned from login endpoint.
 * Does NOT: Generate tokens, validate credentials, manage sessions.
 * 
 * Distributed Systems Concept: Stateless authentication response containing
 * all necessary context (token + user info) for client-side storage.
 * Client includes token in subsequent requests without server-side session lookup.
 * 
 * See: POST /api/auth/login response schema in specs/001-ubiquitous-messaging-platform/authentication-jwt-data-model.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    /**
     * JWT access token.
     * Format: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIuLi4ifQ.signature
     * 
     * Client MUST include in gRPC metadata:
     * authorization: Bearer <token>
     */
    private String token;
    
    /**
     * Token type (always "Bearer" for JWT).
     */
    private String tokenType;
    
    /**
     * Token expiration time in milliseconds.
     * Default: 86400000 (24 hours)
     * 
     * Client SHOULD refresh token before expiration.
     * POC: No refresh token mechanism (must re-login after expiration).
     */
    private Long expiresIn;
    
    /**
     * Authenticated user information.
     */
    private UserInfo user;
}
