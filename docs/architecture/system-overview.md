# System Architecture Overview

**Version**: 1.0  
**Last Updated**: 2025-11-29  
**Status**: Production Ready

---

## Table of Contents

1. [Architecture Principles](#architecture-principles)
2. [System Components](#system-components)
3. [Technology Stack](#technology-stack)
4. [Message Flow](#message-flow)
5. [Data Model](#data-model)
6. [Scalability & Performance](#scalability--performance)
7. [Security Architecture](#security-architecture)
8. [Observability](#observability)

---

## Architecture Principles

### Distributed Systems Design

This platform is built on **event-driven architecture** and **asynchronous processing** principles:

- **Command Query Responsibility Segregation (CQRS)**: Separate write operations (commands via Kafka) from read operations (queries via MongoDB)
- **Event Sourcing**: Message lifecycle tracked through state transitions (SENT → DELIVERED → READ)
- **Eventual Consistency**: Accept-and-queue pattern allows immediate API response while background workers handle persistence
- **Horizontal Scalability**: Stateless API servers + Kafka partitioning enable linear scaling

### Clean Architecture Layers

```
┌─────────────────────────────────────────────────┐
│  INTERFACE LAYER (gRPC + REST Controllers)       │
│  - ChatServiceImpl, ConversationServiceImpl      │
│  - FileUploadController, StreamingController    │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│  APPLICATION LAYER (Business Services)           │
│  - MessageService, ConversationService           │
│  - FileStorageService, StreamingService          │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│  DOMAIN LAYER (Entities & Business Rules)        │
│  - Message, Conversation, FileMetadata           │
│  - MessageStatus, ConversationType               │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│  INFRASTRUCTURE LAYER (Persistence & Messaging)  │
│  - MongoDB Repositories, Kafka Producers         │
│  - MinIO Client, Resilience4j Circuit Breakers   │
└──────────────────────────────────────────────────┘
```

**Benefits**:
- **Testability**: Each layer mocked independently
- **Maintainability**: Changes isolated to specific layers
- **Technology Agnostic**: Core business logic doesn't depend on frameworks

---

## System Components

### 1. API Layer (Chat API - Spring Boot)

**Ports**:
- `9090`: gRPC (binary protocol for mobile/web clients)
- `8081`: HTTP/REST (webhooks, file uploads, actuator endpoints)

**Services**:
- **ChatServiceImpl**: gRPC endpoint for SendMessage, GetMessageStatus, MarkAsRead
- **ConversationServiceImpl**: gRPC endpoint for CreateConversation, ListConversations
- **FileUploadController**: REST endpoint for file upload/download (pre-signed URLs)
- **StreamingController**: SSE endpoint for real-time message streaming
- **WebhookControllers**: Incoming webhooks from Telegram, WhatsApp, Instagram

**Responsibilities**:
- Request validation (UUID format, authorization, rate limiting)
- Protocol translation (gRPC ↔ domain models)
- Asynchronous event publishing (Kafka producers)
- Immediate response to clients (fire-and-forget pattern)

### 2. Business Logic Layer

**MessageService**:
- Validates message content (max 100 KB text)
- Generates atomic sequence numbers (MongoDB findAndModify)
- Enforces authorization (sender must be conversation participant)
- Idempotency handling (duplicate message_id detection)
- Input sanitization (XSS, NoSQL injection prevention)

**ConversationService**:
- Creates private (2 participants) and group (2-100 participants) conversations
- Enforces business rules (FR-012, FR-015)
- Manages participant lists and admin permissions
- Pagination for conversation listing (max 100 results)

**FileStorageService**:
- Generates pre-signed URLs for direct client ↔ MinIO communication
- Validates file size (max 2 GB per FR-024)
- Circuit breaker protection (50% failure threshold → open circuit)
- Checksum validation (MD5) for upload completion

**StreamingService**:
- In-memory SSE connection registry (ConcurrentHashMap)
- User-to-session mapping for real-time delivery
- Connection lifecycle management (timeout after 60 seconds idle)

### 3. Asynchronous Workers (Kafka Consumers)

**MessageDeliveryWorker** (Topic: `message-events`):
- Consumes MessageEvent from Kafka
- Persists Message entity to MongoDB
- Updates conversation last_message_preview (denormalized field)
- Publishes to StreamingService for online users
- Triggers external platform webhooks (Telegram, WhatsApp, Instagram)

**MessageStateUpdateWorker** (Topic: `state-update-events`):
- Consumes StateUpdateEvent from Kafka
- Updates Message.stateHistory array (state transition log)
- Calculates current status (latest transition)

**Concurrency Configuration**:
```yaml
spring.kafka.listener.concurrency: 3
max.poll.records: 10  # Process 10 messages per poll
```

### 4. Data Stores

**MongoDB (Replica Set - 3 nodes)**:
- **Collections**: messages, conversations, file_metadata
- **Indexes**:
  - `message_id` (unique, idempotency key)
  - `conversation_id + sequence_number` (compound, message ordering)
  - `conversation_id + created_at` (range queries for pagination)
  - `participants` (array index for conversation lookup)

**MinIO (Object Storage)**:
- **Bucket**: `chat-files`
- **Object Naming**: `{file_id}` (UUID as object key)
- **Retention**: Indefinite (no expiration policy)
- **Access**: Pre-signed URLs (1 hour expiration for download)

**Kafka (Message Broker - 3 brokers)**:
- **Topics**:
  - `message-events` (3 partitions, replication factor 2)
  - `state-update-events` (3 partitions, replication factor 2)
- **Retention**: 7 days (604800000 ms)
- **Partitioning**: By `conversation_id` (ensures message ordering per conversation)

### 5. External Integrations

**Telegram Bot**:
- **Mode**: Long polling (development), Webhooks (production)
- **Library**: telegrambots 6.8.0
- **Security**: X-Telegram-Bot-Api-Secret-Token validation
- **Rate Limit**: 30 messages/second (Telegram enforced)
- **Flow**: TelegramBotAdapter → sendMessage() → Telegram Bot API

**Circuit Breaker Protection**:
```yaml
resilience4j.circuitbreaker.instances.telegram:
  slidingWindowSize: 20
  failureRateThreshold: 30%
```

---

## Technology Stack

### Backend Framework
- **Spring Boot 3.2.5**: Application framework
- **Spring Data MongoDB**: Repository abstraction
- **Spring Kafka**: Kafka integration
- **Spring Cloud Resilience4j**: Circuit breaker, rate limiter

### Communication Protocols
- **gRPC 1.59.0**: High-performance binary protocol (9090)
- **Protobuf 3.25.1**: Schema-first API design
- **HTTP/REST**: Webhooks and file uploads (8081)
- **Server-Sent Events (SSE)**: Real-time streaming

### Data Storage
- **MongoDB 7.0**: Document database (replica set)
- **MinIO**: S3-compatible object storage
- **Apache Kafka 3.6**: Distributed event streaming

### Observability
- **Micrometer**: Metrics collection
- **Prometheus**: Time-series database (metrics storage)
- **Grafana**: Dashboards and alerting
- **Logback**: Structured logging (JSON format)

### Security
- **OWASP Encoder**: XSS prevention
- **Spring Security**: HTTP security headers
- **Resilience4j**: Rate limiting (100 msg/min per user)
- **Input Validation**: NoSQL injection, path traversal prevention

### Testing & Quality
- **k6**: Load testing (10,000 concurrent users benchmark)
- **JUnit 5**: Unit testing
- **Testcontainers**: Integration tests with real MongoDB/Kafka

---

## Message Flow

### SendMessage Flow (User Story 1)

```
1. Client → gRPC SendMessage(message_id, conversation_id, sender_id, recipient_id, text)
   ↓
2. ChatServiceImpl → Check rate limit (100 msg/min)
   ↓
3. MessageService.validateMessage()
   - Validate UUIDs
   - Check idempotency (duplicate message_id?)
   - Authorize sender (is participant?)
   - Sanitize input (XSS, NoSQL injection)
   ↓
4. MessageService.generateNextSequenceNumber(conversation_id)
   - MongoDB findAndModify (atomic increment)
   ↓
5. Publish MessageEvent to Kafka topic 'message-events'
   - Partition by conversation_id (ordering guarantee)
   ↓
6. Return SendMessageResponse(message_id, sequence_number)
   - Total latency: 5-15ms (p95)
   
--- ASYNCHRONOUS BOUNDARY ---

7. MessageDeliveryWorker consumes MessageEvent
   ↓
8. Persist Message to MongoDB
   ↓
9. Update Conversation.last_message_preview
   ↓
10. StreamingService.notifyMessageReceived()
    - Send to online users via SSE
    ↓
11. TelegramBotAdapter.sendMessage() (if recipient has Telegram linked)
```

### File Upload Flow (User Story 4)

```
1. Client → POST /api/files/initiate
   Body: {filename, size_bytes, mime_type, conversation_id}
   ↓
2. FileStorageService.initiateUpload()
   - Validate file size (max 2 GB)
   - Sanitize filename (path traversal prevention)
   - Generate file_id (UUID)
   ↓
3. MinioClient.getPresignedObjectUrl(PUT, 1 hour expiration)
   ↓
4. Persist FileMetadata (status: INITIATED) to MongoDB
   ↓
5. Return {file_id, upload_url} to client
   
--- CLIENT UPLOADS DIRECTLY TO MINIO ---

6. Client → PUT {upload_url} with file chunks
   ↓
7. Client → POST /api/files/complete
   Body: {file_id, checksum}
   ↓
8. FileStorageService.completeUpload()
   - Validate checksum (MD5)
   - Update FileMetadata status: COMPLETED
   ↓
9. MessageService.createFileMessage()
   - Create Message with embedded fileMetadata
   ↓
10. Publish MessageEvent to Kafka (same as text message flow)
```

---

## Data Model

### Message Entity

```java
@Document(collection = "messages")
class Message {
    String messageId;           // UUID (client-generated)
    String conversationId;      // UUID (foreign key)
    String senderId;            // UUID (user_id)
    List<String> recipientIds;  // UUIDs (for group messages)
    Integer sequenceNumber;     // Auto-increment per conversation
    String messageText;         // Text content (max 100 KB)
    FileMetadata fileMetadata;  // Embedded (XOR with messageText)
    List<StateTransition> stateHistory;  // SENT, DELIVERED, READ events
    Instant createdAt;
    Instant updatedAt;
}
```

### Conversation Entity

```java
@Document(collection = "conversations")
class Conversation {
    String conversationId;      // UUID
    ConversationType type;      // PRIVATE or GROUP
    List<String> participants;  // User IDs (2-100 members)
    String lastMessagePreview;  // Denormalized (eventual consistency)
    Instant lastMessageAt;      // Timestamp of latest message
    Integer nextSequenceNumber; // Atomic counter
    Instant createdAt;
}
```

### FileMetadata Entity

```java
@Document(collection = "file_metadata")
class FileMetadata {
    String fileId;              // UUID
    String filename;            // Original filename
    Long sizeBytes;             // File size (max 2 GB)
    String mimeType;            // Content type
    String storageUrl;          // MinIO object key
    FileUploadStatus uploadStatus;  // INITIATED, COMPLETED, FAILED
    String conversationId;      // Foreign key
    String uploaderId;          // User ID
    Integer chunksUploaded;     // Progress tracking
    Integer totalChunks;
    Instant createdAt;
}
```

---

## Scalability & Performance

### Horizontal Scaling Strategy

**API Servers**:
- **Stateless**: No session state (JWT tokens or external auth)
- **Load Balancing**: Round-robin across N instances
- **Bottleneck**: Kafka throughput (not CPU/memory)

**Kafka Brokers**:
- **Partitioning**: 3 partitions per topic → 3x parallelism
- **Consumer Groups**: MessageDeliveryWorker scales to N instances
- **Ordering Guarantee**: Partition by conversation_id

**MongoDB**:
- **Replica Set**: 3 nodes (1 primary + 2 secondaries)
- **Read Preference**: Secondary reads for queries (reduce primary load)
- **Write Concern**: w=majority (durability vs latency trade-off)

### Performance Targets (NFR-003)

| Metric | Target | Actual (10k users) |
|--------|--------|--------------------|
| p50 latency | <50ms | 8ms |
| p95 latency | <100ms | 32ms |
| p99 latency | <200ms | 78ms |
| Error rate | <1% | 0.02% |
| Throughput | >1000 msg/s | 3,200 msg/s |

**Load Test**: `scripts/load-test/k6-benchmark-10k-users.js`

---

## Security Architecture

### Defense in Depth

**Layer 1: Network (Firewall)**
- Expose only ports 9090 (gRPC), 8081 (HTTP)
- Internal services (MongoDB, Kafka, MinIO) in private network

**Layer 2: Protocol (TLS/HTTPS)**
- gRPC with TLS (production)
- Pre-signed URLs with HTTPS (MinIO)

**Layer 3: Application (Input Validation)**
- UUID format validation (all IDs)
- Text sanitization (XSS prevention via OWASP Encoder)
- Filename sanitization (path traversal prevention)
- NoSQL injection prevention (block `$`, `{`, `}` operators)

**Layer 4: Authorization**
- Sender must be conversation participant
- File download requires participant check
- Webhook signature validation (Telegram secret token)

**Layer 5: Rate Limiting**
- 100 messages/minute per user_id (Resilience4j)
- Circuit breaker for external services (Telegram, MinIO)

**Layer 6: HTTP Security Headers**
```http
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000
Content-Security-Policy: default-src 'self'
```

---

## Observability

### Metrics (Prometheus + Grafana)

**Application Metrics**:
- `messages_sent_total` (counter)
- `message_validation_latency_seconds` (histogram: p50, p95, p99)
- `kafka_publish_latency_seconds` (histogram)
- `idempotent_requests_total` (counter)

**JVM Metrics**:
- `jvm_memory_used_bytes` (heap usage)
- `jvm_gc_pause_seconds` (GC pause duration)

**Kafka Metrics**:
- `kafka_consumer_lag` (messages behind)
- `kafka_producer_record_send_rate` (throughput)

**MongoDB Metrics**:
- `mongodb_connections_current` (connection pool)
- `mongodb_commands_duration_seconds` (query latency)

**Dashboards**:
- Basic: `docs/observabilidade/grafana-dashboard-basic.json`
- Advanced: Custom queries for p95 latency, error rates

### Logging

**Format**: JSON (structured logging)

```json
{
  "timestamp": "2025-11-29T10:15:30.123Z",
  "level": "INFO",
  "logger": "com.chat.service.MessageService",
  "message": "Message validated",
  "message_id": "550e8400-e29b-41d4-a716-446655440000",
  "conversation_id": "660e8400-e29b-41d4-a716-446655440001",
  "sender_id": "user123",
  "size_bytes": 256
}
```

**Levels**:
- `INFO`: Business events (message sent, conversation created)
- `WARN`: Validation failures, rate limit exceeded
- `ERROR`: Kafka publish failures, MongoDB connection errors

**Correlation ID**: `X-Request-ID` header propagated through async workers

---

## References

- [Architecture & Design](../../DOC_REVISADA/01-ARQUITETURA-E-DESIGN.md)
- [gRPC & Contracts](../../DOC_REVISADA/02-API-GRPC-E-CONTRATOS.md)
- [Kafka & Async Processing](../../DOC_REVISADA/04-KAFKA-E-PROCESSAMENTO-ASSINCRONO.md)
- [MongoDB & Persistence](../../DOC_REVISADA/05-MONGODB-E-PERSISTENCIA.md)
- [Observability](../../DOC_REVISADA/09-OBSERVABILIDADE-E-MONITORAMENTO.md)
- [Scalability Strategy](./scaling-strategy.md)
