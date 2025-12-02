# Kafka Consumer Workers

This package contains Kafka consumer workers that handle asynchronous message processing.

---

## Package Responsibility

**Purpose**: Asynchronous event processing layer that decouples API requests from persistence.

**Does**:
- Consume events from Kafka topics
- Persist messages to MongoDB
- Update conversation metadata (last_message_preview)
- Trigger external platform webhooks (Telegram, WhatsApp)
- Send real-time notifications via SSE

**Does NOT**:
- Accept client requests (handled by gRPC layer)
- Generate business entities (delegated to service layer)
- Manage Kafka infrastructure (topic creation, partitioning)

---

## Architecture Pattern: Event-Driven Processing

### Message Flow

```
┌─────────────┐     Kafka      ┌──────────────────┐     MongoDB     ┌──────────┐
│ gRPC API    │ ──publish────> │ MessageDelivery  │ ───persist────> │ MongoDB  │
│             │                 │ Worker           │                 │          │
└─────────────┘                 └──────────────────┘                 └──────────┘
                                         │
                                         │ notify
                                         ▼
                                 ┌──────────────────┐
                                 │ StreamingService │
                                 │ (in-memory SSE)  │
                                 └──────────────────┘
```

**Key Insight**: API layer returns immediately while workers handle expensive operations asynchronously. This enables:
- **Horizontal Scaling**: Add worker instances without API coordination
- **Fault Isolation**: Worker crashes don't impact API availability
- **Load Smoothing**: Burst traffic queued in Kafka, processed at steady rate

---

## Worker Implementations

### 1. MessageDeliveryWorker

**Topic**: `message-events`  
**Partitions**: 3 (by `conversation_id`)  
**Concurrency**: 3 threads per instance

**Responsibilities**:
1. Consume `MessageEvent` from Kafka
2. Persist `Message` entity to MongoDB
3. Update `Conversation.last_message_preview` (denormalized field)
4. Notify online users via `StreamingService`
5. Trigger external platform webhooks

**Code Example**:
```java
@KafkaListener(topics = "message-events", groupId = "message-delivery-group")
public void consumeMessageEvent(MessageEvent event, Acknowledgment ack) {
    try {
        // 1. Map Protobuf → Domain Entity
        Message message = mapToMessage(event);
        
        // 2. Persist to MongoDB
        messageRepository.save(message);
        
        // 3. Update conversation preview
        updateConversationPreview(event.getConversationId(), event.getMessageText());
        
        // 4. Notify online users (SSE)
        streamingService.notifyMessageReceived(message);
        
        // 5. Trigger platform webhooks
        platformRoutingService.routeMessage(message);
        
        // 6. CRITICAL: Manual offset commit (at-least-once delivery)
        ack.acknowledge();
        
    } catch (Exception e) {
        logger.error("Failed to process message event: {}", event.getMessageId(), e);
        // Do NOT acknowledge - message will be redelivered
    }
}
```

**Error Handling**:
- **Transient Errors** (network timeout): Retry by not acknowledging
- **Permanent Errors** (invalid data): Log error, acknowledge to skip
- **Circuit Breaker**: MongoDB/MinIO failures trigger circuit breaker

### 2. MessageStateUpdateWorker

**Topic**: `state-update-events`  
**Partitions**: 3 (by `message_id`)  
**Concurrency**: 3 threads per instance

**Responsibilities**:
1. Consume `StateUpdateEvent` from Kafka
2. Append state transition to `Message.stateHistory` array
3. Calculate current status (SENT → DELIVERED → READ)

**Code Example**:
```java
@KafkaListener(topics = "state-update-events", groupId = "state-update-group")
public void consumeStateUpdate(StateUpdateEvent event, Acknowledgment ack) {
    try {
        // Find message
        Message message = messageRepository.findByMessageId(event.getMessageId())
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        
        // Add state transition
        message.addStateTransition(
            MessageStatus.valueOf(event.getNewState()),
            event.getRecipientId()
        );
        
        // Persist
        messageRepository.save(message);
        
        // Acknowledge
        ack.acknowledge();
        
    } catch (Exception e) {
        logger.error("Failed to process state update: {}", event.getMessageId(), e);
    }
}
```

### 3. PlatformMessageWorker

**Topic**: `platform-message-events`  
**Partitions**: 3  
**Concurrency**: 3 threads per instance

**Responsibilities**:
1. Consume incoming messages from external platforms (Telegram, WhatsApp)
2. Map platform message to internal `MessageEvent`
3. Route to appropriate conversation
4. Persist via normal message flow

**Code Example**:
```java
@KafkaListener(topics = "platform-message-events", groupId = "platform-message-group")
public void consumePlatformMessage(PlatformMessageEvent event, Acknowledgment ack) {
    try {
        // 1. Map platform user to internal user_id
        String internalUserId = userMappingService.getInternalUserId(
            event.getPlatform(),
            event.getExternalUserId()
        );
        
        // 2. Find or create conversation
        String conversationId = conversationService.findOrCreateConversation(
            internalUserId,
            event.getRecipientId()
        );
        
        // 3. Create internal message event
        MessageEvent internalEvent = MessageEvent.newBuilder()
            .setMessageId(UUID.randomUUID().toString())
            .setConversationId(conversationId)
            .setSenderId(internalUserId)
            .setMessageText(event.getMessageText())
            .build();
        
        // 4. Publish to message-events topic
        kafkaTemplate.send("message-events", conversationId, internalEvent);
        
        // 5. Acknowledge
        ack.acknowledge();
        
    } catch (Exception e) {
        logger.error("Failed to process platform message", e);
    }
}
```

---

## Distributed Systems Concepts

### 1. At-Least-Once Delivery (FR-026)

**Configuration**:
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, MessageEvent> containerFactory() {
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    return factory;
}
```

**Pattern**:
```java
public void consume(MessageEvent event, Acknowledgment ack) {
    // Process message
    messageRepository.save(message);
    
    // CRITICAL: Acknowledge ONLY after successful persistence
    ack.acknowledge();
}
```

**Why?**: If worker crashes after processing but before ack, message will be redelivered. This ensures zero message loss.

**Trade-off**: Duplicate processing possible → mitigated by `message_id` unique index (idempotency).

### 2. Partition Ordering (FR-007)

**Kafka Partitioning**:
```java
// Producer partitions by conversation_id
kafkaTemplate.send("message-events", event.getConversationId(), event);
```

**Guarantee**: Messages for same conversation always go to same partition → consumed in order by single consumer thread.

**Example**:
```
Conversation A messages: [1, 2, 3] → Partition 0 → Consumer Thread 1 (ordered)
Conversation B messages: [1, 2, 3] → Partition 1 → Consumer Thread 2 (ordered)
Conversation C messages: [1, 2, 3] → Partition 2 → Consumer Thread 3 (ordered)
```

### 3. Backpressure Control

**Configuration**:
```java
configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
```

**Behavior**: Consumer fetches max 10 messages per poll. If processing is slow, Kafka won't overwhelm worker.

**Monitoring**: `kafka_consumer_lag` metric tracks unprocessed messages.

### 4. Consumer Lag Management

**Target**: Lag < 1,000 messages under normal load

**Scaling Strategy**:
```
Lag < 100:     1 instance (3 threads)  ✅ Optimal
Lag 100-1000:  2 instances (6 threads) ⚠️ Growing
Lag > 1000:    3+ instances            ❌ Action needed
```

**Auto-Scaling** (Kubernetes HPA):
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
spec:
  scaleTargetRef:
    name: message-worker
  minReplicas: 1
  maxReplicas: 10
  metrics:
    - type: External
      external:
        metric:
          name: kafka_consumer_lag
        target:
          type: AverageValue
          averageValue: "500"
```

---

## Error Handling Strategies

### Transient Errors (Retry)

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2.0),
    include = {MongoTimeoutException.class, SocketTimeoutException.class}
)
public void consume(MessageEvent event) {
    // Will retry 3 times: 1s, 2s, 4s
}
```

### Permanent Errors (Dead Letter Queue)

```java
@DltHandler
public void handleDlq(MessageEvent event, @Header(KafkaHeaders.EXCEPTION_MESSAGE) String error) {
    logger.error("Message sent to DLQ: {}, Error: {}", event.getMessageId(), error);
    // Alert ops team, investigate data corruption
}
```

### Circuit Breaker Integration

```java
@CircuitBreaker(name = "mongodb", fallbackMethod = "saveFallback")
public void persistMessage(Message message) {
    messageRepository.save(message);
}

public void saveFallback(Message message, Exception e) {
    logger.error("MongoDB circuit breaker OPEN - message not persisted: {}", message.getMessageId());
    // Do NOT acknowledge - will retry when circuit closes
}
```

---

## Performance Optimization

### Batch Processing

```java
@KafkaListener(topics = "message-events", batch = "true")
public void consumeBatch(List<MessageEvent> events, Acknowledgment ack) {
    // Map to entities
    List<Message> messages = events.stream()
        .map(this::mapToMessage)
        .collect(Collectors.toList());
    
    // Batch insert (1 MongoDB call instead of N)
    messageRepository.saveAll(messages);
    
    // Single acknowledge for entire batch
    ack.acknowledge();
}
```

**Throughput**: 10 msg/s → 300 msg/s (30x improvement)

### Async Webhook Calls

```java
@Async
public CompletableFuture<Void> triggerWebhook(Message message) {
    webhookService.sendToTelegram(message);
    return CompletableFuture.completedFuture(null);
}
```

**Why?**: Don't block consumer thread waiting for external API calls.

---

## Monitoring & Alerting

### Key Metrics

```promql
# Consumer lag (alert if >1000)
kafka_consumer_lag{group="message-delivery-group",topic="message-events"}

# Processing rate (messages/second)
rate(kafka_consumer_records_consumed_total[1m])

# Error rate
rate(kafka_consumer_failed_records_total[1m])

# Processing latency
histogram_quantile(0.95, kafka_consumer_processing_duration_seconds)
```

### Grafana Dashboard

**Panels**:
1. Consumer Lag (time series)
2. Throughput (messages/sec)
3. Error Rate (%)
4. p95 Processing Latency

**Alerts**:
- Lag >1000 for 5 minutes → Page on-call engineer
- Error rate >1% for 10 minutes → Slack notification

---

## Testing

### Unit Tests

```java
@Test
void testConsumeMessageEvent_ValidEvent_PersistsToMongoDB() {
    // Arrange
    MessageEvent event = MessageEvent.newBuilder()
        .setMessageId(UUID.randomUUID().toString())
        .setConversationId(UUID.randomUUID().toString())
        .setMessageText("Test")
        .build();
    
    // Act
    worker.consumeMessageEvent(event, acknowledgment);
    
    // Assert
    verify(messageRepository).save(any(Message.class));
    verify(acknowledgment).acknowledge();
}
```

### Integration Tests

```java
@SpringBootTest
@EmbeddedKafka(topics = {"message-events"})
class MessageDeliveryWorkerIntegrationTest {
    @Test
    void testEndToEnd() {
        // Publish event → Wait for consumption → Verify MongoDB persistence
    }
}
```

---

## Best Practices

1. **Acknowledge Last**: Only ack after all side effects complete
2. **Idempotent Processing**: Handle duplicate events gracefully
3. **Monitor Lag**: Alert when lag exceeds threshold
4. **Circuit Breakers**: Protect downstream services
5. **Structured Logging**: Include `message_id` in all logs
6. **Batch When Possible**: Batch operations for higher throughput

---

## References

- [Kafka Event Processing](../../../../../DOC_REVISADA/04-KAFKA-E-PROCESSAMENTO-ASSINCRONO.md)
- [MongoDB Persistence](../../../../../DOC_REVISADA/05-MONGODB-E-PERSISTENCIA.md)
- [Kafka Optimization](../../../../../docs/kafka/consumer-optimization.md)
- [System Architecture](../../../../../docs/architecture/system-overview.md)
