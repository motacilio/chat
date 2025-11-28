package com.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Message Event DTO for Kafka (T033)
 * 
 * Responsibility: Serializable payload for message-events topic.
 * Does NOT: Contain business logic (pure data transfer object).
 * 
 * Distributed Systems Concept: This DTO is the message contract between producers (ChatServiceImpl)
 * and consumers (MessageDeliveryWorker). Using JSON serialization enables schema evolution -
 * new fields can be added without breaking existing consumers (forward compatibility).
 * 
 * Kafka Topic: message-events
 * Producers: ChatServiceImpl.SendMessage
 * Consumers: MessageDeliveryWorker
 * 
 * Serialization: JSON (configured in KafkaProducerConfig)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageEventDto implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Client-generated UUID for idempotency (FR-006)
     */
    private String messageId;
    
    /**
     * Conversation UUID
     */
    private String conversationId;
    
    /**
     * Sender user_id (UUID)
     */
    private String senderId;
    
    /**
     * Recipient user_id list for platform routing
     */
    private java.util.List<String> recipientIds;
    
    /**
     * Message text content (XOR with fileId - one must be null)
     */
    private String messageText;
    
    /**
     * File UUID for file messages (User Story 4)
     * XOR constraint: Either messageText OR fileId must be non-null
     */
    private String fileId;
    
    /**
     * Per-conversation sequence number (for ordering per FR-007)
     */
    private Long sequenceNumber;
    
    /**
     * Message timestamp (ISO-8601 format)
     */
    private String timestamp;
}
