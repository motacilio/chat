package com.chat.grpc;

import com.chat.exception.RateLimitExceededException;
import com.chat.grpc.v1.*;
import com.chat.kafka.v1.MessageEvent;
import com.chat.kafka.v1.StateUpdateEvent;
import com.chat.model.Conversation;
import com.chat.model.MessageStatus;
import com.chat.repository.ConversationRepository;
import com.chat.service.MessageService;
import com.chat.service.RateLimitService;
import com.chat.service.StreamingService;
import com.chat.util.UuidValidator;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ChatService gRPC Implementation (T032)
 * 
 * Responsibility: gRPC endpoint for message operations - request/response mapping, Kafka publishing.
 * Does NOT: Handle persistence (delegated to MessageDeliveryWorker via Kafka), business validation (see MessageService).
 * 
 * Distributed Systems Concept: This is the API layer that accepts synchronous gRPC requests
 * and converts them to asynchronous events published to Kafka. Clients get immediate response
 * (message accepted) while actual persistence happens asynchronously in background worker.
 * This enables horizontal scaling - multiple API instances can publish to Kafka without coordination.
 * 
 * Architecture Pattern: Command-Query Responsibility Segregation (CQRS)
 * - SendMessage = COMMAND (write operation, async via Kafka)
 * - GetMessageStatus = QUERY (read operation, direct MongoDB query)
 */
@GRpcService
public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);
    private static final String MESSAGE_EVENTS_TOPIC = "message-events";
    private static final String STATE_UPDATE_EVENTS_TOPIC = "state-update-events";
    
    private final MessageService messageService;
    private final StreamingService streamingService;
    private final RateLimitService rateLimitService;
    private final ConversationRepository conversationRepository;
    private final KafkaTemplate<String, MessageEvent> messageKafkaTemplate;
    private final KafkaTemplate<String, StateUpdateEvent> stateKafkaTemplate;
    private final GlobalExceptionHandler exceptionHandler;
    
    public ChatServiceImpl(
            MessageService messageService,
            StreamingService streamingService,
            RateLimitService rateLimitService,
            ConversationRepository conversationRepository,
            KafkaTemplate<String, MessageEvent> messageKafkaTemplate,
            KafkaTemplate<String, StateUpdateEvent> stateKafkaTemplate,
            GlobalExceptionHandler exceptionHandler) {
        this.messageService = messageService;
        this.streamingService = streamingService;
        this.rateLimitService = rateLimitService;
        this.conversationRepository = conversationRepository;
        this.messageKafkaTemplate = messageKafkaTemplate;
        this.stateKafkaTemplate = stateKafkaTemplate;
        this.exceptionHandler = exceptionHandler;
    }
    
    /**
     * SendMessage gRPC endpoint (T032 - User Story 1).
     * 
     * Flow:
     * 1. Check rate limit (100 messages/minute per user)
     * 2. Validate request (MessageService validates message_id, conversation, sender authorization)
     * 3. Generate sequence number (atomic MongoDB increment per FR-007)
     * 4. Publish message event to Kafka (async persistence via MessageDeliveryWorker)
     * 5. Return success response immediately (fire-and-forget pattern)
     * 
     * Rate Limiting: 100 messages per minute per user (configured in application.yml)
     * - Throws RESOURCE_EXHAUSTED if limit exceeded
     * - Rate limit resets every 60 seconds
     * 
     * Idempotency: Client provides message_id (UUID). Duplicate requests return success without re-publishing.
     * 
     * Performance: p95 latency target <100ms (per NFR-003) - achieved by async Kafka publish vs sync MongoDB write.
     * 
     * @param request  SendMessageRequest from client
     * @param responseObserver gRPC response stream
     */
    @Override
    public void sendMessage(SendMessageRequest request, StreamObserver<SendMessageResponse> responseObserver) {
        try {
            // Server generates message_id (prevents client conflicts and ensures uniqueness)
            String messageId = UuidValidator.generate();
            String conversationId = request.getConversationId();
            String senderId = request.getSenderId();
            String recipientId = request.getRecipientId();
            String messageText = request.getMessageText();
            
            // SECURITY: Validate sender_id matches authenticated user from JWT token
            String authenticatedUserId = com.chat.security.AuthenticationInterceptor.USER_ID_CONTEXT_KEY.get();
            if (authenticatedUserId == null) {
                logger.error("SECURITY: No authenticated user in context");
                responseObserver.onError(Status.UNAUTHENTICATED
                        .withDescription("Authentication required")
                        .asRuntimeException());
                return;
            }
            
            if (!senderId.equals(authenticatedUserId)) {
                logger.error("SECURITY: sender_id mismatch - token userId: {}, request senderId: {}", 
                            authenticatedUserId, senderId);
                responseObserver.onError(Status.PERMISSION_DENIED
                        .withDescription("sender_id must match authenticated user")
                        .asRuntimeException());
                return;
            }
            
            // Rate limiting: Check if user exceeded 100 messages/minute
            try {
                rateLimitService.checkMessageSendingLimit(senderId);
            } catch (RateLimitExceededException e) {
                logger.warn("Rate limit exceeded for user: {} - operation: {}, retry after: {}s",
                        e.getUserId(), e.getOperation(), e.getRetryAfterSeconds());
                responseObserver.onError(Status.RESOURCE_EXHAUSTED
                        .withDescription(String.format("Rate limit exceeded: %s. Retry after %d seconds.",
                                e.getMessage(), e.getRetryAfterSeconds()))
                        .asRuntimeException());
                return;
            }
            
            // Validate message (includes authorization, size limits, idempotency check)
            messageService.validateMessage(messageId, conversationId, senderId, recipientId, messageText);
            
            // Generate sequence number for message ordering (atomic per FR-007)
            Long sequenceNumber = messageService.generateSequenceNumber(conversationId);
            
            // Get conversation to extract all participants for group message fanout
            Conversation conversation = conversationRepository.findByConversationId(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
            
            // Build recipient list: all participants EXCEPT sender
            List<String> recipientIds = conversation.getParticipants().stream()
                    .filter(participantId -> !participantId.equals(senderId))
                    .toList();
            
            // Create Kafka event (Protobuf)
            Instant now = Instant.now();
            MessageEvent event = MessageEvent.newBuilder()
                    .setMessageId(messageId)
                    .setConversationId(conversationId)
                    .setSenderId(senderId)
                    .addAllRecipientIds(recipientIds)  // All participants (excluding sender) for streaming fanout
                    .setMessageText(messageText)
                    .setSequenceNumber(sequenceNumber)
                    .setTimestamp(Timestamps.fromMillis(now.toEpochMilli()))
                    .build();
            
            // Publish to Kafka (async persistence)
            // Key = conversation_id ensures all messages for same conversation go to same partition (ordering)
            messageKafkaTemplate.send(MESSAGE_EVENTS_TOPIC, conversationId, event);
            
            // T036: Log message submission per NFR-017
            logger.info("Message published to Kafka - message_id: {}, conversation_id: {}, sender_id: {}, sequence: {}",
                    messageId, conversationId, senderId, sequenceNumber);
            
            // Build response
            SendMessageResponse response = SendMessageResponse.newBuilder()
                    .setMessageId(messageId)
                    .setStatus(com.chat.grpc.v1.MessageStatus.SENT)  // Initial status
                    .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(now.getEpochSecond())
                            .setNanos(now.getNano())
                            .build())
                    .setSequenceNumber(sequenceNumber)  // Message ordering number
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (IllegalArgumentException e) {
            responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
        } catch (SecurityException e) {
            responseObserver.onError(exceptionHandler.permissionDenied(e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("Unexpected error in sendMessage", e);
            responseObserver.onError(exceptionHandler.handleGenericException(e));
        }
    }
    
    /**
     * GetMessageStatus gRPC endpoint (T040 - User Story 2).
     * 
     * Queries Message.stateHistory from MongoDB and returns current status with transition timeline.
     * For group messages, builds per-recipient status showing who read the message.
     * 
     * @param request  GetMessageStatusRequest from client
     * @param responseObserver gRPC response stream
     */
    @Override
    public void getMessageStatus(GetMessageStatusRequest request, StreamObserver<GetMessageStatusResponse> responseObserver) {
        try {
            String messageId = request.getMessageId();
            UuidValidator.validateOrThrow(messageId, "message_id");
            
            // Query message with complete state history from MessageService
            com.chat.model.Message message = messageService.getMessage(messageId);
            
            // Map complete state history to protobuf
            List<com.chat.grpc.v1.MessageStateTransition> protoStateHistory = message.getStateHistory()
                    .stream()
                    .map(this::mapStateTransitionToProto)
                    .collect(java.util.stream.Collectors.toList());
            
            // Build per-recipient read status for groups
            List<com.chat.grpc.v1.RecipientReadStatus> recipientStatuses = buildRecipientStatuses(message);
            
            // Calculate intelligent current_status based on recipient statuses
            com.chat.grpc.v1.MessageStatus protoStatus = calculateOverallStatus(recipientStatuses);
            
            GetMessageStatusResponse response = GetMessageStatusResponse.newBuilder()
                    .setMessageId(messageId)
                    .addAllStateHistory(protoStateHistory)
                    .setCurrentStatus(protoStatus)
                    .addAllRecipientStatus(recipientStatuses)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (IllegalArgumentException e) {
            responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
        } catch (RuntimeException e) {
            logger.error("Unexpected error in getMessageStatus", e);
            responseObserver.onError(exceptionHandler.handleGenericException(e));
        }
    }
    
    /**
     * Build per-recipient read status from state history.
     * Shows who read the message (for groups) or single recipient status (for 1:1).
     * 
     * @param message Message entity with state history
     * @return List of RecipientReadStatus (one per recipient)
     */
    private List<com.chat.grpc.v1.RecipientReadStatus> buildRecipientStatuses(com.chat.model.Message message) {
        // Get conversation to know ALL participants
        com.chat.model.Conversation conversation = messageService.getConversationForMessage(message.getMessageId());
        
        // Group state transitions by recipient_id
        // Include ALL states (SENT, DELIVERED, READ) to properly track recipient status
        java.util.Map<String, java.util.List<com.chat.model.MessageStateTransition>> transitionsByRecipient = 
            message.getStateHistory()
                .stream()
                .filter(t -> t.getRecipientId() != null && !t.getRecipientId().isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(
                    com.chat.model.MessageStateTransition::getRecipientId
                ));
        
        // Build status for ALL participants (not just those who interacted)
        // Exclude sender from recipient list (sender doesn't "receive" their own message)
        return conversation.getParticipants().stream()
            .filter(participantId -> !participantId.equals(message.getSenderId()))
            .map(participantId -> {
                // Get transitions for this specific participant
                java.util.List<com.chat.model.MessageStateTransition> transitions = 
                    transitionsByRecipient.getOrDefault(participantId, java.util.Collections.emptyList());
                
                // Find DELIVERED and READ timestamps for this participant
                com.google.protobuf.Timestamp deliveredAt = null;
                com.google.protobuf.Timestamp readAt = null;
                com.chat.grpc.v1.MessageStatus status = com.chat.grpc.v1.MessageStatus.SENT;
                
                for (com.chat.model.MessageStateTransition t : transitions) {
                    if (t.getState() == com.chat.model.MessageStatus.DELIVERED) {
                        deliveredAt = toProtobufTimestamp(t.getTimestamp());
                        status = com.chat.grpc.v1.MessageStatus.DELIVERED;
                    } else if (t.getState() == com.chat.model.MessageStatus.READ) {
                        readAt = toProtobufTimestamp(t.getTimestamp());
                        status = com.chat.grpc.v1.MessageStatus.READ;
                    }
                }
                
                com.chat.grpc.v1.RecipientReadStatus.Builder builder = com.chat.grpc.v1.RecipientReadStatus.newBuilder()
                        .setUserId(participantId)
                        .setStatus(status);
                
                if (deliveredAt != null) {
                    builder.setDeliveredAt(deliveredAt);
                }
                if (readAt != null) {
                    builder.setReadAt(readAt);
                }
                
                return builder.build();
            })
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Calculate overall message status based on all recipients' statuses.
     * 
     * Logic:
     * - SENT: All recipients still in SENT state (none received)
     * - DELIVERED: All recipients in SENT or DELIVERED (none read yet)
     * - PARTIALLY_READ: Some recipients read, others didn't
     * - READ: All recipients read the message
     * 
     * @param recipientStatuses List of recipient read statuses
     * @return Overall MessageStatus
     */
    private com.chat.grpc.v1.MessageStatus calculateOverallStatus(
            List<com.chat.grpc.v1.RecipientReadStatus> recipientStatuses) {
        
        if (recipientStatuses.isEmpty()) {
            return com.chat.grpc.v1.MessageStatus.SENT;
        }
        
        long readCount = recipientStatuses.stream()
                .filter(r -> r.getStatus() == com.chat.grpc.v1.MessageStatus.READ)
                .count();
        
        long deliveredCount = recipientStatuses.stream()
                .filter(r -> r.getStatus() == com.chat.grpc.v1.MessageStatus.DELIVERED)
                .count();
        
        int totalRecipients = recipientStatuses.size();
        
        // All recipients read the message
        if (readCount == totalRecipients) {
            return com.chat.grpc.v1.MessageStatus.READ;
        }
        
        // Some recipients read, others didn't
        if (readCount > 0) {
            return com.chat.grpc.v1.MessageStatus.PARTIALLY_READ;
        }
        
        // At least one recipient received (but none read yet)
        if (deliveredCount > 0) {
            return com.chat.grpc.v1.MessageStatus.DELIVERED;
        }
        
        // All recipients still in SENT state
        return com.chat.grpc.v1.MessageStatus.SENT;
    }
    
    /**
     * MarkMessageAsRead gRPC endpoint (T041 - User Story 2).
     * 
     * Validates user is participant, then publishes state update event to Kafka.
     * MessageStateUpdateWorker consumes event and updates Message.stateHistory.
     * 
     * @param request  MarkMessageAsReadRequest from client
     * @param responseObserver gRPC response stream
     */
    @Override
    public void markMessageAsRead(MarkMessageAsReadRequest request, StreamObserver<MarkMessageAsReadResponse> responseObserver) {
        try {
            String messageId = request.getMessageId();
            String userId = request.getUserId();
            
            // Validate UUIDs
            UuidValidator.validateOrThrow(messageId, "message_id");
            UuidValidator.validateOrThrow(userId, "user_id");
            
            // SECURITY: Validate user_id matches authenticated user from JWT token
            String authenticatedUserId = com.chat.security.AuthenticationInterceptor.USER_ID_CONTEXT_KEY.get();
            if (authenticatedUserId == null) {
                logger.error("SECURITY: No authenticated user in context");
                responseObserver.onError(Status.UNAUTHENTICATED
                        .withDescription("Authentication required")
                        .asRuntimeException());
                return;
            }
            
            if (!userId.equals(authenticatedUserId)) {
                logger.error("SECURITY: user_id mismatch - token userId: {}, request userId: {}", 
                            authenticatedUserId, userId);
                responseObserver.onError(Status.PERMISSION_DENIED
                        .withDescription("user_id must match authenticated user")
                        .asRuntimeException());
                return;
            }
            
            // Validate user is participant (T042 authorization check)
            String conversationId = messageService.markAsRead(messageId, userId);
            
            // Publish state-update event to Kafka (T041 - Protobuf)
            Instant now = Instant.now();
            StateUpdateEvent stateEvent = StateUpdateEvent.newBuilder()
                    .setMessageId(messageId)
                    .setNewStatus(StateUpdateEvent.MessageStatus.READ)
                    .setUserId(userId)
                    .setTimestamp(Timestamps.fromMillis(now.toEpochMilli()))
                    .setConversationId(conversationId)
                    .build();
            
            // Key = message_id ensures all state updates for same message go to same partition (ordering)
            stateKafkaTemplate.send(STATE_UPDATE_EVENTS_TOPIC, messageId, stateEvent);
            MarkMessageAsReadResponse response = MarkMessageAsReadResponse.newBuilder()
                    .setMessageId(messageId)
                    .setStatus(com.chat.grpc.v1.MessageStatus.READ)
                    .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(now.getEpochSecond())
                            .setNanos(now.getNano())
                            .build())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (IllegalArgumentException e) {
            responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
        } catch (SecurityException e) {
            responseObserver.onError(exceptionHandler.permissionDenied(e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("Unexpected error in markMessageAsRead", e);
            responseObserver.onError(exceptionHandler.handleGenericException(e));
        }
    }
    
    /**
     * Map domain MessageStatus to protobuf MessageStatus.
     */
    private com.chat.grpc.v1.MessageStatus mapStatusToProto(com.chat.model.MessageStatus status) {
        if (status == null) {
            return com.chat.grpc.v1.MessageStatus.MESSAGE_STATUS_UNSPECIFIED;
        }
        
        switch (status) {
            case SENT:
                return com.chat.grpc.v1.MessageStatus.SENT;
            case DELIVERED:
                return com.chat.grpc.v1.MessageStatus.DELIVERED;
            case READ:
                return com.chat.grpc.v1.MessageStatus.READ;
            default:
                return com.chat.grpc.v1.MessageStatus.MESSAGE_STATUS_UNSPECIFIED;
        }
    }
    
    /**
     * Map domain MessageStateTransition to protobuf MessageStateTransition.
     */
    private com.chat.grpc.v1.MessageStateTransition mapStateTransitionToProto(
            com.chat.model.MessageStateTransition transition) {
        
        com.chat.grpc.v1.MessageStateTransition.Builder builder = 
                com.chat.grpc.v1.MessageStateTransition.newBuilder()
                        .setState(mapStatusToProto(transition.getState()))
                        .setTimestamp(toProtobufTimestamp(transition.getTimestamp()));
        
        // recipient_id is optional (only present for DELIVERED and READ states)
        if (transition.getRecipientId() != null && !transition.getRecipientId().isEmpty()) {
            builder.setRecipientId(transition.getRecipientId());
        }
        
        return builder.build();
    }
    
    /**
     * Convert Java Instant to protobuf Timestamp.
     */
    private com.google.protobuf.Timestamp toProtobufTimestamp(java.time.Instant instant) {
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
    
    /**
     * StreamMessages gRPC endpoint (T090 - Real-Time Streaming).
     * 
     * Server-side streaming RPC that pushes MessageEvent to client whenever new messages arrive.
     * This provides real-time delivery for online users without polling.
     * 
     * Flow:
     * 1. Client calls StreamMessages(SubscribeRequest) with user_id
     * 2. Server registers stream in StreamingService
     * 3. MessageDeliveryWorker checks if user is online when persisting message
     * 4. If online: Push MessageEvent via this stream
     * 5. Client receives message in real-time (<100ms latency)
     * 
     * Lifecycle Management:
     * - Stream stays open until client disconnects or calls onCompleted
     * - On error/disconnect: StreamingService automatically unsubscribes user
     * - On reconnect: Client must re-establish stream (stateless protocol)
     * 
     * Distributed Systems Concept: Server-side streaming enables "push" delivery model.
     * Unlike polling (client queries every N seconds), server pushes when data available.
     * Benefits:
     * - Lower latency: Immediate delivery vs polling interval delay
     * - Lower overhead: No repeated query requests, single long-lived connection
     * - Better UX: Real-time chat experience
     * 
     * Tradeoff: Connection state must be managed (handle disconnects, reconnects).
     * StreamingService uses ConcurrentHashMap for thread-safe stream management.
     * 
     * @param request SubscribeRequest with user_id and optional conversation filters
     * @param responseObserver Server-side streaming observer to push MessageEvent
     */
    @Override
    public void streamMessages(
            SubscribeRequest request,
            StreamObserver<com.chat.grpc.v1.MessageEvent> responseObserver) {
        
        try {
            String userId = request.getUserId();
            UuidValidator.validateOrThrow(userId, "user_id");
            
            // SECURITY: Validate user_id matches authenticated user from JWT token
            String authenticatedUserId = com.chat.security.AuthenticationInterceptor.USER_ID_CONTEXT_KEY.get();
            if (authenticatedUserId == null) {
                logger.error("SECURITY: No authenticated user in context");
                responseObserver.onError(Status.UNAUTHENTICATED
                        .withDescription("Authentication required")
                        .asRuntimeException());
                return;
            }
            
            if (!userId.equals(authenticatedUserId)) {
                logger.error("SECURITY: user_id mismatch - token userId: {}, request userId: {}", 
                            authenticatedUserId, userId);
                responseObserver.onError(Status.PERMISSION_DENIED
                        .withDescription("user_id must match authenticated user")
                        .asRuntimeException());
                return;
            }
            
            // Register stream in StreamingService
            streamingService.subscribeToMessages(userId, responseObserver);
            
            // T094: Log stream lifecycle
            logger.info("User {} opened message stream (active streams: {})",
                    userId, streamingService.getActiveMessageStreamCount());
            
            // Stream lifecycle management:
            // - Stream stays open until client calls onCompleted or connection breaks
            // - No need to call responseObserver.onCompleted here (long-lived stream)
            // - MessageDeliveryWorker will call responseObserver.onNext for each message
            
            // Handle client disconnect/error
            // Note: gRPC automatically calls onError/onCompleted when client disconnects
            // We rely on StreamingService try-catch to detect broken streams and unsubscribe
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid stream messages request: {}", e.getMessage());
            responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
        } catch (Exception e) {
            logger.error("Error establishing message stream", e);
            responseObserver.onError(exceptionHandler.handleGenericException((RuntimeException) e));
        }
    }
}
