package com.chat.dto;

import com.chat.model.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for platform-specific message delivery events.
 * 
 * Published to platform-specific Kafka topics (whatsapp-messages, instagram-messages)
 * for delivery to external platforms via mock adapters.
 * 
 * Layer 2 Enhancement: Routes messages to platform-specific topics for targeted delivery.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformMessageEventDto {
    
    /**
     * Original message ID from internal system.
     */
    private String messageId;
    
    /**
     * Conversation ID for tracking.
     */
    private String conversationId;
    
    /**
     * Sender's user ID (internal).
     */
    private String senderId;
    
    /**
     * Target platform for delivery.
     */
    private Platform platform;
    
    /**
     * External ID of recipient on the platform.
     * Examples:
     * - WhatsApp: +5511987654321 (E.164 format)
     * - Instagram: @john_doe
     */
    private String externalRecipientId;
    
    /**
     * Message text content.
     * Null for file messages (check fileId instead).
     */
    private String messageText;
    
    /**
     * File ID if this is a file message.
     * Null for text messages.
     */
    private String fileId;
    
    /**
     * ISO 8601 timestamp of message creation.
     */
    private String timestamp;
    
    /**
     * Sequence number in conversation (for ordering).
     */
    private Long sequenceNumber;
}
