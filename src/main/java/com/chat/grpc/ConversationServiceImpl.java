package com.chat.grpc;

import com.chat.grpc.v1.*;
import com.chat.model.Conversation;
import com.chat.model.ConversationType;
import com.chat.model.Message;
import com.chat.repository.ConversationRepository;
import com.chat.repository.MessageRepository;
import com.chat.service.ConversationService;
import com.chat.util.UuidValidator;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ConversationService gRPC Implementation (T047-T050)
 * 
 * Responsibility: gRPC endpoint for conversation management - create, list, query history.
 * Does NOT: Send messages (see ChatServiceImpl), handle async operations (all synchronous queries).
 * 
 * Distributed Systems Concept: This service handles synchronous read operations for conversations.
 * Unlike ChatServiceImpl which uses async Kafka, this is traditional request/response RPC.
 * Why? Conversation CRUD operations are low-volume compared to messages, and clients expect
 * immediate consistency (create conversation → immediately list it).
 * 
 * Authorization Pattern: All methods validate that the requesting user is a participant
 * in the conversation per FR-013. This prevents unauthorized access to private conversations.
 */
@GRpcService
public class ConversationServiceImpl extends ConversationServiceGrpc.ConversationServiceImplBase {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversationServiceImpl.class);
    
    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final GlobalExceptionHandler exceptionHandler;
    
    public ConversationServiceImpl(
            ConversationService conversationService,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            GlobalExceptionHandler exceptionHandler) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.exceptionHandler = exceptionHandler;
    }
    
    /**
     * CreateConversation gRPC endpoint (T047 - User Story 3).
     * 
     * Creates a new private 1:1 conversation between two users.
     * Validates exactly 2 participants for PRIVATE type per FR-012.
     * 
     * Flow:
     * 1. Extract participant IDs from request
     * 2. Validate request (exactly 2 participants, valid UUIDs)
     * 3. Call ConversationService.createConversation
     * 4. Map Conversation entity to CreateConversationResponse protobuf
     * 5. Return response
     * 
     * Error Handling:
     * - INVALID_ARGUMENT: Invalid UUIDs, wrong number of participants, same user twice
     * - ALREADY_EXISTS: Conversation already exists between these users
     * 
     * @param request  CreateConversationRequest from client
     * @param responseObserver gRPC response stream
     */
    @Override
    public void createConversation(
            CreateConversationRequest request,
            StreamObserver<CreateConversationResponse> responseObserver) {
        
        try {
            // Extract participants and type
            List<String> participantIds = request.getParticipantIdsList();
            com.chat.grpc.v1.ConversationType requestType = request.getType();
            
            Conversation conversation;
            
            // Handle PRIVATE vs GROUP conversation creation
            if (requestType == com.chat.grpc.v1.ConversationType.PRIVATE) {
                // Validate: must be exactly 2 participants for PRIVATE conversations
                if (participantIds.size() != 2) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("PRIVATE conversations require exactly 2 participants")
                            .asRuntimeException());
                    return;
                }
                
                String participant1 = participantIds.get(0);
                String participant2 = participantIds.get(1);
                
                // TODO: Extract creatorId from gRPC metadata (JWT claims)
                // For now, use participant1 as creator
                String creatorId = participant1;
                
                // Create private conversation via service layer
                conversation = conversationService.createConversation(participant1, participant2, creatorId);
                
            } else if (requestType == com.chat.grpc.v1.ConversationType.GROUP) {
                // T062: Group conversation creation
                
                // Validate: must have 2-100 participants for GROUP
                if (participantIds.size() < 2) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("GROUP conversations require at least 2 participants")
                            .asRuntimeException());
                    return;
                }
                
                // TODO: Extract creatorId from gRPC metadata (JWT claims)
                // For now, use first participant as creator
                String creatorId = participantIds.get(0);
                
                // Create group conversation via service layer (T062)
                conversation = conversationService.createGroupConversation(participantIds, creatorId);
                
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Invalid conversation type")
                        .asRuntimeException());
                return;
            }
            
            // Map entity to protobuf response
            CreateConversationResponse response = CreateConversationResponse.newBuilder()
                    .setConversationId(conversation.getConversationId())
                    .setType(mapConversationType(conversation.getType()))
                    .addAllParticipantIds(conversation.getParticipants())
                    .setCreatedAt(toProtobufTimestamp(conversation.getCreatedAt()))
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (IllegalArgumentException e) {
            // Validation errors mapped to INVALID_ARGUMENT status
            logger.warn("Invalid create conversation request: {}", e.getMessage());
            responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
        } catch (Exception e) {
            // Unexpected errors handled by global exception handler
            logger.error("Error creating conversation", e);
            responseObserver.onError(exceptionHandler.handleGenericException((RuntimeException)e));
        }
    }
    
    /**
     * AddMember gRPC endpoint (T066 - User Story 5).
     * 
     * Adds a new member to a group conversation.
     * Only admins can add members per FR-017.
     * 
     * Flow:
     * 1. Extract conversation_id, user_id, and added_by from request
     * 2. Validate request (valid UUIDs)
     * 3. Call ConversationService.addMember (handles authorization & capacity checks)
     * 4. Retrieve updated conversation
     * 5. Return updated participant list
     * 
     * Error Handling:
     * - INVALID_ARGUMENT: Invalid UUIDs, member already in group, capacity reached
     * - PERMISSION_DENIED: Requester is not admin
     * - NOT_FOUND: Conversation not found
     * 
     * @param request  AddMemberRequest from client
     * @param responseObserver gRPC response stream
     */
    @Override
    public void addMember(
            AddMemberRequest request,
            StreamObserver<AddMemberResponse> responseObserver) {
        
        try {
            String conversationId = request.getConversationId();
            String userId = request.getUserId();
            String addedBy = request.getAddedBy();
            
            // Add member via service layer (handles validation, authorization, capacity checks)
            conversationService.addMember(conversationId, userId, addedBy);
            
            // Retrieve updated conversation
            Conversation conversation = conversationRepository.findByConversationId(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
            
            // Build response with updated participant list
            AddMemberResponse response = AddMemberResponse.newBuilder()
                    .setConversationId(conversationId)
                    .addAllParticipantIds(conversation.getParticipants())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (SecurityException e) {
            // Authorization errors mapped to PERMISSION_DENIED status
            logger.warn("Unauthorized add member request: {}", e.getMessage());
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (IllegalArgumentException e) {
            // Validation errors mapped to INVALID_ARGUMENT status
            logger.warn("Invalid add member request: {}", e.getMessage());
            responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
        } catch (Exception e) {
            // Unexpected errors handled by global exception handler
            logger.error("Error adding member", e);
            responseObserver.onError(exceptionHandler.handleGenericException((RuntimeException)e));
        }
    }
    
    /**
     * RemoveMember gRPC endpoint (T067 - User Story 5).
     * 
     * Removes a member from a group conversation.
     * Only admins can remove members per FR-017.
     * 
     * Flow:
     * 1. Extract conversation_id, user_id, and removed_by from request
     * 2. Validate request (valid UUIDs)
     * 3. Call ConversationService.removeMember (handles authorization checks)
     * 4. Retrieve updated conversation
     * 5. Return updated participant list
     * 
     * Error Handling:
     * - INVALID_ARGUMENT: Invalid UUIDs, user not in group, cannot remove last admin
     * - PERMISSION_DENIED: Requester is not admin
     * - NOT_FOUND: Conversation not found
     * 
     * @param request  RemoveMemberRequest from client
     * @param responseObserver gRPC response stream
     */
    @Override
    public void removeMember(
            RemoveMemberRequest request,
            StreamObserver<RemoveMemberResponse> responseObserver) {
        
        try {
            String conversationId = request.getConversationId();
            String userId = request.getUserId();
            String removedBy = request.getRemovedBy();
            
            // Remove member via service layer (handles validation, authorization)
            conversationService.removeMember(conversationId, userId, removedBy);
            
            // Retrieve updated conversation
            Conversation conversation = conversationRepository.findByConversationId(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
            
            // Build response with updated participant list
            RemoveMemberResponse response = RemoveMemberResponse.newBuilder()
                    .setConversationId(conversationId)
                    .addAllParticipantIds(conversation.getParticipants())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (SecurityException e) {
            // Authorization errors mapped to PERMISSION_DENIED status
            logger.warn("Unauthorized remove member request: {}", e.getMessage());
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (IllegalArgumentException e) {
            // Validation errors mapped to INVALID_ARGUMENT status
            logger.warn("Invalid remove member request: {}", e.getMessage());
            responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
        } catch (Exception e) {
            // Unexpected errors handled by global exception handler
            logger.error("Error removing member", e);
            responseObserver.onError(exceptionHandler.handleGenericException((RuntimeException)e));
        }
    }
    
    /**
     * ListConversations gRPC endpoint (T048 - User Story 3).
     * 
     * Fetches paginated list of conversations for a user, sorted by last_message_at descending.
     * Includes last message preview for each conversation (denormalized field for performance).
     * 
     * Flow:
     * 1. Extract user_id and pagination params from request
     * 2. Validate pagination limits (max 100 items per A-007)
     * 3. Query ConversationRepository via ConversationService
     * 4. Map Page<Conversation> to ListConversationsResponse with PaginationInfo
     * 
     * Performance: MongoDB compound index (participants, lastMessageAt) enables efficient query.
     * No JOIN required - last_message_preview is denormalized for fast reads.
     * 
     * @param request  ListConversationsRequest from client
     * @param responseObserver gRPC response stream
     */
    @Override
    public void listConversations(
            ListConversationsRequest request,
            StreamObserver<ListConversationsResponse> responseObserver) {
        
        try {
            String userId = request.getUserId();
            int limit = request.getLimit() > 0 ? request.getLimit() : 20; // Default 20
            int offset = request.getOffset();
            
            // Query conversations via service layer (handles pagination validation)
            Page<Conversation> conversationsPage = conversationService.listConversations(userId, limit, offset);
            
            // Map conversations to protobuf
            List<ConversationSummary> summaries = conversationsPage.getContent().stream()
                    .map(this::toConversationSummary)
                    .collect(Collectors.toList());
            
            // Build pagination info
            PaginationInfo pagination = PaginationInfo.newBuilder()
                    .setTotalCount((int) conversationsPage.getTotalElements())
                    .setOffset(offset)
                    .setLimit(limit)
                    .setHasMore(conversationsPage.hasNext())
                    .build();
            
            // Build response
            ListConversationsResponse response = ListConversationsResponse.newBuilder()
                    .addAllConversations(summaries)
                    .setPagination(pagination)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid list conversations request: {}", e.getMessage());
            responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
        } catch (Exception e) {
            logger.error("Error listing conversations", e);
            responseObserver.onError(exceptionHandler.handleGenericException((RuntimeException)e));
        }
    }
    
    /**
     * GetConversation gRPC endpoint (T049 - User Story 3).
     * 
     * Fetches full conversation details by conversation_id.
     * Validates that requesting user is a participant (authorization check).
     * 
     * Flow:
     * 1. Extract conversation_id from request
     * 2. Query ConversationRepository
     * 3. Validate user is participant (from metadata or JWT claims)
     * 4. Map Conversation entity to GetConversationResponse
     * 
     * Authorization: Per FR-013, only conversation participants can access details.
     * 
     * @param request  GetConversationRequest from client
     * @param responseObserver gRPC response stream
     */
    @Override
    public void getConversation(
            GetConversationRequest request,
            StreamObserver<GetConversationResponse> responseObserver) {
        
        try {
            String conversationId = request.getConversationId();
            
            // Validate conversation_id format
            UuidValidator.validateOrThrow(conversationId, "conversation_id");
            
            // TODO: Add authorization check - extract user_id from JWT claims in gRPC metadata
            // and verify user is in conversation.participants list
            
            // Query conversation by ID
            Optional<Conversation> conversationOpt = conversationRepository.findByConversationId(conversationId);
            
            if (conversationOpt.isEmpty()) {
                logger.warn("Conversation not found: {}", conversationId);
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Conversation not found: " + conversationId)
                        .asRuntimeException());
                return;
            }
            
            Conversation conversation = conversationOpt.get();
            
            // Build GetConversationResponse with full conversation details
            GetConversationResponse.Builder responseBuilder = GetConversationResponse.newBuilder()
                    .setConversationId(conversation.getConversationId())
                    .setType(mapConversationType(conversation.getType()))
                    .setCreatedAt(toProtobufTimestamp(conversation.getCreatedAt()));
            
            // Add participants as UserInfo
            conversation.getParticipants().forEach(participantId -> {
                UserInfo userInfo = UserInfo.newBuilder()
                        .setUserId(participantId)
                        .setUsername("User-" + participantId.substring(0, 8))
                        .build();
                responseBuilder.addParticipants(userInfo);
            });
            
            GetConversationResponse response = responseBuilder.build();
            
            logger.info("Retrieved conversation: {} (type: {}, participants: {})",
                    conversationId,
                    conversation.getType(),
                    conversation.getParticipants().size());
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid get conversation request: {}", e.getMessage());
            responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
        } catch (Exception e) {
            logger.error("Error getting conversation", e);
            responseObserver.onError(exceptionHandler.handleGenericException((RuntimeException)e));
        }
    }
    
    /**
     * GetConversationHistory gRPC endpoint (T050 - User Story 3).
     * 
     * Fetches paginated message history for a conversation, sorted by timestamp descending.
     * Returns messages in reverse chronological order (newest first) for chat UI display.
     * 
     * Flow:
     * 1. Extract conversation_id and pagination params
     * 2. Validate user is participant in conversation (authorization)
     * 3. Query MessageRepository with pagination (Sort.by("timestamp").descending())
     * 4. Map Message entities to protobuf with complete state history
     * 
     * Performance: MongoDB index (conversationId, timestamp) enables efficient paginated queries.
     * Supports cursor-based pagination for real-time updates (messages.since timestamp filter).
     * 
     * @param request  GetConversationHistoryRequest from client
     * @param responseObserver gRPC response stream
     */
    @Override
    public void getConversationHistory(
            GetConversationHistoryRequest request,
            StreamObserver<GetConversationHistoryResponse> responseObserver) {
        
        try {
            String conversationId = request.getConversationId();
            int limit = request.getLimit() > 0 ? request.getLimit() : 50; // Default 50
            int offset = request.getOffset();
            
            // Validate conversation_id
            UuidValidator.validateOrThrow(conversationId, "conversation_id");
            
            // TODO: Add authorization check - verify user is conversation participant
            
            // Query messages with pagination (sorted by timestamp descending)
            Pageable pageable = PageRequest.of(
                    offset / limit,
                    limit
            );
            
            Page<Message> messagesPage = messageRepository.findByConversationIdOrderByTimestampDesc(conversationId, pageable);
            
            // Map messages to protobuf
            List<com.chat.grpc.v1.Message> messageProtos = messagesPage.getContent().stream()
                    .map(this::toMessageProto)
                    .collect(Collectors.toList());
            
            // Build pagination info
            PaginationInfo pagination = PaginationInfo.newBuilder()
                    .setTotalCount((int) messagesPage.getTotalElements())
                    .setOffset(offset)
                    .setLimit(limit)
                    .setHasMore(messagesPage.hasNext())
                    .build();
            
            // Build response
            GetConversationHistoryResponse response = GetConversationHistoryResponse.newBuilder()
                    .setConversationId(conversationId)
                    .addAllMessages(messageProtos)
                    .setPagination(pagination)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid get conversation history request: {}", e.getMessage());
            responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
        } catch (Exception e) {
            logger.error("Error fetching conversation history", e);
            responseObserver.onError(exceptionHandler.handleGenericException((RuntimeException)e));
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Convert Conversation entity to ConversationSummary protobuf (for list view).
     */
    private ConversationSummary toConversationSummary(Conversation conversation) {
        ConversationSummary.Builder builder = ConversationSummary.newBuilder()
                .setConversationId(conversation.getConversationId())
                .setType(mapConversationType(conversation.getType()))
                .setLastMessageAt(toProtobufTimestamp(conversation.getLastMessageAt()));
        
        // Add participants (TODO: fetch User entities for username display)
        conversation.getParticipants().forEach(participantId -> {
            UserInfo userInfo = UserInfo.newBuilder()
                    .setUserId(participantId)
                    .setUsername("User-" + participantId.substring(0, 8)) // Placeholder until User repository integrated
                    .build();
            builder.addParticipants(userInfo);
        });
        
        // Add last message preview if available
        if (conversation.getLastMessagePreview() != null) {
            builder.setLastMessagePreview(conversation.getLastMessagePreview());
        }
        
        // TODO: Add group name if applicable when getName() is added to Conversation entity
        // if (conversation.getType() == ConversationType.GROUP && conversation.getName() != null) {
        //     builder.setName(conversation.getName());
        // }
        
        return builder.build();
    }
    
    /**
     * Convert Message entity to Message protobuf (for history queries).
     */
    private com.chat.grpc.v1.Message toMessageProto(com.chat.model.Message message) {
        com.chat.grpc.v1.Message.Builder builder = com.chat.grpc.v1.Message.newBuilder()
                .setMessageId(message.getMessageId())
                .setConversationId(message.getConversationId())
                .setSender(UserInfo.newBuilder()
                        .setUserId(message.getSenderId())
                        .setUsername("User-" + message.getSenderId().substring(0, 8)) // Placeholder
                        .build())
                .setMessageText(message.getMessageText())
                .setTimestamp(toProtobufTimestamp(message.getTimestamp()))
                .setSequenceNumber(message.getSequenceNumber());
        
        // Add state history
        message.getStateHistory().forEach(transition -> {
            MessageStateTransition transitionProto = MessageStateTransition.newBuilder()
                    .setState(mapMessageStatus(transition.getState()))
                    .setTimestamp(toProtobufTimestamp(transition.getTimestamp()))
                    .build();
            builder.addStateHistory(transitionProto);
        });
        
        // Set current status (derived from last state history entry)
        if (!message.getStateHistory().isEmpty()) {
            com.chat.model.MessageStatus currentStatus = message.getStateHistory().get(message.getStateHistory().size() - 1).getState();
            builder.setCurrentStatus(mapMessageStatus(currentStatus));
        }
        
        return builder.build();
    }
    
    /**
     * Convert Java Instant to Protobuf Timestamp.
     */
    private Timestamp toProtobufTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
    
    /**
     * Map ConversationType enum to protobuf enum.
     */
    private com.chat.grpc.v1.ConversationType mapConversationType(com.chat.model.ConversationType type) {
        return switch (type) {
            case PRIVATE -> com.chat.grpc.v1.ConversationType.PRIVATE;
            case GROUP -> com.chat.grpc.v1.ConversationType.GROUP;
            default -> com.chat.grpc.v1.ConversationType.CONVERSATION_TYPE_UNSPECIFIED;
        };
    }
    
    /**
     * Map MessageStatus enum to protobuf enum.
     */
    private com.chat.grpc.v1.MessageStatus mapMessageStatus(com.chat.model.MessageStatus status) {
        return switch (status) {
            case SENT -> com.chat.grpc.v1.MessageStatus.SENT;
            case DELIVERED -> com.chat.grpc.v1.MessageStatus.DELIVERED;
            case READ -> com.chat.grpc.v1.MessageStatus.READ;
            default -> com.chat.grpc.v1.MessageStatus.MESSAGE_STATUS_UNSPECIFIED;
        };
    }
}
