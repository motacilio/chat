package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversation Entity (data-model.md Entity 2)
 * 
 * Responsibility: Represents a messaging context (1:1 or group) with participants.
 * Does NOT: Store messages directly (messages reference conversation_id), handle permissions (P3 feature).
 * 
 * Distributed Systems Concept: Aggregate root for message grouping, enables conversation-level operations.
 * Participants array allows efficient query: "find all conversations where user_id in participants".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "conversations")
@CompoundIndexes({
    @CompoundIndex(name = "participants_last_message", def = "{'participants': 1, 'last_message_at': -1}")
})
public class Conversation {
    
    /**
     * MongoDB internal ID
     */
    @Id
    private String id;
    
    /**
     * UUID - unique conversation identifier
     */
    @Field("conversation_id")
    @Indexed(unique = true)
    private String conversationId;
    
    /**
     * Conversation type: PRIVATE (1:1) or GROUP (n participants)
     */
    @Indexed
    private ConversationType type;
    
    /**
     * List of participant user_id values.
     * - PRIVATE: Exactly 2 participants
     * - GROUP: N participants (max 100 per A-007)
     */
    private List<String> participants;
    
    /**
     * List of admin user IDs (for GROUP conversations only, null for PRIVATE).
     * Admins can add/remove members and promote other users to admin (per FR-017, FR-018).
     * Creator is automatically the first admin.
     */
    @Field("admin_user_ids")
    private List<String> adminUserIds;
    
    /**
     * Creator user ID (who created the conversation).
     * Automatically becomes the first admin in GROUP conversations (per FR-016).
     */
    @Field("creator_id")
    private String creatorId;
    
    /**
     * Creation timestamp
     */
    @Field("created_at")
    private Instant createdAt;
    
    /**
     * Last message timestamp (for sorting user's conversation list)
     */
    @Field("last_message_at")
    private Instant lastMessageAt;
    
    /**
     * Preview of last message text (e.g., "Hello world...")
     * Updated by MessageDeliveryWorker after persisting message.
     */
    @Field("last_message_preview")
    private String lastMessagePreview;
    
    /**
     * Factory method to create new private conversation.
     * 
     * @param conversationId UUID identifier
     * @param participant1   First user_id
     * @param participant2   Second user_id
     * @param creatorId      Creator user_id
     * @return Conversation instance with type PRIVATE
     */
    public static Conversation createPrivate(String conversationId, String participant1, String participant2, String creatorId) {
        Instant now = Instant.now();
        return Conversation.builder()
                .conversationId(conversationId)
                .type(ConversationType.PRIVATE)
                .participants(List.of(participant1, participant2))
                .creatorId(creatorId)
                .adminUserIds(null) // PRIVATE conversations don't have admins
                .createdAt(now)
                .lastMessageAt(now)
                .build();
    }
    
    /**
     * Factory method to create new group conversation (User Story 5 - P3).
     * 
     * Educational Note: Group conversations demonstrate fan-out messaging patterns.
     * Each message sent to a group must be delivered to all N participants, tracking
     * per-recipient state (DELIVERED/READ) separately. This teaches eventual consistency
     * and async processing at scale.
     * 
     * @param conversationId UUID identifier
     * @param participants   List of user_id values (max 100 per FR-015a)
     * @param creatorId      Creator user_id (becomes first admin per FR-016)
     * @return Conversation instance with type GROUP
     */
    public static Conversation createGroup(String conversationId, List<String> participants, String creatorId) {
        Instant now = Instant.now();
        return Conversation.builder()
                .conversationId(conversationId)
                .type(ConversationType.GROUP)
                .participants(new ArrayList<>(participants))
                .creatorId(creatorId)
                .adminUserIds(new ArrayList<>(List.of(creatorId))) // Creator is first admin
                .createdAt(now)
                .lastMessageAt(now)
                .build();
    }
    
    /**
     * Check if user is participant in conversation.
     * 
     * @param userId User ID to check
     * @return true if user is participant
     */
    public boolean isParticipant(String userId) {
        return participants != null && participants.contains(userId);
    }
    
    /**
     * Check if user is admin in GROUP conversation.
     * 
     * @param userId User ID to check
     * @return true if user is admin (always false for PRIVATE conversations)
     */
    public boolean isAdmin(String userId) {
        return type == ConversationType.GROUP 
                && adminUserIds != null 
                && adminUserIds.contains(userId);
    }
    
    /**
     * Get current member count.
     * 
     * @return Number of participants
     */
    public int getMemberCount() {
        return participants != null ? participants.size() : 0;
    }
    
    /**
     * Check if group is at maximum capacity (100 members per FR-015a).
     * 
     * @return true if group has 100 members
     */
    public boolean isAtMaxCapacity() {
        return type == ConversationType.GROUP && getMemberCount() >= 100;
    }
}
