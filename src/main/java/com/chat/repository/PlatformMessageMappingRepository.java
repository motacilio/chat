package com.chat.repository;

import com.chat.model.PlatformMessageMapping;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for platform message ID mappings.
 * 
 * Provides lookup methods to translate between internal message IDs and
 * platform-specific external IDs for webhook processing.
 */
@Repository
public interface PlatformMessageMappingRepository extends MongoRepository<PlatformMessageMapping, String> {
    
    /**
     * Find mapping by platform-specific message ID.
     * 
     * Used by WebhookController to look up internal messageId when receiving
     * webhook callbacks that contain platformMessageId.
     * 
     * @param platformMessageId Platform message ID (e.g., wamid.xxx or mid.xxx)
     * @return Mapping if found
     */
    Optional<PlatformMessageMapping> findByPlatformMessageId(String platformMessageId);
    
    /**
     * Find mapping by internal message ID.
     * 
     * @param messageId Internal system message ID (UUID)
     * @return Mapping if found
     */
    Optional<PlatformMessageMapping> findByMessageId(String messageId);
}
