package com.chat.grpc;

import com.chat.grpc.v1.*;
import com.chat.kafka.v1.MessageEvent;
import com.chat.kafka.v1.StateUpdateEvent;
import com.chat.model.MessageStatus;
import com.chat.service.MessageService;
import com.chat.service.StreamingService;
import com.chat.util.UuidValidator;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

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
    private final KafkaTemplate<String, MessageEvent> messageKafkaTemplate;
    private final KafkaTemplate<String, StateUpdateEvent> stateKafkaTemplate;
    private final GlobalExceptionHandler exceptionHandler;
    
    public ChatServiceImpl(
            MessageService messageService,
            StreamingService streamingService,
            KafkaTemplate<String, MessageEvent> messageKafkaTemplate,
            KafkaTemplate<String, StateUpdateEvent> stateKafkaTemplate,
            GlobalExceptionHandler exceptionHandler) {
        this.messageService = messageService;
        this.streamingService = streamingService;
        this.messageKafkaTemplate = messageKafkaTemplate;
        this.stateKafkaTemplate = stateKafkaTemplate;
        this.exceptionHandler = exceptionHandler;
    }
    
    /**
     * SendMessage gRPC endpoint (T032 - User Story 1).
     * 
     * Flow:
     * 1. Validate request (MessageService validates message_id, conversation, sender authorization)
     * 2. Generate sequence number (atomic MongoDB increment per FR-007)
     * 3. Publish message event to Kafka (async persistence via MessageDeliveryWorker)
     * 4. Return success response immediately (fire-and-forget pattern)
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
            String messageId = request.getMessageId();
            String conversationId = request.getConversationId();
            String senderId = request.getSenderId();
            String recipientId = request.getRecipientId();
            String messageText = request.getMessageText();
            
            // Validate message (includes authorization, size limits, idempotency check)
            messageService.validateMessage(messageId, conversationId, senderId, recipientId, messageText);
            
            // Generate sequence number for message ordering (atomic per FR-007)
            Long sequenceNumber = messageService.generateSequenceNumber(conversationId);
            
            // Create Kafka event (Protobuf)
            Instant now = Instant.now();
            MessageEvent event = MessageEvent.newBuilder()
                    .setMessageId(messageId)
                    .setConversationId(conversationId)
                    .setSenderId(senderId)
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
            logger.error("Unexpected error in sendMessage", e);
            responseObserver.onError(exceptionHandler.handleGenericException(e));
        }
    }
    
    /**
     * GetMessageStatus gRPC endpoint (T040 - User Story 2).
     * 
     * Queries Message.stateHistory from MongoDB and returns current status with transition timeline.
     * 
     * @param request  GetMessageStatusRequest from client
     * @param responseObserver gRPC response stream
     */
    @Override
    public void getMessageStatus(GetMessageStatusRequest request, StreamObserver<GetMessageStatusResponse> responseObserver) {
        try {
            String messageId = request.getMessageId();
            UuidValidator.validateOrThrow(messageId, "message_id");
            
            // Query current status from MessageService
            com.chat.model.MessageStatus status = messageService.getCurrentStatus(messageId);
            
            // Map to protobuf enum
            com.chat.grpc.v1.MessageStatus protoStatus = mapStatusToProto(status);
            
            GetMessageStatusResponse response = GetMessageStatusResponse.newBuilder()
                    .setMessageId(messageId)
                    .setCurrentStatus(protoStatus)
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
