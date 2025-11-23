package com.chat.dto;

import com.chat.model.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * StateUpdateEventDto - Kafka message payload for state-update-events topic
 * 
 * Responsibility: Data Transfer Object for message state transition events.
 * Consumed by MessageStateUpdateWorker to update Message.stateHistory in MongoDB.
 * 
 * Distributed Systems Concept: Decouples state updates from gRPC endpoints.
 * - Client calls MarkMessageAsRead RPC
 * - ChatServiceImpl publishes StateUpdateEventDto to Kafka
 * - MessageStateUpdateWorker consumes event and persists state transition
 * - Enables async processing and horizontal scaling of state updates
 * 
 * Kafka Topic: state-update-events
 * Kafka Key: message_id (ensures all state updates for same message go to same partition)
 * 
 * Schema Evolution: Adding new fields is backward compatible (old consumers ignore new fields)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StateUpdateEventDto implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Message ID (UUID) - also used as Kafka key
     */
    private String messageId;
    
    /**
     * New status to transition to (DELIVERED, READ)
     */
    private MessageStatus newStatus;
    
    /**
     * User ID who triggered the state change (e.g., recipient marking as read)
     */
    private String userId;
    
    /**
     * Timestamp of the state transition (ISO-8601 format)
     */
    private String timestamp;
    
    /**
     * Optional: Conversation ID for logging/debugging
     */
    private String conversationId;
}
