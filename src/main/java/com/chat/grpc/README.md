# gRPC Service Layer

This package contains gRPC service implementations that expose the Chat API to external clients.

---

## Package Responsibility

**Purpose**: Protocol translation layer between gRPC wire format and domain models.

**Does**:
- Accept gRPC requests (SendMessage, CreateConversation, etc.)
- Validate request format (UUID validation, field presence)
- Translate Protobuf messages to domain entities
- Publish events to Kafka (async processing)
- Return immediate responses to clients

**Does NOT**:
- Persist to MongoDB directly (delegated to Kafka workers)
- Implement business logic (delegated to service layer)
- Handle authentication (delegated to SecurityConfig)

---

## Architecture Pattern: CQRS (Command Query Responsibility Segregation)

### Command Pattern (Write Operations)

```java
// SendMessage - COMMAND
@Override
public void sendMessage(SendMessageRequest request, StreamObserver<SendMessageResponse> responseObserver) {
    // 1. Validate request
    messageService.validateMessage(...);
    
    // 2. Generate sequence number
    int seqNum = messageService.generateNextSequenceNumber(conversationId);
    
    // 3. Publish to Kafka (async)
    kafkaTemplate.send("message-events", messageEvent);
    
    // 4. Return immediately (fire-and-forget)
    responseObserver.onNext(SendMessageResponse.newBuilder()
        .setMessageId(messageId)
        .setSequenceNumber(seqNum)
        .build());
}
```

**Key Insight**: Client gets immediate response while actual persistence happens asynchronously in MessageDeliveryWorker. This enables horizontal scaling without coordination.

### Query Pattern (Read Operations)

```java
// GetMessageStatus - QUERY
@Override
public void getMessageStatus(GetMessageStatusRequest request, StreamObserver<GetMessageStatusResponse> responseObserver) {
    // Direct MongoDB query (no Kafka)
    MessageStatus status = messageService.getCurrentStatus(messageId);
    
    responseObserver.onNext(GetMessageStatusResponse.newBuilder()
        .setStatus(status)
        .build());
}
```

**Key Insight**: Read operations bypass Kafka and query MongoDB directly for low-latency responses.

---

## Service Implementations

### 1. ChatServiceImpl

**Endpoints**:
- `SendMessage` - Submit text message (User Story 1)
- `GetMessageStatus` - Query message state (SENT/DELIVERED/READ)
- `MarkAsRead` - Update read status (User Story 2)
- `StreamMessages` - Real-time message streaming (User Story 3)

**Pattern**: Request → Validate → Kafka Publish → Response

**Example Flow**:
```
Client → SendMessage(text="Hello")
  ↓
ChatServiceImpl → MessageService.validateMessage()
  ↓
ChatServiceImpl → Kafka.publish(MessageEvent)
  ↓
ChatServiceImpl → Response(message_id, sequence_number)
  ↓
[ASYNC] MessageDeliveryWorker → MongoDB.save(Message)
```

### 2. ConversationServiceImpl

**Endpoints**:
- `CreateConversation` - Create 1:1 or group conversation
- `ListConversations` - Paginated conversation list
- `AddParticipant` - Add member to group (User Story 5)
- `RemoveParticipant` - Remove member from group

**Pattern**: Direct service calls (no Kafka for conversation CRUD)

**Why No Kafka?**: Conversations are low-volume operations (create once, query many). Direct MongoDB writes are acceptable.

### 3. FileServiceImpl

**Endpoints**:
- `InitiateUpload` - Generate pre-signed upload URL
- `CompleteUpload` - Finalize upload, create message
- `GetDownloadUrl` - Generate pre-signed download URL

**Pattern**: Pre-signed URLs for direct client ↔ MinIO communication

**Example Flow**:
```
Client → InitiateUpload(filename, size)
  ↓
FileServiceImpl → MinIO.getPresignedUrl(PUT)
  ↓
FileServiceImpl → Response(file_id, upload_url)
  ↓
Client → PUT {upload_url} [Direct to MinIO, bypasses API]
  ↓
Client → CompleteUpload(file_id, checksum)
  ↓
FileServiceImpl → MessageService.createFileMessage()
```

---

## Error Handling

### GlobalExceptionHandler

Centralized exception mapping for consistent error responses:

```java
@GrpcAdvice
public class GlobalExceptionHandler {
    @GrpcExceptionHandler(IllegalArgumentException.class)
    public Status handleValidationError(IllegalArgumentException e) {
        return Status.INVALID_ARGUMENT
            .withDescription(e.getMessage());
    }
    
    @GrpcExceptionHandler(RateLimitExceededException.class)
    public Status handleRateLimitError(RateLimitExceededException e) {
        return Status.RESOURCE_EXHAUSTED
            .withDescription("Rate limit exceeded");
    }
}
```

**gRPC Status Codes Used**:
- `OK` (0) - Success
- `INVALID_ARGUMENT` (3) - Validation failure
- `NOT_FOUND` (5) - Resource not found
- `ALREADY_EXISTS` (6) - Duplicate message_id (idempotency)
- `RESOURCE_EXHAUSTED` (8) - Rate limit exceeded
- `INTERNAL` (13) - Unexpected error

---

## Distributed Systems Concepts Demonstrated

### 1. Idempotency (FR-006)

```java
// Client provides message_id (UUID) for idempotent retry
if (messageRepository.existsByMessageId(messageId)) {
    logger.info("Duplicate message_id: {} - returning success", messageId);
    // Return success without re-publishing (idempotent behavior)
    return;
}
```

**Why?**: Network failures may cause client retries. Idempotency ensures duplicate requests are safe.

### 2. At-Least-Once Delivery (FR-026)

```java
// Kafka publish + manual offset commit
kafkaTemplate.send("message-events", conversationId, event);
// Consumer commits offset ONLY after MongoDB save
```

**Trade-off**: Possible duplicate processing (handled by idempotency check).

### 3. Backpressure

```java
// Rate limiting prevents service overwhelm
@RateLimiter(name = "sendMessage")
public void sendMessage(...) {
    // Max 100 messages/minute per user
}
```

**Why?**: Prevents single user from degrading service for others.

### 4. Circuit Breaker

```java
// MinIO circuit breaker protects against cascading failures
@CircuitBreaker(name = "minio", fallbackMethod = "uploadFallback")
public FileMetadata initiateUpload(...) {
    return minioClient.getPresignedUrl(...);
}
```

**Why?**: If MinIO is down, open circuit prevents overwhelming it with retries.

---

## Testing

### Unit Tests

```java
@Test
void testSendMessage_ValidRequest_ReturnsSuccess() {
    // Arrange
    SendMessageRequest request = SendMessageRequest.newBuilder()
        .setMessageId(UUID.randomUUID().toString())
        .setConversationId(UUID.randomUUID().toString())
        .setSenderId("user123")
        .setRecipientId("user456")
        .setMessageText("Hello")
        .build();
    
    // Act
    chatService.sendMessage(request, responseObserver);
    
    // Assert
    verify(kafkaTemplate).send(eq("message-events"), any(MessageEvent.class));
    verify(responseObserver).onNext(any(SendMessageResponse.class));
}
```

### Integration Tests

```java
@SpringBootTest
@Testcontainers
class ChatServiceIntegrationTest {
    @Container
    static KafkaContainer kafka = new KafkaContainer(...);
    
    @Test
    void testSendMessage_EndToEnd() {
        // Send via gRPC → Verify Kafka event → Verify MongoDB persistence
    }
}
```

---

## Best Practices

1. **Fail Fast**: Validate requests early, before publishing to Kafka
2. **Async by Default**: Use Kafka for write operations (horizontal scaling)
3. **Sync for Reads**: Direct MongoDB queries for low-latency reads
4. **Log Everything**: Structured logging for distributed tracing
5. **Idempotency**: Always support safe retries
6. **Backpressure**: Rate limit to prevent overload

---

## References

- [gRPC API Contracts](../../../../../DOC_REVISADA/02-API-GRPC-E-CONTRATOS.md)
- [Kafka Event Processing](../../../../../DOC_REVISADA/04-KAFKA-E-PROCESSAMENTO-ASSINCRONO.md)
- [System Architecture](../../../../../docs/architecture/system-overview.md)
