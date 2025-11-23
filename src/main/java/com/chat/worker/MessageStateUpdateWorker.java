package com.chat.worker;

import com.chat.dto.StateUpdateEventDto;
import com.chat.grpc.v1.MessageEvent;
import com.chat.grpc.v1.StatusUpdateEvent;
import com.chat.model.Message;
import com.chat.model.MessageStatus;
import com.chat.model.MessageStateTransition;
import com.chat.repository.MessageRepository;
import com.chat.service.StreamingService;
import com.google.protobuf.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MessageStateUpdateWorker (T039)
 * 
 * Responsibility: Kafka consumer that updates message state transitions in MongoDB.
 * Consumes state-update-events topic, appends StateTransition to Message.stateHistory array.
 * 
 * Distributed Systems Concept: Async state management decouples user actions from persistence.
 * - User calls MarkMessageAsRead RPC (fast response)
 * - ChatServiceImpl publishes state update event to Kafka
 * - MessageStateUpdateWorker processes event asynchronously
 * - Enables horizontal scaling: multiple workers consume from different partitions
 * 
 * State Machine: SENT → DELIVERED → READ
 * - SENT: Message accepted by server (initial state in MessageDeliveryWorker)
 * - DELIVERED: Message delivered to recipient's device (future: push notification confirmation)
 * - READ: Recipient opened/read the message (via MarkMessageAsRead RPC)
 * 
 * Idempotency Strategy: Check if state transition already exists in stateHistory.
 * This handles duplicate Kafka messages (at-least-once delivery semantics).
 * 
 * Error Handling:
 * - Business errors (message not found, invalid state transition): Log and commit offset
 * - Transient errors (DB connection): Don't commit offset, Kafka will retry
 */
@Component
public class MessageStateUpdateWorker {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageStateUpdateWorker.class);
    
    private final MessageRepository messageRepository;
    private final StreamingService streamingService;
    
    public MessageStateUpdateWorker(
            MessageRepository messageRepository,
            StreamingService streamingService) {
        this.messageRepository = messageRepository;
        this.streamingService = streamingService;
    }
    
    /**
     * Kafka consumer for state-update-events topic (T039).
     * 
     * Concurrency: Multiple consumer instances read from different partitions (scaling).
     * Ordering: State updates for same message_id go to same partition (Kafka key).
     * Acknowledgment: Manual commit - only commit offset after successful MongoDB update.
     * 
     * Flow:
     * 1. Retrieve Message from MongoDB by message_id
     * 2. Check idempotency (state already in stateHistory?)
     * 3. Append StateTransition to stateHistory array
     * 4. Save updated Message
     * 5. Commit Kafka offset
     * 
     * @param event  StateUpdateEventDto from Kafka
     * @param acknowledgment Manual offset commit control
     */
    @KafkaListener(
            topics = "state-update-events",
            groupId = "message-state-update-workers",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleStateUpdateEvent(StateUpdateEventDto event, Acknowledgment acknowledgment) {
        try {
            String messageId = event.getMessageId();
            MessageStatus newStatus = event.getNewStatus();
            
            // Retrieve message from MongoDB
            Optional<Message> messageOpt = messageRepository.findByMessageId(messageId);
            
            if (messageOpt.isEmpty()) {
                // Message not found - this is a business error (data inconsistency)
                logger.error("Message not found for state update - message_id: {}, skipping", messageId);
                acknowledgment.acknowledge();
                return;
            }
            
            Message message = messageOpt.get();
            
            // Get current state history
            List<MessageStateTransition> stateHistory = message.getStateHistory();
            if (stateHistory == null) {
                stateHistory = new ArrayList<>();
            }
            
            // Idempotency check - if state already exists, skip
            boolean stateExists = stateHistory.stream()
                    .anyMatch(st -> st.getState() == newStatus);
            
            if (stateExists) {
                logger.info("State transition already exists (idempotency check) - message_id: {}, status: {}, skipping",
                        messageId, newStatus);
                acknowledgment.acknowledge();
                return;
            }
            
            // Get old status for logging (T043)
            MessageStatus oldStatus = stateHistory.isEmpty() ? null : 
                    stateHistory.get(stateHistory.size() - 1).getState();
            
            // Append new state transition
            MessageStateTransition transition = MessageStateTransition.create(newStatus, event.getUserId());
            
            stateHistory.add(transition);
            message.setStateHistory(stateHistory);
            
            // Persist updated message
            messageRepository.save(message);
            
            // T043: Log state transition per NFR-017
            logger.info("Message state updated - message_id: {}, conversation_id: {}, old_status: {}, new_status: {}, user_id: {}",
                    messageId, event.getConversationId(), oldStatus, newStatus, event.getUserId());
            
            // T092: Notify StreamingService to push status update to sender (if online)
            MessageEvent statusEvent = buildStatusUpdateMessageEvent(
                    messageId,
                    oldStatus,
                    newStatus,
                    transition.getTimestamp(),
                    event.getUserId()
            );
            
            // Get sender_id from message to notify them
            boolean deliveredViaStream = streamingService.notifyUserMessage(message.getSenderId(), statusEvent);
            
            if (deliveredViaStream) {
                logger.debug("Status update delivered to sender via stream - message_id: {}, status: {}",
                        messageId, newStatus);
            }
            
            // Commit Kafka offset after successful processing
            acknowledgment.acknowledge();
            
        } catch (IllegalArgumentException e) {
            // Business validation error - log and skip event (commit offset)
            logger.error("Invalid state update event - validation failed: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            // Transient error (DB connection, etc) - DO NOT commit offset
            // Kafka will retry this event after backoff period
            logger.error("Failed to process state update event - will retry: {}", e.getMessage(), e);
            // Don't acknowledge - Kafka will redeliver
        }
    }
    
    /**
     * Build MessageEvent with StatusUpdateEvent for streaming to sender.
     * 
     * @param messageId Message identifier
     * @param oldStatus Previous message status
     * @param newStatus New message status
     * @param timestamp State transition timestamp
     * @param recipientId User who triggered the transition
     * @return MessageEvent protobuf for gRPC streaming
     */
    private MessageEvent buildStatusUpdateMessageEvent(
            String messageId,
            MessageStatus oldStatus,
            MessageStatus newStatus,
            Instant timestamp,
            String recipientId) {
        
        // Map domain MessageStatus to protobuf MessageStatus
        com.chat.grpc.v1.MessageStatus protoOldStatus = oldStatus != null ? mapStatusToProto(oldStatus) : 
                com.chat.grpc.v1.MessageStatus.MESSAGE_STATUS_UNSPECIFIED;
        com.chat.grpc.v1.MessageStatus protoNewStatus = mapStatusToProto(newStatus);
        
        // Build StatusUpdateEvent
        StatusUpdateEvent statusEvent = StatusUpdateEvent.newBuilder()
                .setMessageId(messageId)
                .setOldStatus(protoOldStatus)
                .setNewStatus(protoNewStatus)
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(timestamp.getEpochSecond())
                        .setNanos(timestamp.getNano())
                        .build())
                .setRecipientId(recipientId)
                .build();
        
        // Wrap in MessageEvent (oneof event)
        return MessageEvent.newBuilder()
                .setStatusUpdate(statusEvent)
                .build();
    }
    
    /**
     * Map domain MessageStatus to protobuf MessageStatus.
     */
    private com.chat.grpc.v1.MessageStatus mapStatusToProto(MessageStatus status) {
        return switch (status) {
            case SENT -> com.chat.grpc.v1.MessageStatus.SENT;
            case DELIVERED -> com.chat.grpc.v1.MessageStatus.DELIVERED;
            case READ -> com.chat.grpc.v1.MessageStatus.READ;
        };
    }
}
