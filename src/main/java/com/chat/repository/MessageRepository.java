package com.chat.repository;

import com.chat.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * MessageRepository (data-model.md Entity 3 Repository)
 * 
 * Responsibility: Provides data access methods for Message entity.
 * Does NOT: Handle delivery logic (see MessageDeliveryWorker), manage state transitions (see MessageService).
 * 
 * Indexes (defined in Message entity):
 * - message_id: unique index (idempotency guarantee per FR-006)
 * - conversation_id + timestamp: compound index (conversation history pagination)
 * - conversation_id + sequence_number: compound index (strict ordering per FR-007)
 * - sender_id + timestamp: compound index (user's sent messages)
 */
@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    
    /**
     * Find message by UUID identifier.
     * Used for idempotency checks (FR-006) and status queries (FR-004).
     * 
     * @param messageId UUID message identifier
     * @return Optional Message
     */
    Optional<Message> findByMessageId(String messageId);
    
    /**
     * Check if message with ID already exists.
     * Used for idempotency validation before persisting message.
     * 
     * @param messageId UUID message identifier
     * @return true if message exists
     */
    boolean existsByMessageId(String messageId);
    
    /**
     * Find conversation messages sorted by timestamp descending (newest first).
     * Used for FR-008: conversation history with pagination.
     * 
     * Query uses compound index: (conversationId, timestamp DESC)
     * 
     * @param conversationId Conversation UUID
     * @param pageable       Pagination parameters (limit, offset)
     * @return Page of Messages
     */
    Page<Message> findByConversationIdOrderByTimestampDesc(String conversationId, Pageable pageable);
    
    /**
     * Find conversation messages sorted by sequence number (strict ordering).
     * Used for FR-007: preserve message ordering within conversation.
     * 
     * Query uses compound index: (conversationId, sequenceNumber ASC)
     * 
     * @param conversationId Conversation UUID
     * @param pageable       Pagination parameters
     * @return Page of Messages
     */
    Page<Message> findByConversationIdOrderBySequenceNumberAsc(String conversationId, Pageable pageable);
    
    /**
     * Find conversation messages after specific timestamp.
     * Used for incremental message fetching (e.g., "fetch messages since last sync").
     * 
     * @param conversationId Conversation UUID
     * @param since          Timestamp filter (exclusive)
     * @param pageable       Pagination parameters
     * @return Page of Messages
     */
    Page<Message> findByConversationIdAndTimestampAfterOrderByTimestampAsc(
            String conversationId, Instant since, Pageable pageable);
    
    /**
     * Find user's sent messages sorted by timestamp.
     * Used for user message history and analytics.
     * 
     * Query uses compound index: (senderId, timestamp DESC)
     * 
     * @param senderId Sender user_id
     * @param pageable Pagination parameters
     * @return Page of Messages
     */
    Page<Message> findBySenderIdOrderByTimestampDesc(String senderId, Pageable pageable);
    
    /**
     * Count messages in conversation.
     * Used for conversation statistics.
     * 
     * @param conversationId Conversation UUID
     * @return Message count
     */
    long countByConversationId(String conversationId);
}
