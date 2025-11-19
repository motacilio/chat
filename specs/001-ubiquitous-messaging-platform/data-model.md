# Phase 1: Data Model Design

**Feature**: Ubiquitous Messaging Platform  
**Date**: 2025-11-18  
**Status**: Complete

This document defines all entities, MongoDB schemas, relationships, and indexes for the messaging platform POC.

---

## Entity Overview

The system has **7 core entities** aligned with spec Key Entities section:

1. **User**: Platform user with unique identifier
2. **Conversation**: Messaging context (1:1 or group)
3. **Message**: Single message in a conversation
4. **MessageState**: State transition tracking (embedded in Message)
5. **FileMetadata**: File attachment information (P2 - embedded in Message)
6. **Webhook**: Registered callback endpoint (P2)
7. **LinkedAccount**: External platform account mapping (P4)

**POC Scope (Phase 1)**: Entities 1-4 only. Entities 5-7 deferred to post-POC layers.

---

## Entity 1: User

**Purpose**: Represents a platform user with unique identifier and profile information.

**Domain Model** (Java POJO):
```java
package com.chat.domain.model;

/**
 * Responsibility: Represents a registered platform user.
 * Does NOT: Handle authentication (assumed external per spec A-002), manage linked accounts (P4).
 * Distributed Systems Concept: Aggregate root in domain model, uniquely identified by user_id.
 */
public class User {
    private String userId;           // UUID - unique identifier
    private String username;         // Display name (e.g., "john_doe")
    private String email;            // Contact email
    private Instant createdAt;       // Registration timestamp
    
    // Getters, setters, equals/hashCode based on userId
}
```

**MongoDB Schema**:
```javascript
// Collection: users
{
  "_id": ObjectId("673ab2c1e4b0a1234567890a"),
  "user_id": "550e8400-e29b-41d4-a716-446655440001",  // UUID (String)
  "username": "john_doe",
  "email": "john@example.com",
  "created_at": ISODate("2025-11-18T10:00:00Z")
}
```

**Indexes**:
```javascript
// Primary identifier (unique)
db.users.createIndex({ "user_id": 1 }, { unique: true, name: "idx_user_id" });

// Lookup by username (for user search - P3 feature)
db.users.createIndex({ "username": 1 }, { unique: true, name: "idx_username" });

// Lookup by email (for login - deferred auth feature)
db.users.createIndex({ "email": 1 }, { unique: true, name: "idx_email" });
```

**Relationships**:
- **Conversations**: User participates in many Conversations (many-to-many via `participants` array)
- **Messages**: User sends many Messages (one-to-many via `sender_id`)

---

## Entity 2: Conversation

**Purpose**: Represents a messaging context (1:1 private or group) containing messages.

**Domain Model** (Java POJO):
```java
package com.chat.domain.model;

/**
 * Responsibility: Represents a messaging context (1:1 or group) with participants.
 * Does NOT: Store messages directly (messages reference conversation_id), handle permissions (P3).
 * Distributed Systems Concept: Aggregate root for message grouping, enables conversation-level operations.
 */
public class Conversation {
    private String conversationId;        // UUID - unique identifier
    private ConversationType type;        // PRIVATE or GROUP
    private List<String> participants;    // List of user_id values
    private Instant createdAt;            // Creation timestamp
    private Instant lastMessageAt;        // Last message timestamp (for sorting)
    private String lastMessagePreview;    // Preview text (e.g., "Hello world...")
    
    public enum ConversationType {
        PRIVATE,  // 1:1 conversation (exactly 2 participants)
        GROUP     // Group conversation (n participants, max 100 in MVP per A-007)
    }
    
    // Getters, setters, equals/hashCode based on conversationId
}
```

**MongoDB Schema**:
```javascript
// Collection: conversations
{
  "_id": ObjectId("673ab2c1e4b0a1234567890b"),
  "conversation_id": "550e8400-e29b-41d4-a716-446655440000",  // UUID (String)
  "type": "PRIVATE",                                          // or "GROUP"
  "participants": [                                           // Array of user_id values
    "550e8400-e29b-41d4-a716-446655440001",
    "550e8400-e29b-41d4-a716-446655440002"
  ],
  "created_at": ISODate("2025-11-18T10:15:00Z"),
  "last_message_at": ISODate("2025-11-18T11:30:00Z"),
  "last_message_preview": "Hello! How are you?"
}
```

**Indexes**:
```javascript
// Primary identifier (unique)
db.conversations.createIndex({ "conversation_id": 1 }, { unique: true, name: "idx_conversation_id" });

// User's conversation list (FR-011: list with most recent message)
// Compound index: participant lookup + sort by last message
db.conversations.createIndex(
  { "participants": 1, "last_message_at": -1 }, 
  { name: "idx_participants_last_message" }
);

// Type-based queries (future: filter by PRIVATE vs GROUP)
db.conversations.createIndex({ "type": 1 }, { name: "idx_type" });
```

**Relationships**:
- **Users**: Conversation has many participants (many-to-many via `participants` array)
- **Messages**: Conversation contains many Messages (one-to-many via `conversation_id` foreign key)

**Authorization Rules** (per FR-010):
- Only participants can read/write messages in conversation
- Enforced at service layer: check if `current_user.userId` in `conversation.participants`

---

## Entity 3: Message

**Purpose**: Represents a single message (text or file) in a conversation with state tracking.

**Domain Model** (Java POJO):
```java
package com.chat.domain.model;

/**
 * Responsibility: Represents a single message with text OR file content and state lifecycle.
 * Does NOT: Handle delivery logic (see MessageDeliveryWorker), validate business rules (see MessageService).
 * Distributed Systems Concept: Event entity with state machine (SENT → DELIVERED → READ), demonstrates eventual consistency.
 */
public class Message {
    private String messageId;             // UUID - unique identifier (client-generated for idempotency)
    private String conversationId;        // Foreign key to Conversation
    private String senderId;              // Foreign key to User
    private String messageText;           // Text content (null if file message)
    private FileMetadata fileMetadata;    // File content (null if text message) - P2 deferred
    private Instant timestamp;            // Send timestamp
    private Long sequenceNumber;          // Per-conversation ordering (auto-incremented)
    private List<MessageStateTransition> stateHistory; // State tracking (embedded)
    
    // Getters, setters, equals/hashCode based on messageId
    
    /** Current state (derived from last entry in stateHistory) */
    public MessageStatus getCurrentStatus() {
        return stateHistory.isEmpty() 
            ? MessageStatus.SENT 
            : stateHistory.get(stateHistory.size() - 1).getState();
    }
}

/** Message state enum (FR-004) */
public enum MessageStatus {
    SENT,       // Accepted by server, persisted in MongoDB
    DELIVERED,  // Reached recipient's device (online user or reconnected offline user)
    READ        // Opened by recipient in conversation view
}
```

**MongoDB Schema**:
```javascript
// Collection: messages
{
  "_id": ObjectId("673ab2c1e4b0a1234567890c"),
  "message_id": "9e1g8891-9647-62g1-d16d-g29bf3h12cg9",      // UUID (String)
  "conversation_id": "550e8400-e29b-41d4-a716-446655440000",  // Foreign key
  "sender_id": "550e8400-e29b-41d4-a716-446655440001",        // Foreign key
  "message_text": "Hello! How are you?",                      // OR null if file message
  "file_metadata": null,                                       // OR { file_id, filename, size_bytes, storage_url } for P2
  "timestamp": ISODate("2025-11-18T11:30:00Z"),
  "sequence_number": 42,                                       // Per-conversation auto-increment
  "state_history": [                                           // Embedded array (MessageStateTransition)
    {
      "state": "SENT",
      "timestamp": ISODate("2025-11-18T11:30:00.123Z"),
      "recipient_id": null                                     // null for SENT state
    },
    {
      "state": "DELIVERED",
      "timestamp": ISODate("2025-11-18T11:30:01.456Z"),
      "recipient_id": "550e8400-e29b-41d4-a716-446655440002"  // Specific recipient (for groups)
    },
    {
      "state": "READ",
      "timestamp": ISODate("2025-11-18T11:32:15.789Z"),
      "recipient_id": "550e8400-e29b-41d4-a716-446655440002"
    }
  ]
}
```

**Indexes**:
```javascript
// Primary identifier + idempotency guarantee (FR-003)
db.messages.createIndex({ "message_id": 1 }, { unique: true, name: "idx_message_id" });

// Conversation history queries (FR-008: pagination by timestamp)
// Compound index: conversation lookup + sort by timestamp descending
db.messages.createIndex(
  { "conversation_id": 1, "timestamp": -1 }, 
  { name: "idx_conversation_timestamp" }
);

// Per-conversation ordering (FR-007: preserve message ordering)
// Compound index: conversation lookup + sort by sequence_number
db.messages.createIndex(
  { "conversation_id": 1, "sequence_number": 1 }, 
  { name: "idx_conversation_sequence" }
);

// Sender's message history (for analytics/debugging)
db.messages.createIndex(
  { "sender_id": 1, "timestamp": -1 }, 
  { name: "idx_sender_timestamp" }
);
```

**Relationships**:
- **Conversation**: Message belongs to one Conversation (many-to-one via `conversation_id`)
- **User**: Message sent by one User (many-to-one via `sender_id`)
- **MessageStateTransition**: Message has many state transitions (one-to-many embedded)

**Idempotency Guarantee** (FR-003):
- Unique index on `message_id` enforces deduplication at database level
- Client generates UUID for `message_id` before submission
- Duplicate submissions return error: `"Message with message_id {uuid} already exists"`

**Sequence Number Generation**:
- Auto-incremented per conversation using MongoDB `findAndModify` with atomic increment
- Ensures FR-007: "Preserve message ordering within conversation"
- Alternative: Use `timestamp` for ordering (less reliable due to clock skew)

---

## Entity 4: MessageStateTransition (Embedded)

**Purpose**: Tracks state lifecycle for a message (SENT → DELIVERED → READ).

**Domain Model** (Java POJO - Embedded in Message):
```java
package com.chat.domain.model;

/**
 * Responsibility: Represents a single state transition event for a message.
 * Does NOT: Handle state validation logic (see MessageService for state machine rules).
 * Distributed Systems Concept: Event sourcing lite - state history enables audit trail and eventual consistency debugging.
 */
public class MessageStateTransition {
    private MessageStatus state;      // SENT, DELIVERED, or READ
    private Instant timestamp;        // When state transition occurred
    private String recipientId;       // Specific recipient (for group messages), null for SENT
    
    // Getters, setters
}
```

**MongoDB Schema** (Embedded in `messages.state_history` array):
```javascript
{
  "state": "DELIVERED",                                      // Enum: SENT, DELIVERED, READ
  "timestamp": ISODate("2025-11-18T11:30:01.456Z"),
  "recipient_id": "550e8400-e29b-41d4-a716-446655440002"    // null for SENT, user_id for DELIVERED/READ
}
```

**State Machine Rules**:
- **SENT**: Initial state when message accepted by API (timestamp = message creation)
- **DELIVERED**: Transition when message reaches recipient's device (online user) or reconnection (offline user)
- **READ**: Transition when recipient opens conversation and views message
- **Per-Recipient Tracking**: Group messages have multiple DELIVERED/READ entries (one per participant)

**Indexes**: No separate indexes (embedded in `messages` collection, covered by parent indexes)

---

## Entity 5: FileMetadata (Embedded - P2 Deferred)

**Purpose**: Represents file attachment information for file messages.

**Domain Model** (Java POJO - Embedded in Message):
```java
package com.chat.domain.model;

/**
 * Responsibility: Represents file attachment metadata (filename, size, storage reference).
 * Does NOT: Handle file upload/download logic (see FileStorageService), validate file types (see FileValidator).
 * Distributed Systems Concept: Separation of metadata (MongoDB) vs. binary storage (MinIO) - teaches blob storage patterns.
 */
public class FileMetadata {
    private String fileId;           // UUID - unique identifier
    private String filename;         // Original filename (e.g., "report.pdf")
    private Long sizeBytes;          // File size in bytes (max 2 GB per FR-019)
    private String mimeType;         // Content type (e.g., "application/pdf")
    private String storageUrl;       // MinIO reference (e.g., "minio://bucket/path")
    private Instant uploadedAt;      // Upload completion timestamp
    
    // Getters, setters
}
```

**MongoDB Schema** (Embedded in `messages.file_metadata`):
```javascript
{
  "file_id": "ch4j1124-2970-95j4-g49g-j52ei6k45fj2",       // UUID (String)
  "filename": "quarterly_report_Q4_2025.pdf",
  "size_bytes": 524288,                                     // 512 KB
  "mime_type": "application/pdf",
  "storage_url": "minio://chat-files/2025/11/18/ch4j1124...", // MinIO object reference
  "uploaded_at": ISODate("2025-11-18T12:00:00Z")
}
```

**Implementation Notes** (P2 only):
- Resumable uploads via tus protocol (per research.md Decision 4)
- Pre-signed download URLs (1-hour expiry per FR-018) generated by MinIO
- File size limit enforced at upload initiation: 2 GB (FR-019)

---

## Entity 6: Webhook (P2 Deferred)

**Purpose**: Registered callback endpoint for event notifications (message_delivered, message_read).

**Domain Model** (Java POJO):
```java
package com.chat.domain.model;

/**
 * Responsibility: Represents a registered webhook endpoint for event delivery.
 * Does NOT: Handle webhook invocation logic (see WebhookPublisher), validate callback URLs (see WebhookValidator).
 * Distributed Systems Concept: Event-driven notifications with retry logic (at-least-once delivery).
 */
public class Webhook {
    private String webhookId;        // UUID - unique identifier
    private String userId;           // Owner user_id
    private String callbackUrl;      // HTTPS endpoint (e.g., "https://client.example.com/webhooks")
    private List<String> eventTypes; // List: ["message_delivered", "message_read"]
    private boolean active;          // Enabled/disabled flag
    private String secret;           // HMAC-SHA256 signing secret (for X-Webhook-Signature)
    
    // Getters, setters
}
```

**MongoDB Schema**:
```javascript
// Collection: webhooks
{
  "_id": ObjectId("673ab2c1e4b0a1234567890d"),
  "webhook_id": "ej6l3346-41b2-b7l6-i6bi-l74gk8m67hl4",     // UUID (String)
  "user_id": "550e8400-e29b-41d4-a716-446655440001",        // Foreign key
  "callback_url": "https://client.example.com/webhooks",
  "event_types": ["message_delivered", "message_read"],
  "active": true,
  "secret": "webhook_secret_abc123"                          // For HMAC signature verification
}
```

**Indexes**:
```javascript
db.webhooks.createIndex({ "webhook_id": 1 }, { unique: true, name: "idx_webhook_id" });
db.webhooks.createIndex({ "user_id": 1, "active": 1 }, { name: "idx_user_active" }); // Active webhooks lookup
```

---

## Entity 7: LinkedAccount (P4 Deferred)

**Purpose**: Maps internal user to external platform accounts (WhatsApp, Instagram, Telegram).

**Domain Model** (Java POJO):
```java
package com.chat.domain.model;

/**
 * Responsibility: Maps internal user_id to external platform account identifier.
 * Does NOT: Handle platform authentication (see PlatformAdapter), sync account status (polling feature).
 * Distributed Systems Concept: Integration pattern for multi-platform routing, enables adapter architecture.
 */
public class LinkedAccount {
    private String userId;              // Internal user_id
    private Platform platform;          // WHATSAPP, INSTAGRAM, TELEGRAM
    private String externalId;          // Platform-specific identifier (phone, username, etc.)
    private Instant linkedAt;           // Link timestamp
    
    public enum Platform {
        WHATSAPP,   // phone_number (e.g., "+5511987654321")
        INSTAGRAM,  // username (e.g., "@john_doe")
        TELEGRAM    // user_id (e.g., "123456789")
    }
    
    // Getters, setters
}
```

**MongoDB Schema**:
```javascript
// Collection: linked_accounts
{
  "_id": ObjectId("673ab2c1e4b0a1234567890e"),
  "user_id": "550e8400-e29b-41d4-a716-446655440001",        // Internal user
  "platform": "TELEGRAM",                                    // Enum: WHATSAPP, INSTAGRAM, TELEGRAM
  "external_id": "123456789",                                // Telegram user_id
  "linked_at": ISODate("2025-11-18T13:00:00Z")
}
```

**Indexes**:
```javascript
db.linked_accounts.createIndex({ "user_id": 1, "platform": 1 }, { unique: true, name: "idx_user_platform" });
db.linked_accounts.createIndex({ "platform": 1, "external_id": 1 }, { unique: true, name: "idx_platform_external" });
```

---

## Relationships Diagram

```
┌────────────┐         participants         ┌──────────────────┐
│    User    │◄─────────────────────────────┤  Conversation    │
│            │                               │                  │
│ - user_id  │                               │ - conversation_id│
│ - username │                               │ - type           │
│ - email    │                               │ - participants[] │
└────────────┘                               └──────────────────┘
      │                                              │
      │ sender_id                        conversation_id
      │                                              │
      ▼                                              ▼
┌─────────────────────────────────────────────────────────────┐
│                         Message                             │
│                                                             │
│ - message_id                                                │
│ - conversation_id (FK)                                      │
│ - sender_id (FK)                                            │
│ - message_text OR file_metadata (embedded)                  │
│ - state_history[] (embedded MessageStateTransition)         │
│ - sequence_number                                           │
└─────────────────────────────────────────────────────────────┘
```

---

## MongoDB Write Concerns & Durability

Per Constitution Principle V (Performance with Scalability) and FR-024:

**Write Concern: Majority** (all collections)
```java
@Configuration
public class MongoConfig {
    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory) {
        MongoTemplate template = new MongoTemplate(factory);
        template.setWriteConcern(WriteConcern.MAJORITY); // Ensures durability across replica set
        return template;
    }
}
```

**Rationale**:
- **Durability**: Data written to majority of replica set nodes (survives primary failure)
- **At-least-once delivery**: Combined with RabbitMQ manual acknowledgments (FR-021)
- **Trade-off**: Slightly higher latency (~10-20ms) vs. eventual consistency, acceptable for <100ms p95 target

**Read Preference**: `ReadPreference.PRIMARY_PREFERRED`
- Reads from primary (strong consistency) or secondary if primary unavailable (high availability)

---

## Schema Evolution Strategy

**Versioning Approach**: MongoDB flexible schema enables additive changes without downtime

**Safe Changes** (no migration required):
- Add optional fields: `user.profile_picture_url` (null for existing users)
- Add new collections: `user_sessions` for authentication tracking
- Add indexes: `db.messages.createIndex({ "state_history.state": 1 })`

**Breaking Changes** (require migration script):
- Rename field: `message_text` → `content` (use MongoDB `$rename` aggregation)
- Change type: `participants` from array to object (requires data transformation)
- Delete field: Remove `last_message_preview` (if no longer needed)

**Migration Tool**: Spring Data MongoDB migrations via `mongock` library (deferred to post-MVP)

---

## Summary

**POC Entities**: User, Conversation, Message, MessageStateTransition (embedded)  
**Deferred**: FileMetadata (P2), Webhook (P2), LinkedAccount (P4)

**Key Design Decisions**:
1. **Embedded state_history** (not separate collection) for atomic updates and faster queries
2. **Compound indexes** on (conversation_id, timestamp) for efficient pagination
3. **Write concern: majority** for durability (at-least-once delivery guarantee)
4. **sequence_number** for strict per-conversation ordering (avoids clock skew issues)

**Next Phase**: Generate Protobuf contracts (`contracts/`) mapping these entities to gRPC message types.
