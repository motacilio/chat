package com.chat.service;

import com.chat.kafka.v1.MessageEvent;
import com.chat.kafka.v1.PlatformMessageEvent;
import com.chat.model.Platform;
import com.chat.model.RecipientContact;
import com.chat.repository.RecipientContactRepository;
import com.google.protobuf.util.Timestamps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PlatformRoutingService
 * 
 * Responsibility: Route messages to platform-specific Kafka topics for delivery
 * via external platform adapters (WhatsApp, Instagram, etc.).
 * 
 * Layer 2 Enhancement: Enables multi-platform message delivery by routing
 * messages to appropriate external channels based on recipient's platform preferences.
 * 
 * Flow:
 * 1. Receive MessageEventDto from message-events topic (via MessageDeliveryWorker)
 * 2. Lookup recipient's external contacts (platform + externalId)
 * 3. For each platform, publish to platform-specific topic:
 *    - whatsapp-messages → WhatsAppMessageWorker
 *    - instagram-messages → InstagramMessageWorker
 * 4. Platform workers consume and call mock adapters for external delivery
 * 
 * Design Pattern: Message Router (Enterprise Integration Patterns)
 * - Content-Based Router: Routes based on recipient's platform contacts
 * - Publish-Subscribe: Single internal message → multiple platform deliveries
 * 
 * Fallback Strategy: If recipient has no external contacts, log warning
 * (future: could send internal-only notification or SMS fallback).
 */
@Service
public class PlatformRoutingService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlatformRoutingService.class);
    
    private final RecipientContactRepository recipientContactRepository;
    private final KafkaTemplate<String, com.chat.kafka.v1.PlatformMessageEvent> platformKafkaTemplate;
    
    public PlatformRoutingService(
            RecipientContactRepository recipientContactRepository,
            KafkaTemplate<String, com.chat.kafka.v1.PlatformMessageEvent> platformKafkaTemplate) {
        this.recipientContactRepository = recipientContactRepository;
        this.platformKafkaTemplate = platformKafkaTemplate;
    }
    
    /**
     * Route message to recipient's external platforms.
     * 
     * Called after message is persisted to MongoDB (by MessageDeliveryWorker).
     * Lookups recipient's external contacts and publishes to platform-specific topics.
     * 
     * @param messageEvent Internal message event (Protobuf)
     * @param recipientId  Recipient's internal user ID
     */
    public void routeMessageToPlatforms(MessageEvent messageEvent, String recipientId) {
        logger.debug("Routing message to platforms - messageId: {}, recipientId: {}", 
                    messageEvent.getMessageId(), recipientId);
        
        // Lookup recipient's external contacts
        List<RecipientContact> contacts = recipientContactRepository.findByUserId(recipientId);
        
        if (contacts.isEmpty()) {
            logger.warn("Recipient has no external platform contacts - messageId: {}, recipientId: {}", 
                       messageEvent.getMessageId(), recipientId);
            // Future: Could trigger internal notification or SMS fallback
            return;
        }
        
        // Route to each platform
        for (RecipientContact contact : contacts) {
            try {
                routeToPlatform(messageEvent, contact);
            } catch (Exception e) {
                logger.error("Failed to route message to platform - messageId: {}, platform: {}, error: {}", 
                            messageEvent.getMessageId(), contact.getPlatform(), e.getMessage(), e);
                // Continue with other platforms even if one fails
            }
        }
    }
    
    /**
     * Route message to specific platform topic.
     * 
     * @param messageEvent Internal message event (Protobuf)
     * @param contact      Recipient's platform contact
     */
    private void routeToPlatform(MessageEvent messageEvent, RecipientContact contact) {
        Platform platform = contact.getPlatform();
        String topicName = getPlatformTopicName(platform);
        
        // Build platform-specific message event (Protobuf)
        PlatformMessageEvent platformEvent = PlatformMessageEvent.newBuilder()
                .setMessageId(messageEvent.getMessageId())
                .setConversationId(messageEvent.getConversationId())
                .setSenderId(messageEvent.getSenderId())
                .setPlatform(mapToPlatformEnum(platform))
                .setExternalRecipientId(contact.getExternalId())
                .setMessageText(messageEvent.hasMessageText() ? messageEvent.getMessageText() : "")
                .setTimestamp(messageEvent.getTimestamp())
                .setSequenceNumber(messageEvent.getSequenceNumber())
                .build();
        
        // Publish to platform-specific topic
        platformKafkaTemplate.send(topicName, messageEvent.getConversationId(), platformEvent);
        
        logger.info("Message routed to platform - messageId: {}, platform: {}, topic: {}, externalId: {}", 
                   messageEvent.getMessageId(), platform, topicName, contact.getExternalId());
    }
    
    /**
     * Route file message to recipient's external platforms.
     * 
     * Similar to text messages but includes fileId instead of messageText.
     * 
     * @param messageEvent Internal message event (Protobuf)
     * @param recipientId  Recipient's internal user ID
     * @param fileId       File ID for download URL generation
     */
    public void routeFileMessageToPlatforms(MessageEvent messageEvent, String recipientId, String fileId) {
        logger.debug("Routing file message to platforms - messageId: {}, recipientId: {}, fileId: {}", 
                    messageEvent.getMessageId(), recipientId, fileId);
        
        // Lookup recipient's external contacts
        List<RecipientContact> contacts = recipientContactRepository.findByUserId(recipientId);
        
        if (contacts.isEmpty()) {
            logger.warn("Recipient has no external platform contacts for file - messageId: {}, recipientId: {}", 
                       messageEvent.getMessageId(), recipientId);
            return;
        }
        
        // Route to each platform
        for (RecipientContact contact : contacts) {
            try {
                routeFileToPlatform(messageEvent, contact, fileId);
            } catch (Exception e) {
                logger.error("Failed to route file to platform - messageId: {}, platform: {}, error: {}", 
                            messageEvent.getMessageId(), contact.getPlatform(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Route file message to specific platform topic.
     * 
     * @param messageEvent Internal message event (Protobuf)
     * @param contact      Recipient's platform contact
     * @param fileId       File ID for download
     */
    private void routeFileToPlatform(MessageEvent messageEvent, RecipientContact contact, String fileId) {
        Platform platform = contact.getPlatform();
        String topicName = getPlatformTopicName(platform);
        
        // Build platform-specific file message event (Protobuf)
        PlatformMessageEvent platformEvent = PlatformMessageEvent.newBuilder()
                .setMessageId(messageEvent.getMessageId())
                .setConversationId(messageEvent.getConversationId())
                .setSenderId(messageEvent.getSenderId())
                .setPlatform(mapToPlatformEnum(platform))
                .setExternalRecipientId(contact.getExternalId())
                .setFileId(fileId)  // File messages use fileId (oneof content)
                .setTimestamp(messageEvent.getTimestamp())
                .setSequenceNumber(messageEvent.getSequenceNumber())
                .build();
        
        // Publish to platform-specific topic
        platformKafkaTemplate.send(topicName, messageEvent.getConversationId(), platformEvent);
        
        logger.info("File message routed to platform - messageId: {}, platform: {}, fileId: {}, externalId: {}", 
                   messageEvent.getMessageId(), platform, fileId, contact.getExternalId());
    }
    
    /**
     * Get Kafka topic name for platform.
     * 
     * @param platform Target platform
     * @return Kafka topic name
     */
    private String getPlatformTopicName(Platform platform) {
        switch (platform) {
            case WHATSAPP:
                return "whatsapp-messages";
            case INSTAGRAM:
                return "instagram-messages";
            case TELEGRAM:
                return "telegram-messages";
            default:
                throw new IllegalArgumentException("Unknown platform: " + platform);
        }
    }
    
    /**
     * Map domain Platform to Protobuf PlatformMessageEvent.Platform.
     */
    private PlatformMessageEvent.Platform mapToPlatformEnum(Platform platform) {
        switch (platform) {
            case WHATSAPP:
                return PlatformMessageEvent.Platform.WHATSAPP;
            case INSTAGRAM:
                return PlatformMessageEvent.Platform.INSTAGRAM;
            case TELEGRAM:
                return PlatformMessageEvent.Platform.TELEGRAM;
            default:
                return PlatformMessageEvent.Platform.PLATFORM_UNSPECIFIED;
        }
    }
    
    /**
     * Lookup recipient's platform contact (for manual routing).
     * 
     * @param recipientId Internal user ID
     * @param platform    Target platform
     * @return RecipientContact if exists
     */
    public Optional<RecipientContact> getRecipientContact(String recipientId, Platform platform) {
        return recipientContactRepository.findByUserIdAndPlatform(recipientId, platform);
    }
}
