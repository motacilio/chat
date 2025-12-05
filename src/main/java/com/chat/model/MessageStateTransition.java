package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * MessageStateTransition (data-model.md Entity 4 - Embedded)
 * 
 * Responsibility: Represents a single state transition event for a message.
 * Does NOT: Handle state validation logic (see MessageService for state machine rules).
 * 
 * Distributed Systems Concept: Event sourcing lite - state history enables audit trail and
 * eventual consistency debugging. Each transition records who, when, and what state change occurred.
 * 
 * Note: This is an embedded entity, stored within Message.stateHistory array in MongoDB.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageStateTransition {
    
    /**
     * State value: SENT, DELIVERED, or READ
     */
    @Field("state")
    private MessageStatus state;
    
    /**
     * When state transition occurred
     */
    @Field("timestamp")
    private Instant timestamp;
    
    /**
     * Specific recipient (for group messages tracking per-recipient state).
     * Null for SENT state (applies to all recipients).
     * Set to user_id for DELIVERED/READ states.
     */
    @Field("recipient_id")
    private String recipientId;
    
    /**
     * Factory method to create state transition with current timestamp.
     * 
     * @param state       Message status
     * @param recipientId Recipient user_id (null for SENT)
     * @return MessageStateTransition instance
     */
    public static MessageStateTransition create(MessageStatus state, String recipientId) {
        return new MessageStateTransition(state, Instant.now(), recipientId);
    }
}
