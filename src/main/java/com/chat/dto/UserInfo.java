package com.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User Info DTO
 * 
 * Responsibility: Exposes safe user details in API responses.
 * Does NOT: Include password, expose internal identifiers, manage authentication.
 * 
 * Distributed Systems Concept: DTOs prevent accidental exposure of sensitive data
 * (passwords, internal IDs) across service boundaries. Safe projection of User entity.
 * 
 * See: LoginResponse.user field in specs/001-ubiquitous-messaging-platform/authentication-jwt-data-model.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    
    /**
     * User's unique identifier (UUID).
     * Safe to expose - used in message sender_id, conversation participants.
     */
    private String userId;
    
    /**
     * User's login username.
     * Safe to expose - public identifier.
     */
    private String username;
    
    /**
     * User's authorization role.
     * Values: ROLE_USER, ROLE_ADMIN
     * 
     * Used for role-based access control in services.
     */
    private String role;
}
