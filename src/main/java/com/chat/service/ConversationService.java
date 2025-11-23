package com.chat.service;

import com.chat.model.Conversation;
import com.chat.model.ConversationType;
import com.chat.repository.ConversationRepository;
import com.chat.util.UuidValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Conversation Service (T031)
 * 
 * Responsibility: Business logic for conversation management - creation, listing, message preview updates.
 * Does NOT: Handle gRPC mapping (see ConversationServiceImpl), message operations (see MessageService).
 * 
 * Distributed Systems Concept: Conversation is an aggregate root that groups messages.
 * The last_message_preview is a denormalized field updated asynchronously by MessageDeliveryWorker,
 * demonstrating eventual consistency trade-off for read performance (avoid JOIN on message query).
 */
@Service
public class ConversationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);
    private static final int MAX_PAGINATION_LIMIT = 100; // Per A-007
    
    private final ConversationRepository conversationRepository;
    
    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }
    
    /**
     * Create new private conversation (T044).
     * 
     * Validates exactly 2 participants for PRIVATE type per FR-012.
     * Generates conversation_id UUID.
     * 
     * @param participant1 First user_id
     * @param participant2 Second user_id
     * @return Created Conversation entity
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public Conversation createConversation(String participant1, String participant2) {
        // Validate UUIDs
        UuidValidator.validateOrThrow(participant1, "participant1");
        UuidValidator.validateOrThrow(participant2, "participant2");
        
        // Validate not same user
        if (participant1.equals(participant2)) {
            throw new IllegalArgumentException("Cannot create conversation with same user twice");
        }
        
        // Generate conversation_id
        String conversationId = UuidValidator.generate();
        
        // Create conversation entity
        Conversation conversation = Conversation.createPrivate(conversationId, participant1, participant2);
        
        // Persist
        Conversation saved = conversationRepository.save(conversation);
        
        // T052: Log conversation creation per NFR-017
        logger.info("Conversation created - conversation_id: {}, type: {}, participants: [{}, {}]",
                conversationId, ConversationType.PRIVATE, participant1, participant2);
        
        return saved;
    }
    
    /**
     * List conversations for user (T045).
     * 
     * Queries conversations where user is participant, sorted by last_message_at descending.
     * Supports pagination with max limit 100 per A-007.
     * 
     * Distributed Systems Pattern: Pagination with cursor (offset/limit) to handle large result sets.
     * MongoDB query uses compound index (participants, lastMessageAt) for efficient filtering and sorting.
     * 
     * @param userId User UUID
     * @param limit  Items per page (max 100)
     * @param offset Starting position (0-indexed)
     * @return Page of Conversation entities
     */
    public Page<Conversation> listConversations(String userId, int limit, int offset) {
        // Validate user_id
        UuidValidator.validateOrThrow(userId, "user_id");
        
        // Validate pagination limits (T048 per FR-014)
        if (limit <= 0 || limit > MAX_PAGINATION_LIMIT) {
            throw new IllegalArgumentException(
                    "Pagination limit must be between 1 and " + MAX_PAGINATION_LIMIT);
        }
        
        if (offset < 0) {
            throw new IllegalArgumentException("Pagination offset must be >= 0");
        }
        
        // Create pageable with sort by lastMessageAt descending
        Pageable pageable = PageRequest.of(
                offset / limit,  // Page number
                limit,
                Sort.by(Sort.Direction.DESC, "lastMessageAt")
        );
        
        // Query conversations where user is participant
        Page<Conversation> conversations = conversationRepository
                .findByParticipantsContainingOrderByLastMessageAtDesc(userId, pageable);
        
        logger.debug("Listed conversations for user_id: {}, limit: {}, offset: {}, total: {}",
                userId, limit, offset, conversations.getTotalElements());
        
        return conversations;
    }
    
    /**
     * Update conversation's last message preview (T046).
     * 
     * Called by MessageDeliveryWorker after persisting message to MongoDB.
     * Updates last_message_at timestamp and last_message_preview text for conversation list display.
     * 
     * Distributed Systems Pattern: Eventual consistency via async update.
     * The message is already persisted; this updates denormalized preview field for read optimization.
     * If this update fails, conversation preview becomes stale but message is still queryable.
     * 
     * @param conversationId Conversation UUID
     * @param messagePreview First 100 chars of message text
     * @param timestamp      Message timestamp
     */
    @Transactional
    public void updateLastMessage(String conversationId, String messagePreview, Instant timestamp) {
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));
        
        // Truncate preview to 100 chars for display
        String preview = messagePreview.length() > 100 
                ? messagePreview.substring(0, 100) + "..." 
                : messagePreview;
        
        conversation.setLastMessageAt(timestamp);
        conversation.setLastMessagePreview(preview);
        
        conversationRepository.save(conversation);
        
        logger.debug("Updated last message preview - conversation_id: {}, timestamp: {}",
                conversationId, timestamp);
    }
}
