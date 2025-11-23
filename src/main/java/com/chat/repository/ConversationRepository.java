package com.chat.repository;

import com.chat.model.Conversation;
import com.chat.model.ConversationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ConversationRepository (data-model.md Entity 2 Repository)
 * 
 * Responsibility: Provides data access methods for Conversation entity.
 * Does NOT: Handle business logic (see ConversationService), validate participants.
 * 
 * Indexes (defined in Conversation entity):
 * - conversation_id: unique index (primary lookup)
 * - participants + last_message_at: compound index (user's conversation list sorted by recent activity)
 * - type: index (filter by PRIVATE vs GROUP)
 */
@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {
    
    /**
     * Find conversation by UUID identifier.
     * Primary lookup method for conversation operations.
     * 
     * @param conversationId UUID conversation identifier
     * @return Optional Conversation
     */
    Optional<Conversation> findByConversationId(String conversationId);
    
    /**
     * Find all conversations where user is participant, sorted by last message timestamp.
     * Used for FR-014: list user's conversations with most recent message preview.
     * 
     * Query uses compound index: (participants, lastMessageAt DESC)
     * 
     * @param userId   User UUID
     * @param pageable Pagination parameters (limit, offset, sort)
     * @return Page of Conversations
     */
    Page<Conversation> findByParticipantsContainingOrderByLastMessageAtDesc(String userId, Pageable pageable);
    
    /**
     * Find conversations by type and participant.
     * Used to filter user's conversations (e.g., only PRIVATE or only GROUP).
     * 
     * @param type       Conversation type (PRIVATE or GROUP)
     * @param userId     User UUID
     * @param pageable   Pagination parameters
     * @return Page of Conversations
     */
    Page<Conversation> findByTypeAndParticipantsContainingOrderByLastMessageAtDesc(
            ConversationType type, String userId, Pageable pageable);
    
    /**
     * Find private conversation between two specific participants.
     * Uses custom MongoDB query to match exact participants list.
     * 
     * @param type Conversation type (PRIVATE)
     * @param participants List of 2 user IDs
     * @return Optional Conversation
     */
    @Query("{'type': ?0, 'participants': {$all: ?1, $size: 2}}")
    Optional<Conversation> findPrivateConversationByParticipants(
            ConversationType type, List<String> participants);
}
