package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Message Entity (data-model.md Entity 3)
 * 
 * Responsibility: Represents a single message with text OR file content and state lifecycle.
 * Does NOT: Handle delivery logic (see MessageDeliveryWorker), validate business rules (see MessageService).
 * 
 * Distributed Systems Concept: Event entity with state machine (SENT → DELIVERED → READ),
 * demonstrates eventual consistency. Message state transitions are tracked asynchronously,
 * enabling reliable delivery tracking across distributed components.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "conversation_timestamp", def = "{'conversationId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "conversation_sequence", def = "{'conversationId': 1, 'sequenceNumber': 1}"),
    @CompoundIndex(name = "sender_timestamp", def = "{'senderId': 1, 'timestamp': -1}")
})
public class Message {
    
    /**
     * MongoDB internal ID
     */
    @Id
    private String id;
    
    /**
     * UUID - unique message identifier (client-generated for idempotency per FR-006)
     */
    @Indexed(unique = true)
    private String messageId;
    
    /**
     * Foreign key to Conversation
     */
    @Indexed
    private String conversationId;
    
    /**
     * Foreign key to User (sender)
     */
    @Indexed
    private String senderId;
    
    /**
     * Text content (null if file message)
     * Max size: 100 KB (validated in MessageService per edge case spec)
     * 
     * Constraint (User Story 4): Mutually exclusive with fileMetadata
     * A message contains EITHER text OR file, not both (enforced in MessageService)
     */
    private String messageText;
    
    /**
     * File metadata reference (null if text message)
     * Embedded document containing file details from MinIO storage
     * 
     * Constraint (User Story 4): Mutually exclusive with messageText
     * When present, this is a file message following same state lifecycle (SENT → DELIVERED → READ)
     * 
     * Pattern: Metadata embedded for denormalization (avoids join queries)
     * File content stored separately in MinIO for blob efficiency
     */
    private FileMetadata fileMetadata;
    
    /**
     * Send timestamp
     */
    @Indexed
    private Instant timestamp;
    
    /**
     * Per-conversation ordering number (auto-incremented).
     * Ensures FR-007: preserve message ordering within conversation.
     * Generated atomically using MongoDB findAndModify with increment.
     */
    private Long sequenceNumber;
    
    /**
     * State transition history (embedded array).
     * Tracks lifecycle: SENT → DELIVERED → READ with timestamps.
     */
    private List<MessageStateTransition> stateHistory;
    
    /**
     * Get current message status (derived from last entry in stateHistory).
     * 
     * @return Current MessageStatus (SENT if no history)
     */
    public MessageStatus getCurrentStatus() {
        if (stateHistory == null || stateHistory.isEmpty()) {
            return MessageStatus.SENT;
        }
        return stateHistory.get(stateHistory.size() - 1).getState();
    }
    
    /**
     * Add state transition to history.
     * 
     * @param state       New state
     * @param recipientId Recipient user_id (null for SENT)
     */
    public void addStateTransition(MessageStatus state, String recipientId) {
        if (stateHistory == null) {
            stateHistory = new ArrayList<>();
        }
        stateHistory.add(MessageStateTransition.create(state, recipientId));
    }
    
    /**
     * Factory method to create new text message with SENT state.
     * 
     * @param messageId      UUID identifier (client-generated)
     * @param conversationId Conversation UUID
     * @param senderId       Sender user_id
     * @param messageText    Text content
     * @param sequenceNumber Per-conversation sequence number
     * @return Message instance with initial SENT state
     */
    public static Message createTextMessage(
            String messageId,
            String conversationId,
            String senderId,
            String messageText,
            Long sequenceNumber) {
        Instant now = Instant.now();
        ArrayList<MessageStateTransition> stateHistory = new ArrayList<>();
        stateHistory.add(MessageStateTransition.create(MessageStatus.SENT, null));
        
        return Message.builder()
                .messageId(messageId)
                .conversationId(conversationId)
                .senderId(senderId)
                .messageText(messageText)
                .fileMetadata(null) // Explicitly null for text messages
                .timestamp(now)
                .sequenceNumber(sequenceNumber)
                .stateHistory(stateHistory)
                .build();
    }
    
    /**
     * Factory method to create new file message with SENT state.
     * (User Story 4 - P2: Upload and Download Files)
     * 
     * @param messageId      UUID identifier (client-generated)
     * @param conversationId Conversation UUID
     * @param senderId       Sender user_id
     * @param fileMetadata   File metadata from completed upload
     * @param sequenceNumber Per-conversation sequence number
     * @return Message instance with initial SENT state and file attachment
     */
    public static Message createFileMessage(
            String messageId,
            String conversationId,
            String senderId,
            FileMetadata fileMetadata,
            Long sequenceNumber) {
        Instant now = Instant.now();
        ArrayList<MessageStateTransition> stateHistory = new ArrayList<>();
        stateHistory.add(MessageStateTransition.create(MessageStatus.SENT, null));
        
        return Message.builder()
                .messageId(messageId)
                .conversationId(conversationId)
                .senderId(senderId)
                .messageText(null) // Explicitly null for file messages
                .fileMetadata(fileMetadata)
                .timestamp(now)
                .sequenceNumber(sequenceNumber)
                .stateHistory(stateHistory)
                .build();
    }
    
    /**
     * Check if this is a text message.
     * 
     * @return true if message contains text (not file)
     */
    public boolean isTextMessage() {
        return messageText != null && fileMetadata == null;
    }
    
    /**
     * Check if this is a file message.
     * 
     * @return true if message contains file (not text)
     */
    public boolean isFileMessage() {
        return fileMetadata != null && messageText == null;
    }
}
