package com.chat.repository;

import com.chat.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository (data-model.md Entity 1 Repository)
 * 
 * Responsibility: Provides data access methods for User entity.
 * Does NOT: Handle business logic (see UserService when implemented), manage authentication.
 * 
 * Indexes (defined in User entity):
 * - user_id: unique index (primary identifier lookup)
 * - username: unique index (user search, mentions)
 * - email: unique index (login, account recovery)
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {
    
    /**
     * Find user by UUID identifier.
     * Primary lookup method used throughout system.
     * 
     * @param userId UUID user identifier
     * @return Optional User
     */
    Optional<User> findByUserId(String userId);
    
    /**
     * Find user by username.
     * Used for user search and mention features.
     * 
     * @param username Display name
     * @return Optional User
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Find user by email.
     * Used for authentication and account recovery (future features).
     * 
     * @param email Contact email
     * @return Optional User
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Check if username already exists.
     * Used for registration validation.
     * 
     * @param username Display name
     * @return true if username exists
     */
    boolean existsByUsername(String username);
    
    /**
     * Check if email already exists.
     * Used for registration validation.
     * 
     * @param email Contact email
     * @return true if email exists
     */
    boolean existsByEmail(String email);
}
