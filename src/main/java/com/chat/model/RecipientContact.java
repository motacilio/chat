package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;

/**
 * RecipientContact - Maps internal user IDs to external platform identifiers.
 * 
 * Purpose: Each user can have multiple external contacts across different platforms
 * (e.g., same person has WhatsApp +5511987654321 and Instagram @john_doe).
 * 
 * Layer 2 Feature: Enables message routing to correct platform-specific external ID.
 * 
 * Example Document:
 * {
 *   "userId": "b2b2b2b2-2222-2222-2222-222222222222",
 *   "platform": "WHATSAPP",
 *   "externalId": "+5511987654321",
 *   "verified": true
 * }
 * 
 * Indexes:
 * - Compound index on (userId, platform) for fast lookups during message routing
 * - Index on externalId for reverse lookups (webhook → internal user)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "recipient_contacts")
@CompoundIndex(name = "user_platform_idx", def = "{'userId': 1, 'platform': 1}", unique = true)
public class RecipientContact {
    
    @Id
    private String id;
    
    /**
     * Internal user ID (UUID).
     */
    @Indexed
    private String userId;
    
    /**
     * External platform (WHATSAPP, INSTAGRAM, etc.).
     */
    private Platform platform;
    
    /**
     * External identifier on the platform.
     * - WhatsApp: E.164 phone number (e.g., +5511987654321)
     * - Instagram: @username (e.g., @john_doe)
     */
    @Indexed
    private String externalId;
    
    /**
     * Whether this contact has been verified (for future use).
     */
    private Boolean verified;
    
    /**
     * Timestamp when contact was added.
     */
    private java.time.Instant createdAt;
    
    /**
     * Last time this contact was updated.
     */
    private java.time.Instant updatedAt;
}
