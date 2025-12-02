package com.chat.worker;

import com.chat.grpc.v1.NewMessageEvent;
import com.chat.grpc.v1.UserInfo;
import com.chat.model.Message;
import com.chat.model.MessageStatus;
import com.chat.model.MessageStateTransition;
import com.chat.repository.MessageRepository;
import com.chat.service.ConversationService;
import com.chat.service.PlatformRoutingService;
import com.chat.service.StreamingService;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MessageDeliveryWorker (T034)
 * 
 * Responsibility: Kafka consumer that persists messages to MongoDB AND routes to platform adapters.
 * Consumes message-events topic, creates Message entities, updates Conversation.lastMessage preview,
 * and triggers platform-specific delivery (WhatsApp, Instagram).
 * 
 * Distributed Systems Concept: This is the write-side of the CQRS pattern.
 * - ChatServiceImpl publishes events (command side)
 * - MessageDeliveryWorker consumes events (query side update)
 * - Multiple workers can consume from different partitions for horizontal scaling
 * - Kafka partitions ensure ordering per conversation (key = conversation_id)
 * 
 * Idempotency Strategy: Check if Message with message_id already exists before inserting.
 * This handles duplicate Kafka messages (at-least-once delivery semantics).
 * 
 * Error Handling: 
 * - Business errors (validation failures): Log and commit offset (message is invalid, skip it)
 * - Transient errors (DB connection): Don't commit offset, Kafka will retry
 * - DLQ: After max retries, move to dead-letter queue for manual investigation
 */
@Component
public class MessageDeliveryWorker {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageDeliveryWorker.class);
    
    private final MessageRepository messageRepository;
    private final ConversationService conversationService;
    private final StreamingService streamingService;
    private final PlatformRoutingService platformRoutingService;
    
    // Métricas Prometheus
    private final Counter messagesProcessedCounter;
    private final Counter messagesPersistedCounter;
    private final Timer kafkaProcessingTimer;
    private final Timer mongodbPersistTimer;
    
    public MessageDeliveryWorker(
            MessageRepository messageRepository,
            ConversationService conversationService,
            StreamingService streamingService,
            PlatformRoutingService platformRoutingService,
            MeterRegistry meterRegistry) {
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
        this.streamingService = streamingService;
        this.platformRoutingService = platformRoutingService;
        
        // Inicializar métricas
        this.messagesProcessedCounter = Counter.builder("messages_processed_total")
                .description("Total de mensagens processadas pelo worker Kafka")
                .tag("status", "SENT")
                .register(meterRegistry);
        
        this.messagesPersistedCounter = Counter.builder("messages_persisted_total")
                .description("Total de mensagens persistidas no MongoDB")
                .register(meterRegistry);
        
        this.kafkaProcessingTimer = Timer.builder("kafka_message_processing_latency_seconds")
                .description("Latência de processamento de mensagens do Kafka")
                .publishPercentiles(0.50, 0.95, 0.99)
                .register(meterRegistry);
        
        this.mongodbPersistTimer = Timer.builder("mongodb_persist_latency_seconds")
                .description("Latência de persistência no MongoDB")
                .publishPercentiles(0.50, 0.95, 0.99)
                .register(meterRegistry);
    }
    
    /**
     * Kafka consumer for message-events topic (T034).
     * 
     * Concurrency: Multiple consumer instances read from different partitions (scaling).
     * Ordering: Messages for same conversation_id go to same partition (Kafka key).
     * Acknowledgment: Manual commit - only commit offset after successful MongoDB write.
     * 
     * Flow:
     * 1. Check idempotency (message_id already exists?)
     * 2. Create Message entity with initial state SENT
     * 3. Persist to MongoDB
     * 4. Update Conversation.lastMessage denormalized field
     * 5. Commit Kafka offset
     * 
     * @param event  MessageEventDto from Kafka
     * @param acknowledgment Manual offset commit control
     */
    @KafkaListener(
            topics = "message-events",
            groupId = "message-delivery-workers",
            containerFactory = "messageEventKafkaListenerContainerFactory"
    )
    public void handleMessageEvent(com.chat.kafka.v1.MessageEvent event, Acknowledgment acknowledgment) {
        // Medir latência total de processamento Kafka
        kafkaProcessingTimer.record(() -> {
            try {
                String messageId = event.getMessageId();
                String conversationId = event.getConversationId();
                
                // Incrementar contador de mensagens processadas
                messagesProcessedCounter.increment();
                
                // Idempotency check - if message already exists, skip persistence but still do routing
                boolean messageExists = messageRepository.existsByMessageId(messageId);
                
                if (!messageExists) {
                    // Medir latência de persistência MongoDB
                    mongodbPersistTimer.record(() -> {
                        // Create Message entity from Protobuf
                        Instant timestamp = Instant.ofEpochSecond(
                            event.getTimestamp().getSeconds(),
                            event.getTimestamp().getNanos()
                        );
                        Message message = new Message();
                        message.setMessageId(messageId);
                        message.setConversationId(conversationId);
                        message.setSenderId(event.getSenderId());
                        
                        // Handle oneof content field (messageText or fileId)
                        if (event.hasMessageText()) {
                            message.setMessageText(event.getMessageText());
                        } else if (event.hasFileId()) {
                            // File messages will be linked when FileController creates full FileMetadata
                            // For now, we skip setting fileId since Message entity uses FileMetadata, not String fileId
                            // The file message will be completed when FileController sends MessageEvent with fileId
                        }
                        
                        message.setSequenceNumber(event.getSequenceNumber());
                        message.setTimestamp(timestamp);
                        
                        // T068: Initialize state history based on conversation type (fan-out for groups)
                        // For PRIVATE (1:1): Single SENT state
                        // For GROUP: SENT + per-recipient DELIVERED states (fan-out pattern)
                        List<MessageStateTransition> stateHistory = new ArrayList<>();
                        stateHistory.add(MessageStateTransition.create(MessageStatus.SENT, null));
                        
                        // Educational Note: Fan-out pattern for group messages
                        // Each recipient gets their own DELIVERED state tracked separately.
                        // This enables per-user acknowledgments and read receipts in group chats.
                        // Why? In a group with N members, we need to track N delivery confirmations.
                        // Example: Group with 3 users → 1 SENT + 3 DELIVERED states (one per recipient)
                        if (event.getRecipientIdsCount() > 1) {
                            // GROUP conversation - create per-recipient DELIVERED states (fan-out)
                            for (String recipientId : event.getRecipientIdsList()) {
                                // Don't create DELIVERED for sender (they already know they sent it)
                                if (!recipientId.equals(event.getSenderId())) {
                                    stateHistory.add(MessageStateTransition.create(MessageStatus.DELIVERED, recipientId));
                                }
                            }
                            
                            logger.debug("Fan-out delivery created - message_id: {}, recipients: {}, states: {}",
                                    messageId, event.getRecipientIdsCount(), stateHistory.size());
                        }
                        
                        message.setStateHistory(stateHistory);
                        
                        // Persist to MongoDB
                        messageRepository.save(message);
                        
                        // Incrementar contador de mensagens persistidas
                        messagesPersistedCounter.increment();
                        
                        // T037: Log message persistence per NFR-017
                        logger.info("Message persisted to MongoDB - message_id: {}, conversation_id: {}, sequence: {}",
                                messageId, conversationId, event.getSequenceNumber());
                        
                        // Update Conversation.lastMessage denormalized field (T046)
                        String messagePreview = event.hasMessageText() 
                            ? event.getMessageText() 
                            : "[File]";
                        conversationService.updateLastMessage(conversationId, messagePreview, timestamp);
                    });
                    
                    // T091: Notify StreamingService for real-time delivery to online users
                    Message message = messageRepository.findByMessageId(messageId)
                            .orElseThrow(() -> new IllegalStateException("Message not found after save: " + messageId));
                    com.chat.grpc.v1.MessageEvent grpcMessageEvent = buildMessageEvent(message);
                    
                    // Check if any participants are online and push via stream
                    boolean deliveredViaStream = streamingService.notifyUserMessage(event.getSenderId(), grpcMessageEvent);
                    
                    if (deliveredViaStream) {
                        logger.debug("Message delivered to online user via stream - message_id: {}", messageId);
                    }
                } else {
                    logger.info("Message already exists (idempotency check) - message_id: {}, skipping persistence but continuing routing", messageId);
                }
            
                
                // Route to external platforms (WhatsApp, Instagram) for each recipient
                if (event.getRecipientIdsCount() > 0) {
                    for (String recipientId : event.getRecipientIdsList()) {
                        platformRoutingService.routeMessageToPlatforms(event, recipientId);
                        logger.debug("Message routed to platforms - message_id: {}, recipient_id: {}", messageId, recipientId);
                    }
                } else {
                    logger.warn("No recipientIds in event - skipping platform routing for message_id: {}", messageId);
                }
                
                // Commit Kafka offset after successful processing
                acknowledgment.acknowledge();
                
            } catch (IllegalArgumentException e) {
                // Business validation error - log and skip message (commit offset)
                logger.error("Invalid message event - validation failed: {}", e.getMessage(), e);
                acknowledgment.acknowledge();
                
            } catch (Exception e) {
                // Transient error (DB connection, etc) - DO NOT commit offset
                // Kafka will retry this message after backoff period
                logger.error("Failed to process message event - will retry: {}", e.getMessage(), e);
                // Don't acknowledge - Kafka will redeliver
            }
        });
    }
    
    /**
     * Build gRPC MessageEvent protobuf for streaming to online users.
     * 
     * @param message Persisted Message entity
     * @return gRPC MessageEvent protobuf for streaming
     */
    private com.chat.grpc.v1.MessageEvent buildMessageEvent(Message message) {
        // Build NewMessageEvent
        NewMessageEvent newMessageEvent = NewMessageEvent.newBuilder()
                .setMessageId(message.getMessageId())
                .setConversationId(message.getConversationId())
                .setSender(UserInfo.newBuilder()
                        .setUserId(message.getSenderId())
                        .setUsername("User-" + message.getSenderId().substring(0, 8))
                        .build())
                .setMessageText(message.getMessageText())
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(message.getTimestamp().getEpochSecond())
                        .setNanos(message.getTimestamp().getNano())
                        .build())
                .setSequenceNumber(message.getSequenceNumber())
                .build();
        
        // Wrap in gRPC MessageEvent (oneof event)
        return com.chat.grpc.v1.MessageEvent.newBuilder()
                .setNewMessage(newMessageEvent)
                .build();
    }
}
