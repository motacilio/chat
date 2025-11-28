package com.chat.service;

import com.chat.model.Platform;
import com.chat.model.PlatformMessageMapping;
import com.chat.repository.PlatformMessageMappingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for managing message ID mappings between internal and platform-specific IDs.
 * 
 * This service provides the critical link between our internal message tracking
 * and external platform messaging APIs:
 * 
 * 1. When sending a message to a platform, the platform returns its own message ID
 * 2. We save the mapping: internal messageId → platform messageId
 * 3. When receiving webhook callbacks with platform messageId, we look up internal messageId
 * 4. This allows MessageStateUpdateWorker to update the correct message in MongoDB
 */
@Service
@RequiredArgsConstructor
public class PlatformMessageMappingService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlatformMessageMappingService.class);
    
    private final PlatformMessageMappingRepository mappingRepository;
    
    /**
     * Save mapping when message is successfully sent to platform.
     * 
     * Called by platform workers (WhatsAppMessageWorker, InstagramMessageWorker)
     * after receiving SendResult.success() from the platform adapter.
     * 
     * @param messageId Internal system message ID (UUID)
     * @param platformMessageId Platform-specific message ID
     * @param platform The platform (WHATSAPP, INSTAGRAM)
     */
    public void saveMapping(String messageId, String platformMessageId, Platform platform) {
        try {
            PlatformMessageMapping mapping = PlatformMessageMapping.builder()
                    .messageId(messageId)
                    .platformMessageId(platformMessageId)
                    .platform(platform)
                    .createdAt(Instant.now())
                    .build();
            
            mappingRepository.save(mapping);
            
            logger.info("Saved platform message mapping - messageId: {}, platformMessageId: {}, platform: {}", 
                       messageId, platformMessageId, platform);
            
        } catch (Exception e) {
            logger.error("Failed to save platform message mapping - messageId: {}, platformMessageId: {}, platform: {}, error: {}", 
                        messageId, platformMessageId, platform, e.getMessage(), e);
            // Don't throw - mapping is for convenience, shouldn't fail the message delivery
        }
    }
    
    /**
     * Look up internal message ID by platform message ID.
     * 
     * Called by WebhookController when receiving webhook callbacks to translate
     * platformMessageId back to internal messageId for state updates.
     * 
     * @param platformMessageId Platform message ID from webhook
     * @return Internal message ID if mapping exists
     */
    public Optional<String> findMessageIdByPlatformMessageId(String platformMessageId) {
        try {
            Optional<PlatformMessageMapping> mapping = mappingRepository.findByPlatformMessageId(platformMessageId);
            
            if (mapping.isPresent()) {
                String messageId = mapping.get().getMessageId();
                logger.debug("Found messageId mapping - platformMessageId: {}, messageId: {}", 
                           platformMessageId, messageId);
                return Optional.of(messageId);
            } else {
                logger.warn("No messageId mapping found for platformMessageId: {}", platformMessageId);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            logger.error("Error looking up messageId mapping - platformMessageId: {}, error: {}", 
                        platformMessageId, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Look up platform message ID by internal message ID.
     * 
     * Less commonly used - mainly for debugging or admin queries.
     * 
     * @param messageId Internal message ID
     * @return Platform message ID if mapping exists
     */
    public Optional<String> findPlatformMessageIdByMessageId(String messageId) {
        try {
            Optional<PlatformMessageMapping> mapping = mappingRepository.findByMessageId(messageId);
            
            if (mapping.isPresent()) {
                String platformMessageId = mapping.get().getPlatformMessageId();
                logger.debug("Found platformMessageId mapping - messageId: {}, platformMessageId: {}", 
                           messageId, platformMessageId);
                return Optional.of(platformMessageId);
            } else {
                logger.warn("No platformMessageId mapping found for messageId: {}", messageId);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            logger.error("Error looking up platformMessageId mapping - messageId: {}, error: {}", 
                        messageId, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
