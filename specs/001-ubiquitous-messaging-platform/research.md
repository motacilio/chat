# Phase 0: Architecture Research & Decisions

**Feature**: Ubiquitous Messaging Platform  
**Date**: 2025-11-18  
**Status**: Complete

This document resolves all technical unknowns identified in the planning phase, documenting architectural decisions with rationale and alternatives considered.

---

## Decision 1: gRPC vs REST for API Layer

**Chosen**: **gRPC with Protobuf 3.25.3**

**Rationale**:
- **30-40% lower latency** than JSON due to binary serialization (critical for <100ms p95 requirement)
- **Strong typing** via Protobuf prevents contract drift between clients and server (educational value: teaches API versioning via package naming `chat_api.v1`)
- **Bidirectional streaming** native support for real-time message delivery (SENT → DELIVERED → READ state notifications)
- **Industry standard** for microservices communication (teaches relevant production patterns)
- **Code generation** from Protobuf reduces boilerplate and ensures type safety across language boundaries

**Alternatives Considered**:
1. **REST with JSON**: Simpler, more familiar, easier debugging with curl. **REJECTED** because text serialization adds 30-40% latency overhead and lacks native streaming for real-time updates.
2. **GraphQL**: Flexible client-driven queries, reduces over-fetching. **REJECTED** because overkill for simple CRUD + pub/sub messaging patterns; adds query complexity without corresponding benefit.
3. **WebSocket**: Native bidirectional streaming. **REJECTED** because lacks strongly-typed schema enforcement and standardized code generation (Protobuf superior for contract management).

**Implementation Notes**:
- Use `grpc-spring-boot-starter` for Spring Boot integration
- Protobuf package versioning: `package chat_api.v1;` enables backward-compatible evolution to `chat_api.v2`
- Enable gRPC Server Reflection for runtime API discovery (aids debugging + documentation)

**Learning Resources**:
- gRPC Java Quickstart: https://grpc.io/docs/languages/java/quickstart/
- Protobuf Language Guide: https://protobuf.dev/programming-guides/proto3/

---

## Decision 2: RabbitMQ Message Patterns for Async Processing

**Chosen**: **RabbitMQ with Topic Exchange + Worker Queues** (POC Phase), **defer Kafka** to post-POC

**Rationale**:
- **At-least-once delivery** via manual acknowledgments (worker confirms processing before RabbitMQ removes message from queue)
- **Topic exchange** enables flexible routing (e.g., `message.sent`, `message.delivered`, `message.read` topics for different worker types)
- **Worker queues** distribute load across multiple consumer instances (demonstrates horizontal scaling pattern)
- **Persistent queues** prevent message loss during broker restarts (durable=true, delivery_mode=2)
- **Lower complexity** than Kafka for MVP (no partition management, offset tracking, or Zookeeper dependency)

**RabbitMQ Topology** (POC):
```
Producer (API) → Exchange (topic: "message.events")
                    ↓ (routing key: "message.sent")
                Queue: "message-processing-queue"
                    ↓
                Workers (MessageDeliveryWorker) → MongoDB
```

**When to Add Kafka** (Post-POC):
- **Trigger**: When throughput exceeds 10,000 messages/second OR need event sourcing with replay capability
- **Kafka advantages**: Partition-based horizontal scaling, log retention for replay, higher throughput (millions msg/sec)
- **Migration path**: Keep RabbitMQ for request-response patterns, add Kafka for event streaming (complement, not replace)

**Alternatives Considered**:
1. **Apache Kafka**: Higher throughput, partition-based scaling, event log persistence. **DEFERRED** because adds complexity (partition assignment, offset management, Zookeeper/KRaft) unnecessary for MVP. RabbitMQ proves async patterns first.
2. **AWS SQS**: Managed service, no broker maintenance. **REJECTED** because vendor lock-in and doesn't teach students about message broker internals (educational goal requires hands-on RabbitMQ/Kafka experience).
3. **Redis Pub/Sub**: Simple, fast. **REJECTED** because no delivery guarantees (messages lost if no subscribers active) and lacks persistent queues.

**Implementation Notes**:
- RabbitMQ connection pool: 10 connections per service instance (sized for 1000 concurrent users)
- Prefetch count: 10 messages per worker (prevents overload, enables backpressure)
- Dead Letter Exchange (DLX) for failed messages after 3 retry attempts
- Spring AMQP `@RabbitListener` with manual acknowledgment mode

**Learning Resources**:
- RabbitMQ Tutorial (Work Queues): https://www.rabbitmq.com/tutorials/tutorial-two-java.html
- Spring AMQP Reference: https://docs.spring.io/spring-amqp/reference/

---

## Decision 3: MongoDB Schema Design for Message Persistence

**Chosen**: **MongoDB with Embedded Documents + Indexes**

**Rationale**:
- **Flexible schema** accommodates multiple message types (text, file attachments, future: voice/video) without ALTER TABLE migrations
- **Embedded documents** for message state transitions (array of {state, timestamp, recipient_id}) avoids joins and enables atomic updates
- **Write concern: majority** ensures durability across replica set (at-least-once delivery guarantee even during network partitions)
- **Indexes** on (conversation_id, timestamp) enable fast pagination queries (<200ms for 50-message pages per NFR-014)
- **Educational value**: Demonstrates NoSQL design patterns (denormalization, embedded vs referenced documents, eventual consistency)

**Schema Design**:

**Messages Collection**:
```javascript
{
  "_id": ObjectId("..."),
  "message_id": "UUID",              // Unique index for idempotency
  "conversation_id": "UUID",         // Index for queries
  "sender_id": "user123",
  "message_text": "Hello world",     // OR null if file message
  "file_metadata": {                 // Embedded document (P2)
    "file_id": "UUID",
    "filename": "report.pdf",
    "size_bytes": 524288,
    "storage_url": "minio://bucket/..."
  },
  "timestamp": ISODate("2025-11-18T10:30:00Z"),
  "sequence_number": 42,             // Per-conversation ordering
  "state_history": [                 // Embedded array for state tracking
    { "state": "SENT", "timestamp": ISODate("..."), "recipient_id": null },
    { "state": "DELIVERED", "timestamp": ISODate("..."), "recipient_id": "user456" },
    { "state": "READ", "timestamp": ISODate("..."), "recipient_id": "user456" }
  ]
}
```

**Conversations Collection**:
```javascript
{
  "_id": ObjectId("..."),
  "conversation_id": "UUID",         // Unique index
  "type": "PRIVATE",                 // or "GROUP"
  "participants": ["user123", "user456"], // Index for user lookup
  "created_at": ISODate("..."),
  "last_message_at": ISODate("..."), // For sorting user's conversation list
  "last_message_preview": "Hello world..."
}
```

**Indexes Required**:
```javascript
// Messages collection
db.messages.createIndex({ "message_id": 1 }, { unique: true });         // Idempotency
db.messages.createIndex({ "conversation_id": 1, "timestamp": -1 });    // Pagination queries
db.messages.createIndex({ "sender_id": 1, "timestamp": -1 });          // User's sent messages

// Conversations collection
db.conversations.createIndex({ "conversation_id": 1 }, { unique: true });
db.conversations.createIndex({ "participants": 1, "last_message_at": -1 }); // User's conversation list
```

**Alternatives Considered**:
1. **PostgreSQL with JSONB**: Relational + flexible columns. **REJECTED** because requires schema migrations for new message types and doesn't teach NoSQL patterns (educational goal includes MongoDB experience).
2. **Cassandra**: Optimized for write-heavy workloads, partition-based scaling. **REJECTED** because excessive complexity for MVP (partition key design, eventual consistency model) and steeper learning curve than MongoDB.
3. **Separate MessageState table**: Normalized design. **REJECTED** because requires JOIN equivalent (MongoDB $lookup) adding latency; embedded state_history array is atomic and faster for reads.

**Implementation Notes**:
- Spring Data MongoDB `@Document` annotations for entity mapping
- Repository methods use `@Query` annotations to document indexes used (per Constitution Principle V)
- Write concern: `WriteConcern.MAJORITY` (ensures durability, trades performance for consistency)
- Replica set: minimum 3 nodes (per NFR-011) for automatic failover

**Learning Resources**:
- MongoDB Schema Design Best Practices: https://www.mongodb.com/docs/manual/core/data-model-design/
- Spring Data MongoDB Reference: https://docs.spring.io/spring-data/mongodb/reference/

---

## Decision 4: Resumable File Upload Protocol (P2 - Deferred to Post-POC)

**Chosen**: **tus Protocol (resumable upload standard)** for P2 file upload feature

**Rationale**:
- **Industry standard** for resumable uploads (used by Vimeo, Cloudflare, Google)
- **Client libraries** available for Java (tus-java-server), JavaScript (tus-js-client), iOS/Android (reduces client implementation complexity)
- **HTTP-based** with standardized headers (`Upload-Offset`, `Upload-Length`, `Tus-Resumable`) for chunk management
- **Stateless server** design (chunk metadata stored in MinIO, not server memory) enables horizontal scaling
- **Educational value**: Demonstrates fault-tolerant file transfer patterns and chunked upload protocol design

**Protocol Flow**:
1. **Initiate Upload**: `POST /v1/files/initiate` → returns `file_id` + `upload_url`
2. **Upload Chunks**: `PATCH {upload_url}` with `Upload-Offset` header (5 MB chunks)
3. **Resume**: Client queries `HEAD {upload_url}` → gets current `Upload-Offset`, continues from there
4. **Complete**: `POST /v1/files/complete` → finalizes upload, creates file message in conversation

**Alternatives Considered**:
1. **S3 Multipart Upload API**: AWS standard, MinIO-compatible. **REJECTED** because proprietary API (vendor-specific) vs. tus open standard; tus has better client library ecosystem.
2. **Custom chunking**: Implement own protocol. **REJECTED** per Constitution Principle VIII (standard library reuse) - tus protocol solves resumable uploads comprehensively, no need to reinvent.
3. **WebSocket streaming**: Real-time upload progress. **REJECTED** because stateful connection required (breaks horizontal scaling); tus HTTP-based approach is stateless.

**Implementation Notes** (P2 only):
- Use `tus-java-server` library (Maven: `io.tus.java.server:tus-java-server`)
- Store chunk metadata in MinIO bucket metadata (not in-memory or MongoDB)
- Max file size: 2 GB (enforced at initiation, per FR-019)
- Chunk size: 5 MB (balances network efficiency vs. resume granularity)

**Learning Resources**:
- tus Protocol Specification: https://tus.io/protocols/resumable-upload.html
- tus Java Server: https://github.com/tus/tus-java-server

---

## Decision 5: Telegram Bot API Integration (P4 - Deferred to Post-POC)

**Chosen**: **Telegram Bot API (ONE real integration)** + **Mocked adapters for WhatsApp/Instagram**

**Rationale**:
- **Telegram Bot API is FREE** (no commercial subscription, unlike WhatsApp Business API at $0.005/msg)
- **Comprehensive documentation** and official Java SDK (`telegrambots`) simplifies integration
- **Webhook support** for bidirectional messaging (receive user replies, forward to internal platform)
- **Educational scope**: One real integration teaches adapter pattern, webhook handling, and external API error handling; mocked adapters demonstrate plugin architecture without commercial API costs

**Adapter Interface** (P4):
```java
public interface PlatformAdapter {
    /** Establish connection with external platform (authenticate, register webhook) */
    ConnectionResult connect(PlatformCredentials credentials);
    
    /** Send text message to external platform user */
    SendResult sendMessage(String externalUserId, String messageText);
    
    /** Send file to external platform user */
    SendResult sendFile(String externalUserId, FileMetadata file);
    
    /** Handle incoming webhook from external platform (user reply) */
    void webhookHandler(HttpServletRequest request, HttpServletResponse response);
}
```

**Real Implementation**: `TelegramBotAdapter implements PlatformAdapter`
- Uses `telegrambots` library (Maven: `org.telegram:telegrambots`)
- Webhook registration: `setWebhook` API call points to `https://api.example.com/webhooks/telegram`
- Incoming messages parsed, mapped to internal `Message` entity, routed to recipient

**Mock Implementations**: `WhatsAppMockAdapter`, `InstagramMockAdapter`
- Implement same `PlatformAdapter` interface
- Log "send" operations to console (no real API calls)
- Simulate webhook callbacks with pre-configured test messages
- Enable testing of multi-platform routing logic without commercial API dependencies

**Alternatives Considered**:
1. **WhatsApp Business API**: Official, production-ready. **REJECTED** because requires expensive subscription ($0.005/message), lengthy approval process (weeks), and monthly minimum charges (impractical for educational project).
2. **Twilio API for WhatsApp**: Managed service wrapper. **REJECTED** because still charges per message ($0.005) and hides underlying WhatsApp Business API complexity (less educational value).
3. **Signal API**: Open-source, privacy-focused. **REJECTED** because unofficial APIs (Signal discourages automated bots), unstable SDK, and less industry relevance than Telegram.

**Implementation Notes** (P4 only):
- Telegram Bot API rate limits: 30 messages/second (document in adapter implementation)
- Webhook security: Verify `X-Telegram-Bot-Api-Secret-Token` header (prevents spoofed callbacks)
- Error handling: Adapter failures MUST NOT block internal message delivery (per FR-033) - use circuit breaker pattern
- Adapter registry: Spring `@Component` with `@Qualifier("telegram")` for dependency injection

**Learning Resources**:
- Telegram Bot API Documentation: https://core.telegram.org/bots/api
- Telegram Bots Java Library: https://github.com/rubenlagus/TelegramBots

---

## Decision 6: Authentication Strategy (Assumed External per Spec A-002)

**Chosen**: **Spring Security with OAuth2 JWT tokens** (implementation deferred, assume pre-authenticated users for MVP)

**Rationale**:
- Spec Assumption A-002 states: "Users are pre-authenticated; this specification does NOT cover user authentication/authorization implementation"
- MVP focuses on messaging core; auth is separate microservice concern
- For integration testing: use mock JWT tokens with hardcoded `user_id` claims

**Deferred Implementation** (Post-MVP):
- OAuth2 Authorization Server (Keycloak or Spring Authorization Server)
- JWT token validation in gRPC interceptor
- User service for user CRUD operations

**Implementation Notes** (MVP):
- gRPC metadata extraction: `user_id` from `Authorization: Bearer <token>` header
- Mock JWT decoder for tests (returns fixed user claims)
- Constitution Principle VIII (Incremental Delivery) supports deferring auth to post-POC

---

## Summary: All Unknowns Resolved

| Unknown | Decision | Deferred? | Rationale |
|---------|----------|-----------|-----------|
| API Protocol | gRPC + Protobuf | No | Lower latency, strong typing, streaming support |
| Message Broker | RabbitMQ (POC) → Kafka (Post-POC) | Kafka: Yes | RabbitMQ proves patterns first, Kafka adds complexity |
| Database | MongoDB (replica set) | No | Flexible schema, embedded documents, educational value |
| File Uploads | tus Protocol | Yes (P2) | Industry standard, client libraries, resumable |
| External Integration | Telegram Bot API (real) + Mocks | Yes (P4) | One real integration, mocks for others (cost/scope) |
| Authentication | Spring Security OAuth2 | Yes (Post-MVP) | Assume pre-authenticated per spec A-002 |

**Next Phase**: Proceed to Phase 1 (Design) - generate `data-model.md`, `contracts/`, `quickstart.md` based on above decisions.
