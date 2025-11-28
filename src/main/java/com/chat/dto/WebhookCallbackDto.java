package com.chat.dto;

import com.chat.model.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for webhook callbacks from external platforms.
 * 
 * Received via POST /api/webhooks/{platform} endpoints.
 * Simulates callbacks from real platform APIs (WhatsApp, Instagram)
 * reporting delivery and read status updates.
 * 
 * Layer 2 Enhancement: Enables bidirectional communication with platform mocks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookCallbackDto {
    
    /**
     * Platform message ID (wamid.XXX for WhatsApp, ig_mid.XXX for Instagram).
     */
    private String platformMessageId;
    
    /**
     * Our internal message ID (mapped from platformMessageId).
     */
    private String messageId;
    
    /**
     * New status reported by platform.
     * Typically: DELIVERED or READ.
     */
    private MessageStatus status;
    
    /**
     * External recipient ID who triggered the status update.
     * Example: +5511987654321 or @john_doe
     */
    private String externalRecipientId;
    
    /**
     * ISO 8601 timestamp of status change on platform.
     */
    private String timestamp;
    
    /**
     * Optional error code if delivery failed.
     */
    private String errorCode;
    
    /**
     * Optional error message.
     */
    private String errorMessage;
}
