package com.chat.service;

import com.chat.model.Conversation;
import com.chat.model.Message;
import com.chat.model.MessageStatus;
import com.chat.repository.ConversationRepository;
import com.chat.repository.MessageRepository;
import com.chat.util.InputSanitizer;
import com.chat.util.UuidValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Message Service (T030)
 * 
 * Responsibility: Business logic for message operations - validation, sequence generation, persistence orchestration.
 * Does NOT: Handle gRPC mapping (see ChatServiceImpl), Kafka publishing (see MessageDeliveryWorker).
 * 
 * Distributed Systems Concept: This is the domain service layer that enforces business rules before
 * messages enter the asynchronous processing pipeline (Kafka). Separating validation from I/O enables
 * fast request/response while deferring expensive operations (MongoDB write) to background workers.
 */
@Service
public class MessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    private static final int MAX_MESSAGE_SIZE_BYTES = 100 * 1024; // 100 KB per edge case spec
    
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MongoTemplate mongoTemplate;
    
    // Métricas Prometheus
    private final Counter messagesSentCounter;
    private final Counter messagesValidatedCounter;
    private final Counter idempotentRequestsCounter;
    private final Timer messageValidationTimer;
    
    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            MongoTemplate mongoTemplate,
            MeterRegistry meterRegistry) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.mongoTemplate = mongoTemplate;
        
        // Inicializar métricas
        this.messagesSentCounter = Counter.builder("messages_sent_total")
                .description("Total de mensagens enviadas")
                .tag("type", "text")
                .register(meterRegistry);
        
        this.messagesValidatedCounter = Counter.builder("messages_validated_total")
                .description("Total de mensagens validadas com sucesso")
                .register(meterRegistry);
        
        this.idempotentRequestsCounter = Counter.builder("idempotent_requests_total")
                .description("Total de requisições idempotentes detectadas")
                .register(meterRegistry);
        
        this.messageValidationTimer = Timer.builder("message_validation_latency_seconds")
                .description("Latência da validação de mensagens")
                .publishPercentiles(0.50, 0.95, 0.99)
                .register(meterRegistry);
    }
    
    /**
     * Generate next sequence number for message in conversation.
     * 
     * Uses MongoDB findAndModify with increment to ensure atomic sequence generation
     * across distributed instances (prevents duplicate sequence numbers per FR-007).
     * 
     * Distributed Systems Pattern: Atomic counter using database increment operation.
     * Alternative would be distributed sequence generator (Snowflake) but MongoDB's
     * findAndModify provides sufficient atomicity for per-conversation sequences.
     * 
     * @param conversationId Conversation UUID
     * @return Next sequence number (1-indexed)
     */
    public Long generateSequenceNumber(String conversationId) {
        Query query = Query.query(Criteria.where("conversationId").is(conversationId));
        Update update = new Update().inc("messageSequence", 1);
        
        // Use findAndModify to atomically increment and return new value
        FindAndModifyOptions options = FindAndModifyOptions.options()
                .returnNew(true)
                .upsert(true);
        
        // Store sequence counter in a separate collection for atomicity
        SequenceCounter counter = mongoTemplate.findAndModify(
                query, 
                update, 
                options, 
                SequenceCounter.class,
                "message_sequences"
        );
        
        return counter != null ? counter.getMessageSequence() : 1L;
    }
    
    /**
     * Validate message before publishing to Kafka.
     * 
     * Validates:
     * - message_id is valid UUID format (FR-006 idempotency)
     * - conversation_id exists in MongoDB or auto-creates with sender_id and recipient_id
     * - sender_id is participant in conversation (authorization per FR-013)
     * - message_text size is within limits (edge case: reject >100 KB per T035)
     * 
     * @param messageId      Client-generated UUID
     * @param conversationId Conversation UUID
     * @param senderId       Sender user_id
     * @param recipientId    Recipient user_id (required for auto-created conversations per FR-004a)
     * @param messageText    Text content
     * @throws IllegalArgumentException if validation fails
     */
    public void validateMessage(String messageId, String conversationId, String senderId, String recipientId, String messageText) {
        // Medir latência de validação
        messageValidationTimer.record(() -> {
            // Validate UUIDs (T035)
            UuidValidator.validateOrThrow(messageId, "message_id");
            UuidValidator.validateOrThrow(conversationId, "conversation_id");
            UuidValidator.validateOrThrow(senderId, "sender_id");
            UuidValidator.validateOrThrow(recipientId, "recipient_id");
            
            // Validate message size (T035 - edge case spec)
            if (messageText == null || messageText.trim().isEmpty()) {
                throw new IllegalArgumentException("message_text cannot be empty");
            }
            
            // Security: Sanitize input to prevent XSS and NoSQL injection (T105)
            String sanitizedText = InputSanitizer.sanitizeText(messageText);
            
            int sizeBytes = sanitizedText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (sizeBytes > MAX_MESSAGE_SIZE_BYTES) {
                throw new IllegalArgumentException(
                        String.format("message_text exceeds maximum size of %d bytes (actual: %d bytes)", 
                                MAX_MESSAGE_SIZE_BYTES, sizeBytes)
                );
            }
            
            // Check for duplicate message_id (idempotency per FR-006)
            if (messageRepository.existsByMessageId(messageId)) {
                logger.info("Duplicate message_id detected: {} - idempotent request, returning success", messageId);
                idempotentRequestsCounter.increment(); // Métrica de requisições idempotentes
                // Not throwing exception - idempotent behavior returns success for duplicate
                return;
            }
            
            // Get or create conversation (auto-create for first message per FR-004a)
            Conversation conversation = conversationRepository.findByConversationId(conversationId)
                    .orElseGet(() -> {
                        logger.info("Conversation not found: {} - creating automatically with sender: {} and recipient: {}", 
                                conversationId, senderId, recipientId);
                        // Auto-create conversation with both sender_id and recipient_id as participants (FR-004a)
                        Conversation newConv = Conversation.builder()
                                .conversationId(conversationId)
                                .type(com.chat.model.ConversationType.PRIVATE)
                                .participants(java.util.Arrays.asList(senderId, recipientId))
                                .createdAt(java.time.Instant.now())
                                .lastMessageAt(java.time.Instant.now())
                                .build();
                        conversationRepository.save(newConv);
                        logger.info("Auto-created conversation: {} with participants: [{}, {}]", 
                                conversationId, senderId, recipientId);
                        return newConv;
                    });
            
            // Validate sender is participant (authorization per FR-013)
            if (!conversation.isParticipant(senderId)) {
                throw new SecurityException(
                        "User " + senderId + " is not a participant in conversation " + conversationId);
            }
            
            // Incrementar contadores de métricas
            messagesSentCounter.increment();
            messagesValidatedCounter.increment();
            
            // T036: Log message submission at INFO level per NFR-017
            logger.info("Message validated - message_id: {}, conversation_id: {}, sender_id: {}, size_bytes: {}", 
                    messageId, conversationId, senderId, sizeBytes);
        });
    }
    
    /**
     * Add state transition to message history (T038).
     * 
     * Updates Message.stateHistory array with new transition event.
     * Called by MessageStateUpdateWorker after consuming state-update-events from Kafka.
     * 
     * @param messageId   Message UUID
     * @param state       New status (DELIVERED or READ)
     * @param recipientId Recipient user_id (for group message tracking)
     */
    @Transactional
    public void addStateTransition(String messageId, MessageStatus state, String recipientId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        
        message.addStateTransition(state, recipientId);
        messageRepository.save(message);
        
        logger.debug("State transition added - message_id: {}, new_state: {}, recipient_id: {}", 
                messageId, state, recipientId);
    }
    
    /**
     * Get current message status (T038 helper).
     * 
     * @param messageId Message UUID
     * @return Current MessageStatus from state_history
     */
    public MessageStatus getCurrentStatus(String messageId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        
        return message.getCurrentStatus();
    }
    
    /**
     * Mark message as read by user (T042).
     * 
     * Validates user is participant in conversation before allowing status update.
     * 
     * @param messageId Message UUID
     * @param userId    User marking message as read
     * @return Conversation ID for the message
     * @throws IllegalArgumentException if message not found
     * @throws SecurityException if user is not participant
     */
    public String markAsRead(String messageId, String userId) {
        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        
        // Validate user is participant (T042 authorization check per FR-013)
        Conversation conversation = conversationRepository.findByConversationId(message.getConversationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Conversation not found for message: " + messageId));
        
        if (!conversation.isParticipant(userId)) {
            throw new SecurityException(
                    "User " + userId + " is not a participant in conversation " + conversation.getConversationId());
        }
        
        logger.info("Mark as read - message_id: {}, user_id: {}", messageId, userId);
        
        return conversation.getConversationId();
    }
    
    /**
     * Create a file message after upload completes.
     * (User Story 4 - P2: File messages follow same lifecycle as text messages)
     * 
     * This method creates a Message entity with fileMetadata instead of messageText,
     * following the XOR constraint (a message contains EITHER text OR file, not both).
     * 
     * Flow:
     * 1. Validate conversation exists and sender is participant
     * 2. Generate message UUID and sequence number
     * 3. Create Message with fileMetadata embedded
     * 4. Persist to MongoDB
     * 5. Return Message for Kafka publishing (done by caller)
     * 
     * Clean Code Principles:
     * - Single Responsibility: Only creates Message entity, doesn't publish to Kafka
     * - Open/Closed: Reuses existing validation and sequence generation methods
     * - Dependency Inversion: Caller decides how to publish (Kafka/webhook/etc)
     * 
     * @param conversationId Conversation UUID
     * @param senderId       Sender user ID
     * @param recipientIds   List of recipient user IDs
     * @param fileMetadata   File metadata from MinIO storage
     * @return Created Message entity (ready for Kafka publishing)
     * @throws IllegalArgumentException if validation fails
     * @throws SecurityException if sender is not participant
     */
    public Message createFileMessage(
            String conversationId, 
            String senderId, 
            java.util.List<String> recipientIds,
            com.chat.model.FileMetadata fileMetadata) {
        
        logger.info("Creating file message - conversation: {}, sender: {}, file: {}", 
                   conversationId, senderId, fileMetadata.getFileId());
        
        // Validate conversation exists
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));
        
        // Validate sender is participant (FR-013 authorization)
        if (!conversation.isParticipant(senderId)) {
            logger.warn("Unauthorized file message attempt - sender: {} not in conversation: {}", 
                       senderId, conversationId);
            throw new SecurityException(
                    "Sender " + senderId + " is not a participant in conversation " + conversationId);
        }
        
        // Generate message UUID (server-generated for file messages)
        String messageId = java.util.UUID.randomUUID().toString();
        
        // Generate sequence number (atomic increment per FR-007)
        Long sequenceNumber = generateSequenceNumber(conversationId);
        
        // Create Message entity with fileMetadata (XOR constraint: no messageText)
        Message message = Message.builder()
                .messageId(messageId)
                .conversationId(conversationId)
                .senderId(senderId)
                .messageText(null)  // Explicitly null for file messages (XOR with fileMetadata)
                .fileMetadata(fileMetadata)
                .timestamp(java.time.Instant.now())
                .sequenceNumber(sequenceNumber)
                .stateHistory(new java.util.ArrayList<>())
                .build();
        
        // Add initial state transition: SENT
        message.getStateHistory().add(
                com.chat.model.MessageStateTransition.create(MessageStatus.SENT, null)
        );
        
        // Persist to MongoDB
        Message savedMessage = messageRepository.save(message);
        
        logger.info("File message created - messageId: {}, fileId: {}, sequence: {}", 
                   messageId, fileMetadata.getFileId(), sequenceNumber);
        
        return savedMessage;
    }
    
    /**
     * Internal class for sequence counter storage.
     * Stored in separate collection for atomic increment operations.
     */
    @org.springframework.data.mongodb.core.mapping.Document(collection = "message_sequences")
    private static class SequenceCounter {
        @org.springframework.data.annotation.Id
        private String id;
        private String conversationId;
        private Long messageSequence = 0L;
        
        public Long getMessageSequence() {
            return messageSequence;
        }
        
        public void setMessageSequence(Long messageSequence) {
            this.messageSequence = messageSequence;
        }
    }
}
