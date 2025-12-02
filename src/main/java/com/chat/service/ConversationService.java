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
     * @param creatorId Creator user_id
     * @return Created Conversation entity
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public Conversation createConversation(String participant1, String participant2, String creatorId) {
        // Validate UUIDs
        UuidValidator.validateOrThrow(participant1, "participant1");
        UuidValidator.validateOrThrow(participant2, "participant2");
        UuidValidator.validateOrThrow(creatorId, "creatorId");
        
        // Validate not same user
        if (participant1.equals(participant2)) {
            throw new IllegalArgumentException("Cannot create conversation with same user twice");
        }
        
        // Generate conversation_id
        String conversationId = UuidValidator.generate();
        
        // Create conversation entity
        Conversation conversation = Conversation.createPrivate(conversationId, participant1, participant2, creatorId);
        
        // Persist
        Conversation saved = conversationRepository.save(conversation);
        
        // T052: Log conversation creation per NFR-017
        logger.info("Conversation created - conversation_id: {}, type: {}, participants: [{}, {}], creator: {}",
                conversationId, ConversationType.PRIVATE, participant1, participant2, creatorId);
        
        return saved;
    }
    
    /**
     * Create new group conversation (T062 - User Story 5).
     * 
     * Validates participants (max 100 per FR-015a).
     * Sets creator as initial admin per FR-016.
     * Generates conversation_id UUID.
     * 
     * Educational Note: Group conversations demonstrate distributed consensus challenges.
     * With N participants, each message requires N delivery confirmations (fan-out pattern).
     * This teaches eventual consistency and async processing patterns.
     * 
     * @param participants List of user_id values (2 to 100 members)
     * @param creatorId    Creator user_id (becomes first admin)
     * @return Created Conversation entity
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public Conversation createGroupConversation(java.util.List<String> participants, String creatorId) {
        // Validate creator
        UuidValidator.validateOrThrow(creatorId, "creatorId");
        
        // Validate participants list
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException("Participants list cannot be empty");
        }
        
        // Validate participant count (min 2, max 100 per FR-015a)
        if (participants.size() < 2) {
            throw new IllegalArgumentException("Group must have at least 2 participants");
        }
        
        if (participants.size() > 100) {
            throw new IllegalArgumentException(
                    "Group member limit reached (100/100). Cannot create group with " + 
                    participants.size() + " members");
        }
        
        // Validate all participant UUIDs
        for (String participantId : participants) {
            UuidValidator.validateOrThrow(participantId, "participant");
        }
        
        // Validate creator is in participants list
        if (!participants.contains(creatorId)) {
            throw new IllegalArgumentException("Creator must be in participants list");
        }
        
        // Generate conversation_id
        String conversationId = UuidValidator.generate();
        
        // Create conversation entity (creator becomes first admin)
        Conversation conversation = Conversation.createGroup(conversationId, participants, creatorId);
        
        // Persist
        Conversation saved = conversationRepository.save(conversation);
        
        // T052: Log conversation creation per NFR-017
        logger.info("Group conversation created - conversation_id: {}, type: {}, participant_count: {}, creator: {}",
                conversationId, ConversationType.GROUP, participants.size(), creatorId);
        
        return saved;
    }
    
    /**
     * Add member to group conversation (T063 - User Story 5).
     * 
     * Only admins can add members per FR-017.
     * Validates group not at max capacity (100 members) per FR-017a.
     * 
     * @param conversationId Conversation UUID
     * @param newMemberId    User UUID to add
     * @param requesterId    User UUID making request (must be admin)
     * @throws IllegalArgumentException if validation fails
     * @throws SecurityException if requester is not admin
     */
    @Transactional
    public void addMember(String conversationId, String newMemberId, String requesterId) {
        // Validate UUIDs
        UuidValidator.validateOrThrow(conversationId, "conversationId");
        UuidValidator.validateOrThrow(newMemberId, "newMemberId");
        UuidValidator.validateOrThrow(requesterId, "requesterId");
        
        // Find conversation
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));
        
        // Validate conversation is GROUP type
        if (conversation.getType() != ConversationType.GROUP) {
            throw new IllegalArgumentException(
                    "Cannot add members to PRIVATE conversation");
        }
        
        // T069: Validate requester is admin (authorization check per FR-017)
        if (!conversation.isAdmin(requesterId)) {
            throw new SecurityException(
                    "Only admins can add members to group conversation");
        }
        
        // Validate new member not already in group
        if (conversation.isParticipant(newMemberId)) {
            throw new IllegalArgumentException(
                    "User is already a member of this conversation");
        }
        
        // Validate group not at max capacity (FR-017a)
        if (conversation.isAtMaxCapacity()) {
            throw new IllegalArgumentException(
                    "Group member limit reached (100/100). Remove members before adding new ones");
        }
        
        // Add member to participants list
        conversation.getParticipants().add(newMemberId);
        
        // Save updated conversation
        conversationRepository.save(conversation);
        
        logger.info("Member added to group - conversation_id: {}, new_member: {}, added_by: {}, member_count: {}",
                conversationId, newMemberId, requesterId, conversation.getMemberCount());
    }
    
    /**
     * Remove member from group conversation (T064 - User Story 5).
     * 
     * Only admins can remove members per FR-017.
     * Cannot remove last admin (must promote someone else first).
     * 
     * @param conversationId     Conversation UUID
     * @param memberIdToRemove   User UUID to remove
     * @param requesterId        User UUID making request (must be admin)
     * @throws IllegalArgumentException if validation fails
     * @throws SecurityException if requester is not admin
     */
    @Transactional
    public void removeMember(String conversationId, String memberIdToRemove, String requesterId) {
        // Validate UUIDs
        UuidValidator.validateOrThrow(conversationId, "conversationId");
        UuidValidator.validateOrThrow(memberIdToRemove, "memberIdToRemove");
        UuidValidator.validateOrThrow(requesterId, "requesterId");
        
        // Find conversation
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));
        
        // Validate conversation is GROUP type
        if (conversation.getType() != ConversationType.GROUP) {
            throw new IllegalArgumentException(
                    "Cannot remove members from PRIVATE conversation");
        }
        
        // T069: Validate requester is admin (authorization check per FR-017)
        if (!conversation.isAdmin(requesterId)) {
            throw new SecurityException(
                    "Only admins can remove members from group conversation");
        }
        
        // Validate member is in group
        if (!conversation.isParticipant(memberIdToRemove)) {
            throw new IllegalArgumentException(
                    "User is not a member of this conversation");
        }
        
        // Validate not removing last admin
        if (conversation.isAdmin(memberIdToRemove) && conversation.getAdminUserIds().size() == 1) {
            throw new IllegalArgumentException(
                    "Cannot remove last admin. Promote another member to admin first");
        }
        
        // Remove member from participants list
        conversation.getParticipants().remove(memberIdToRemove);
        
        // If member was admin, remove from admin list too
        if (conversation.isAdmin(memberIdToRemove)) {
            conversation.getAdminUserIds().remove(memberIdToRemove);
        }
        
        // Save updated conversation
        conversationRepository.save(conversation);
        
        logger.info("Member removed from group - conversation_id: {}, removed_member: {}, removed_by: {}, member_count: {}",
                conversationId, memberIdToRemove, requesterId, conversation.getMemberCount());
    }
    
    /**
     * Promote user to admin in group conversation (T065 - User Story 5).
     * 
     * Only admins can promote other users per FR-018.
     * User must already be a member of the group.
     * 
     * @param conversationId     Conversation UUID
     * @param userIdToPromote    User UUID to promote
     * @param requesterId        User UUID making request (must be admin)
     * @throws IllegalArgumentException if validation fails
     * @throws SecurityException if requester is not admin
     */
    @Transactional
    public void promoteToAdmin(String conversationId, String userIdToPromote, String requesterId) {
        // Validate UUIDs
        UuidValidator.validateOrThrow(conversationId, "conversationId");
        UuidValidator.validateOrThrow(userIdToPromote, "userIdToPromote");
        UuidValidator.validateOrThrow(requesterId, "requesterId");
        
        // Find conversation
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));
        
        // Validate conversation is GROUP type
        if (conversation.getType() != ConversationType.GROUP) {
            throw new IllegalArgumentException(
                    "Cannot promote users in PRIVATE conversation");
        }
        
        // T069: Validate requester is admin (authorization check per FR-018)
        if (!conversation.isAdmin(requesterId)) {
            throw new SecurityException(
                    "Only admins can promote users to admin role");
        }
        
        // Validate user is a member
        if (!conversation.isParticipant(userIdToPromote)) {
            throw new IllegalArgumentException(
                    "User is not a member of this conversation");
        }
        
        // Validate user is not already admin
        if (conversation.isAdmin(userIdToPromote)) {
            throw new IllegalArgumentException(
                    "User is already an admin");
        }
        
        // Add user to admin list
        conversation.getAdminUserIds().add(userIdToPromote);
        
        // Save updated conversation
        conversationRepository.save(conversation);
        
        logger.info("User promoted to admin - conversation_id: {}, promoted_user: {}, promoted_by: {}, admin_count: {}",
                conversationId, userIdToPromote, requesterId, conversation.getAdminUserIds().size());
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
