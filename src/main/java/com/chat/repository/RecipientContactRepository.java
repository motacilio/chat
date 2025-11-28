package com.chat.repository;

import com.chat.model.Platform;
import com.chat.model.RecipientContact;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for RecipientContact entities.
 * 
 * Supports lookups for platform-specific message routing.
 */
@Repository
public interface RecipientContactRepository extends MongoRepository<RecipientContact, String> {
    
    /**
     * Find external contact for a user on specific platform.
     * 
     * @param userId   Internal user ID
     * @param platform Target platform
     * @return RecipientContact if exists
     */
    Optional<RecipientContact> findByUserIdAndPlatform(String userId, Platform platform);
    
    /**
     * Find all external contacts for a user across all platforms.
     * 
     * @param userId Internal user ID
     * @return List of RecipientContact
     */
    List<RecipientContact> findByUserId(String userId);
    
    /**
     * Find user by external ID on platform (reverse lookup for webhooks).
     * 
     * @param externalId External identifier (phone number, username, etc.)
     * @param platform   Platform type
     * @return RecipientContact if exists
     */
    Optional<RecipientContact> findByExternalIdAndPlatform(String externalId, Platform platform);
}
