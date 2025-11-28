package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Mapping between internal message IDs and platform-specific message IDs.
 * 
 * This entity bridges the gap between the system's internal UUID-based message IDs
 * and the platform-specific IDs (wamid.xxx for WhatsApp, mid.xxx for Instagram)
 * returned by external messaging platforms.
 * 
 * Required for webhook processing: when a platform sends a delivery status webhook
 * with its platformMessageId, we need to look up the corresponding internal messageId
 * to update the message status in our database.
 * 
 * Lifecycle:
 * 1. Message created with internal messageId (UUID)
 * 2. Worker sends to platform, receives platformMessageId
 * 3. Worker saves mapping: messageId ↔ platformMessageId
 * 4. Platform sends webhook with platformMessageId
 * 5. WebhookController looks up messageId using this mapping
 * 6. MessageStateUpdateWorker updates message status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platform_message_mappings")
public class PlatformMessageMapping {
    
    /**
     * MongoDB auto-generated ID.
     */
    @Id
    private String id;
    
    /**
     * Internal system message ID (UUID format).
     * Example: 56f7f3ad-b1f6-422d-a7d4-0fc87bf4b283
     */
    @Field("message_id")
    @Indexed
    private String messageId;
    
    /**
     * Platform-specific external message ID.
     * Examples:
     * - WhatsApp: wamid.8D72BB6047E84E48BC3610677196866D
     * - Instagram: mid.21DEB5C0E89B4F338936F2630480C124
     * 
     * This is the ID returned by the platform API and used in webhook callbacks.
     */
    @Field("platform_message_id")
    @Indexed(unique = true)
    private String platformMessageId;
    
    /**
     * Platform where the message was sent.
     */
    @Field("platform")
    @Indexed
    private Platform platform;
    
    /**
     * Timestamp when mapping was created (when message was sent to platform).
     */
    @Field("created_at")
    private Instant createdAt;
}
