package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Authentication User (POC - In-Memory Only)
 * 
 * Responsibility: Represents user credentials for JWT authentication.
 * Does NOT: Replace User entity (which is for MongoDB user data), handle password hashing (POC uses plain text).
 * 
 * Distributed Systems Concept: Separates authentication concerns from user profile data.
 * AuthUser validates credentials and generates JWT tokens. User entity stores profile information.
 * This separation enables different storage strategies (credentials in-memory POC, profiles in MongoDB).
 * 
 * POC Implementation: Hardcoded users stored in HashMap (see UserService).
 * Production Migration: Merge password field into User entity, add BCrypt hashing, remove this class.
 * 
 * See: specs/001-ubiquitous-messaging-platform/authentication-jwt-data-model.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser {
    
    /**
     * Unique user identifier (UUID format).
     * Maps to JWT "sub" (subject) claim.
     * MUST match User.userId for the same user.
     */
    private String userId;
    
    /**
     * Unique username for login.
     * Validation: 3-50 characters, alphanumeric + underscore only.
     */
    private String username;
    
    /**
     * User password for authentication.
     * 
     * POC: Plain text (enables debugging, UNACCEPTABLE in production).
     * Production: BCrypt hashed (use BCryptPasswordEncoder).
     * 
     * WARNING: Never log or expose password in API responses.
     */
    private String password;
    
    /**
     * User role for authorization.
     * Values: ROLE_USER, ROLE_ADMIN
     * 
     * Maps to JWT "role" claim.
     */
    private String role;
    
    /**
     * Account creation timestamp.
     */
    private Instant createdAt;
}
