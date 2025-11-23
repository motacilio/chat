package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * User Entity (data-model.md Entity 1)
 * 
 * Responsibility: Represents a registered platform user with unique identifier.
 * Does NOT: Handle authentication (assumed external per spec A-002), manage linked accounts (P4 feature).
 * 
 * Distributed Systems Concept: Aggregate root in domain model, uniquely identified by user_id (UUID).
 * User entity is referenced by conversations and messages but stored separately for normalization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {
    
    /**
     * MongoDB internal ID (not exposed to clients)
     */
    @Id
    private String id;
    
    /**
     * UUID - unique user identifier (primary identifier throughout system)
     * This is the canonical user ID referenced in conversations, messages, JWT tokens.
     */
    @Indexed(unique = true)
    private String userId;
    
    /**
     * Display name (e.g., "john_doe")
     * Unique across platform for user search and mentions.
     */
    @Indexed(unique = true)
    private String username;
    
    /**
     * Contact email
     * Used for notifications and account recovery (future features).
     */
    @Indexed(unique = true)
    private String email;
    
    /**
     * Registration timestamp
     */
    private Instant createdAt;
    
    /**
     * Factory method to create new user with auto-generated timestamps.
     * 
     * @param userId   UUID user identifier
     * @param username Display name
     * @param email    Contact email
     * @return User instance with createdAt set to current time
     */
    public static User create(String userId, String username, String email) {
        return User.builder()
                .userId(userId)
                .username(username)
                .email(email)
                .createdAt(Instant.now())
                .build();
    }
}
