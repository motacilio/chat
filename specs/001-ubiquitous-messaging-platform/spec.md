# Feature Specification: Ubiquitous Messaging Platform

**Feature Branch**: `001-ubiquitous-messaging-platform`  
**Created**: 2025-11-18  
**Status**: Draft  
**Input**: User description: "desenvolver uma plataforma de comunicação ubíqua (API) capaz de rotear mensagens e arquivos entre usuários em múltiplas plataformas (ex.: WhatsApp, Instagram Direct, Messenger, Telegram) e entre clientes internos (web/mobile/CLI). Suporta comunicação privada e em grupo, persistência no servidor, controle de envio/recebimento/leitura, entrega de arquivos até 2 GB e operação em escala (milhões de usuários)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Send and Receive Text Messages (Priority: P1) 🎯 **MVP Core**

Users MUST be able to send and receive text messages in real-time between internal clients (web/mobile/CLI), with automatic delivery when recipients come online.

**Why this priority**: This is the fundamental capability of any messaging platform. Without reliable text messaging, no other features matter. This alone delivers immediate value and demonstrates the core architectural patterns (async messaging, persistence, real-time delivery).

**Independent Test**: Deploy only text messaging functionality. Users can create 1:1 conversations, send messages, receive them in real-time if online, or retrieve them when reconnecting. Success is measured by message delivery confirmation (SENT → DELIVERED → READ states).

**Acceptance Scenarios**:

1. **Given** user A and user B are both online, **When** user A sends a text message to user B, **Then** user B receives the message within 2 seconds and message state progresses SENT → DELIVERED → READ
2. **Given** user B is offline, **When** user A sends a text message to user B, **Then** message is persisted with state SENT and delivered automatically when user B comes online
3. **Given** a message with duplicate `message_id` is submitted, **When** the system receives it, **Then** the duplicate is rejected (idempotency guarantee) without creating a second message entry
4. **Given** user A sent 5 messages to user B yesterday, **When** user B requests conversation history, **Then** all 5 messages are returned in chronological order with correct state (SENT/DELIVERED/READ)
5. **Given** 1000 users are sending messages concurrently, **When** messages are submitted via gRPC API, **Then** all messages are accepted within <100ms p95 latency

---

### User Story 2 - Track Message Status Lifecycle (Priority: P1) 🎯 **MVP Core**

Users MUST see real-time status updates for their sent messages (SENT, DELIVERED, READ) to understand message lifecycle and delivery guarantees.

**Why this priority**: Status tracking is essential for user trust in the messaging system. Users need to know their messages were delivered, especially in distributed systems where delivery can be asynchronous. This also demonstrates eventual consistency patterns and event-driven architecture.

**Independent Test**: Send a message and observe state transitions: SENT (accepted by server) → DELIVERED (reached recipient's device) → READ (opened by recipient). Test with online and offline scenarios.

**Acceptance Scenarios**:

1. **Given** user A sends a message, **When** the server accepts it, **Then** message state is immediately set to SENT and user A receives confirmation
2. **Given** message is SENT, **When** it reaches user B's connected device, **Then** state transitions to DELIVERED and user A is notified via webhook/event stream
3. **Given** message is DELIVERED, **When** user B opens the conversation and views the message, **Then** state transitions to READ and user A is notified
4. **Given** user A sent multiple messages over the past hour, **When** user A queries message status history, **Then** all state transitions are returned with timestamps
5. **Given** recipient is offline for 24 hours, **When** they reconnect, **Then** all pending messages are delivered and state transitions are recorded correctly

---

### User Story 3 - Create Private Conversations (Priority: P1) 🎯 **MVP Core**

Users MUST be able to create 1:1 private conversations to exchange messages with any other user in the system.

**Why this priority**: Conversations are the organizational primitive for messages. Without conversation management, messages have no context. This is foundational for the data model (conversation_id for sequencing and partitioning).

**Independent Test**: User A creates/joins a conversation with user B, sends messages, and both users see the shared conversation history. Test isolation: user C cannot access the conversation.

**Acceptance Scenarios**:

1. **Given** user A wants to message user B, **When** user A initiates a conversation with user B, **Then** a unique `conversation_id` is created and both users are participants
2. **Given** a conversation exists between A and B, **When** either user sends a message, **Then** message is associated with the `conversation_id` and visible to both participants
3. **Given** user C is not a participant in conversation between A and B, **When** user C attempts to access it, **Then** request is rejected with authorization error
4. **Given** user A has 10 active conversations, **When** user A requests their conversation list, **Then** all 10 conversations are returned with most recent message preview and timestamp
5. **Given** a conversation has 1000 messages, **When** either participant requests history with pagination (50 messages/page), **Then** messages are returned in order with correct page boundaries

---

### User Story 4 - Upload and Download Files (Priority: P2)

Users MUST be able to send files up to 2 GB through conversations, with files stored externally and metadata persisted in the database.

**Why this priority**: File sharing is a common messaging requirement, but not strictly necessary for MVP. It introduces complexity (chunked upload, resumable protocol, object storage integration) that can be added after text messaging is stable.

**Independent Test**: User uploads a 500 MB file to a conversation. System stores file in MinIO (Object Storage), persists metadata in MongoDB, and provides download URL to recipient. Test resumption after network interruption.

**Acceptance Scenarios**:

1. **Given** user A wants to send a 500 MB file, **When** user A initiates file upload via chunked upload, **Then** file is uploaded in chunks with progress tracking and stored in Object Storage
2. **Given** upload is interrupted at 60%, **When** user A resumes upload, **Then** upload continues from last successful chunk (resumable protocol)
3. **Given** file upload completes, **When** system stores file, **Then** metadata (filename, size, conversation_id, object_storage_url) is persisted in MongoDB and file message is created with state SENT
4. **Given** recipient receives file message, **When** recipient requests file download, **Then** system returns pre-signed URL valid for 1 hour with download access
5. **Given** file is 2.1 GB, **When** user attempts upload, **Then** request is rejected with error "File exceeds maximum size of 2 GB"

---

### User Story 5 - Create Group Conversations (Priority: P3)

Users MUST be able to create group conversations with multiple participants (n members) to enable team communication.

**Why this priority**: Groups add complexity (membership management, permission models, notification fan-out) that isn't essential for MVP. 1:1 conversations demonstrate the core patterns, and groups can be layered on top after the foundation is solid.

**Independent Test**: User A creates a group with users B, C, D. Any member sends a message, and all others receive it. Test member addition/removal and message visibility rules.

**Acceptance Scenarios**:

1. **Given** user A wants to create a group chat, **When** user A initiates group with users B, C, D, **Then** a group `conversation_id` is created with all 4 users as participants
2. **Given** a group conversation exists, **When** any member sends a message, **Then** message is delivered to all other online members and persisted for offline members
3. **Given** user E is added to the group, **When** user E joins, **Then** user E can see messages sent AFTER joining but NOT historical messages (privacy-preserving default)
4. **Given** user B leaves the group, **When** user B departs, **Then** user B can no longer send or receive messages in that group
5. **Given** a group has 50 members, **When** a message is sent, **Then** message is fan-out delivered to all 50 members with delivery state tracked per recipient

---

### User Story 6 - Multi-Platform Message Routing (Priority: P4)

Users MUST be able to send messages that are routed to external platforms (WhatsApp, Instagram, Telegram) when recipients have linked accounts.

**Why this priority**: Multi-platform routing is the "ubiquitous" aspect but introduces significant complexity (external API integration, adapter architecture, account mapping, webhook handling). This should be tackled AFTER the internal messaging platform is fully functional and scalable. MVP focuses on internal clients only.

**Independent Test**: User A (internal client) sends message to user B who has linked WhatsApp account. Message is routed through WhatsApp adapter and delivered via WhatsApp API. Test bidirectional routing (WhatsApp → internal).

**Acceptance Scenarios**:

1. **Given** user B has linked their WhatsApp account, **When** user A (internal client) sends message to user B selecting "whatsapp" channel, **Then** message is routed through WhatsApp adapter and delivered via WhatsApp Business API
2. **Given** user C has multiple linked accounts (Instagram + Telegram), **When** user A sends message with channels=["instagram", "telegram"], **Then** message is delivered to both platforms simultaneously
3. **Given** user D receives message via WhatsApp, **When** user D replies on WhatsApp, **Then** reply is captured via webhook, routed back to internal platform, and delivered to original sender
4. **Given** WhatsApp adapter fails (API timeout), **When** message cannot be delivered, **Then** message remains in internal platform with state SENT and retry is scheduled with exponential backoff
5. **Given** a new adapter for Signal is needed, **When** developer implements adapter interface (connect, sendMessage, sendFile, webhookHandler), **Then** Signal messages can be routed without modifying core platform code

---

### Edge Cases

- **What happens when a message exceeds maximum text size?** System MUST reject messages >100 KB with error "Message exceeds maximum size" (prevents abuse and ensures performance).
- **What happens when conversation_id does not exist?** System MUST return "Conversation not found" error and reject message submission.
- **What happens when recipient user_id is invalid?** System MUST return "Recipient not found" error during conversation creation.
- **What happens when a user tries to send messages faster than rate limit?** System MUST enforce rate limit (e.g., 100 messages/minute per user) and return "Rate limit exceeded" error with retry-after timestamp.
- **What happens when Object Storage (MinIO) is unavailable during file upload?** System MUST return "Storage unavailable" error and allow user to retry upload later.
- **What happens when a message is in SENT state for >24 hours (recipient never comes online)?** Message remains persisted with SENT state indefinitely until recipient connects (no automatic expiration in MVP).
- **What happens when network partitions occur between MongoDB replicas?** System uses MongoDB write concern "majority" to ensure at-least-once delivery guarantee even during partitions (eventual consistency model).
- **What happens when duplicate message_id is submitted within 1 second (race condition)?** MongoDB unique index on message_id enforces deduplication at database level, rejecting duplicates immediately.
- **What happens when a group conversation has 500 members and a message is sent?** System uses RabbitMQ fan-out exchange to deliver message to all members asynchronously, tracking delivery state per recipient.
- **What happens when a user requests conversation history for a conversation with 1 million messages?** System enforces pagination (max 100 messages per request) and uses database indexes on (conversation_id, timestamp) for efficient queries.

## Requirements *(mandatory)*

### Functional Requirements

#### Core Messaging (P1 - MVP)

- **FR-001**: System MUST accept text messages via gRPC API with fields: sender_id, recipient_id, conversation_id, message_text, message_id (UUID)
- **FR-002**: System MUST persist message metadata (message_id, conversation_id, sender_id, timestamp, state, message_text) in MongoDB
- **FR-003**: System MUST guarantee message idempotency using unique message_id—duplicate submissions MUST be rejected
- **FR-004**: System MUST support message states: SENT (accepted by server), DELIVERED (reached recipient device), READ (opened by recipient)
- **FR-005**: System MUST deliver messages in real-time to online users via gRPC bidirectional streaming within 2 seconds
- **FR-006**: System MUST store messages for offline users and deliver when they reconnect (store-and-forward pattern)
- **FR-007**: System MUST preserve message ordering within a conversation using per-conversation sequence numbers
- **FR-008**: System MUST provide conversation history API with pagination (default 50 messages, max 100 per request)

#### Conversation Management (P1 - MVP)

- **FR-009**: System MUST allow users to create 1:1 private conversations with unique conversation_id
- **FR-010**: System MUST restrict conversation access to participants only (authorization check on all read/write operations)
- **FR-011**: System MUST provide API to list user's conversations with most recent message preview and timestamp
- **FR-012**: System MUST allow users to create group conversations with multiple participants (n members)
- **FR-013**: System MUST support adding/removing members from group conversations with permission checks
- **FR-014**: Group messages MUST be fan-out delivered to all participants with per-recipient delivery tracking

#### File Handling (P2)

- **FR-015**: System MUST accept file uploads up to 2 GB using chunked upload protocol
- **FR-016**: System MUST implement resumable upload—clients can resume from last successful chunk after network interruption
- **FR-017**: System MUST store files in Object Storage (MinIO) and persist metadata (filename, size, storage_url, conversation_id) in MongoDB
- **FR-018**: System MUST generate pre-signed download URLs valid for 1 hour for authorized users
- **FR-019**: System MUST reject files exceeding 2 GB with clear error message
- **FR-020**: File messages MUST follow same state lifecycle as text messages (SENT → DELIVERED → READ)

#### Delivery Guarantees (P1 - MVP)

- **FR-021**: System MUST provide at-least-once delivery guarantee using RabbitMQ message acknowledgments
- **FR-022**: System MUST support idempotency to enable effectively-once semantics via message_id deduplication
- **FR-023**: System MUST maintain causal ordering within conversations using conversation_id + sequence_number
- **FR-024**: System MUST use MongoDB write concern "majority" for message persistence to ensure durability

#### Webhooks & Events (P2)

- **FR-025**: System MUST expose webhook API for clients to register callback URLs for events (message_delivered, message_read)
- **FR-026**: System MUST publish events to registered webhooks with retry logic (3 attempts with exponential backoff)
- **FR-027**: System MUST provide gRPC streaming API for real-time event subscription (alternative to webhooks)

#### Multi-Platform Routing (P4 - Post-MVP)

- **FR-028**: System MUST support plugin architecture for external platform adapters (WhatsApp, Instagram, Telegram)
- **FR-029**: Adapters MUST implement standard interface: connect(), sendMessage(), sendFile(), webhookHandler()
- **FR-030**: System MUST allow users to link external accounts (user_id mapped to whatsapp_number, instagram_username, etc.)
- **FR-031**: System MUST route messages to selected platforms when user specifies channels: ["whatsapp", "instagram"] or "all"
- **FR-032**: System MUST capture incoming messages from external platforms via webhooks and route to internal recipients
- **FR-033**: Adapter failures MUST NOT block internal message delivery—external routing is best-effort with retry

### Non-Functional Requirements

#### Scalability (P1 - MVP)

- **NFR-001**: System MUST support millions of active users with horizontal scaling
- **NFR-002**: System MUST handle 1,000 concurrent gRPC connections per service instance
- **NFR-003**: System MUST achieve <100ms p95 latency for text message submission (measured at gRPC endpoint)
- **NFR-004**: System MUST support thousands of messages per second per node with horizontal partitioning via RabbitMQ
- **NFR-005**: System MUST be stateless—all session state persisted in MongoDB or distributed cache
- **NFR-006**: System MUST auto-scale horizontally without downtime (add new service instances dynamically)

#### Availability & Reliability (P1 - MVP)

- **NFR-007**: System MUST target 99.9% uptime (max 43 minutes downtime per month)
- **NFR-008**: System MUST implement failover for critical components (RabbitMQ, MongoDB with replica sets)
- **NFR-009**: System MUST detect node failures via heartbeats and replace failed instances automatically
- **NFR-010**: System MUST use RabbitMQ persistent queues to prevent message loss during broker restarts
- **NFR-011**: System MUST use MongoDB replica sets (minimum 3 nodes) for data durability

#### Performance (P1 - MVP)

- **NFR-012**: Text message submission MUST complete within <100ms p95 latency
- **NFR-013**: File upload throughput MUST support at least 10 MB/s per connection
- **NFR-014**: Conversation history queries MUST return within <200ms for pages of 50 messages
- **NFR-015**: Message delivery to online users MUST occur within 2 seconds of submission
- **NFR-016**: System MUST handle traffic spikes of 10x normal load without degradation

#### Observability (P2)

- **NFR-017**: System MUST implement structured logging (JSON format) with centralized aggregation
- **NFR-018**: System MUST expose metrics: messages/second, latency percentiles, error rates, RabbitMQ queue depth, MongoDB connection pool usage
- **NFR-019**: System MUST implement distributed tracing for request flows across services
- **NFR-020**: System MUST provide dashboards for real-time monitoring of key metrics
- **NFR-021**: System MUST send alerts when SLOs are breached (latency >100ms p95, error rate >1%)

#### API & Extensibility (P3)

- **NFR-022**: System MUST version APIs using Protobuf package versioning (chat_api.v1, chat_api.v2)
- **NFR-023**: System MUST generate API documentation from Protobuf definitions
- **NFR-024**: System MUST provide clear adapter interface documentation for adding new platforms
- **NFR-025**: System MUST support backward-compatible API changes without breaking existing clients

### Key Entities

- **User**: Represents a platform user with unique user_id. Attributes: username, email, created_at, linked_accounts (for multi-platform mapping).
- **Conversation**: Represents a messaging context (1:1 or group). Attributes: conversation_id (UUID), type (PRIVATE/GROUP), participants (list of user_id), created_at, last_message_at.
- **Message**: Represents a single message in a conversation. Attributes: message_id (UUID, unique), conversation_id, sender_id, message_text OR file_metadata, timestamp, state (SENT/DELIVERED/READ), sequence_number (per conversation).
- **MessageState**: Tracks state transitions for a message. Attributes: message_id, state, timestamp, recipient_id (for group messages, tracks per-recipient state).
- **FileMetadata**: Represents file attachments. Attributes: file_id (UUID), filename, size_bytes, mime_type, storage_url (MinIO reference), uploaded_at, conversation_id.
- **Webhook**: Represents a registered callback endpoint. Attributes: webhook_id, user_id, callback_url, event_types (list: message_delivered, message_read), active (boolean).
- **LinkedAccount**: Maps internal user to external platform accounts. Attributes: user_id, platform (WHATSAPP/INSTAGRAM/TELEGRAM), external_id (phone_number, username, etc.), linked_at.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can send and receive text messages with <2 second delivery latency for online recipients (measured via instrumentation)
- **SC-002**: System handles 1,000 concurrent users sending messages without degradation in latency (<100ms p95 maintained)
- **SC-003**: Message delivery guarantee: 99.9% of messages transition from SENT to DELIVERED within 24 hours (for online or reconnecting users)
- **SC-004**: File uploads up to 500 MB complete successfully with resumable protocol (measured via test suite with simulated network interruptions)
- **SC-005**: Conversation history queries return within <200ms for 50-message pages with 10,000 messages in conversation (database performance test)
- **SC-006**: System achieves 99.9% uptime over 30-day period (monitored via health checks and uptime tracking)
- **SC-007**: Zero data loss during planned failover tests (MongoDB replica set leader election, RabbitMQ node restart)
- **SC-008**: API documentation is auto-generated from Protobuf definitions and accessible via gRPC Server Reflection
- **SC-009**: MVP delivers P1 user stories (text messaging, status tracking, 1:1 conversations) within 8 weeks with passing integration tests
- **SC-010**: System scales horizontally—adding a 2nd service instance doubles throughput (measured via load testing)

## API Contract Examples *(informative)*

This section provides **informative examples** of the public REST API endpoints. These are **not prescriptive**—the actual implementation may use gRPC, REST, or a hybrid approach. The examples illustrate expected request/response contracts for key operations.

### Authentication

**Endpoint**: `POST /auth/token`

**Purpose**: Obtain access token for API requests

**Request**:
```json
{
  "client_id": "web-client-abc123",
  "client_secret": "secret_xyz789"
}
```

**Response** (200 OK):
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

**Error** (401 Unauthorized):
```json
{
  "error": "invalid_client",
  "error_description": "Invalid client credentials"
}
```

---

### Create Conversation

**Endpoint**: `POST /v1/conversations`

**Purpose**: Create a new conversation (1:1 private or group)

**Request** (Private Conversation):
```json
{
  "type": "private",
  "members": ["userA", "userB"],
  "metadata": {
    "title": "Project Discussion"
  }
}
```

**Request** (Group Conversation):
```json
{
  "type": "group",
  "members": ["userA", "userB", "userC", "userD"],
  "metadata": {
    "title": "Team Chat",
    "description": "Weekly sync channel"
  }
}
```

**Response** (201 Created):
```json
{
  "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "private",
  "members": ["userA", "userB"],
  "created_at": "2025-11-18T10:30:00Z",
  "metadata": {
    "title": "Project Discussion"
  }
}
```

**Error** (400 Bad Request):
```json
{
  "error": "invalid_member",
  "error_description": "User userB not found"
}
```

---

### List Conversations

**Endpoint**: `GET /v1/conversations?user_id={user_id}&limit=20&offset=0`

**Purpose**: Retrieve user's conversation list with pagination

**Response** (200 OK):
```json
{
  "conversations": [
    {
      "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
      "type": "private",
      "members": ["userA", "userB"],
      "last_message": {
        "message_id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "from": "userB",
        "preview": "Hey, can we schedule a meeting?",
        "timestamp": "2025-11-18T14:22:00Z"
      },
      "unread_count": 3
    },
    {
      "conversation_id": "660f9500-f39c-52e5-b827-557766551111",
      "type": "group",
      "members": ["userA", "userC", "userD"],
      "metadata": {
        "title": "Team Chat"
      },
      "last_message": {
        "message_id": "8d0f7780-8536-51f0-c05c-f18ae2g01bf8",
        "from": "userC",
        "preview": "Agenda for tomorrow's standup",
        "timestamp": "2025-11-18T13:15:00Z"
      },
      "unread_count": 0
    }
  ],
  "pagination": {
    "total": 12,
    "limit": 20,
    "offset": 0,
    "has_more": false
  }
}
```

---

### Get Conversation Messages

**Endpoint**: `GET /v1/conversations/{conversation_id}/messages?since={timestamp}&limit=50`

**Purpose**: Retrieve message history with optional timestamp filter

**Example**: `GET /v1/conversations/550e8400-e29b-41d4-a716-446655440000/messages?limit=50`

**Response** (200 OK):
```json
{
  "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
  "messages": [
    {
      "message_id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
      "from": "userA",
      "to": ["userB"],
      "payload": {
        "type": "text",
        "text": "Hello! How are you?"
      },
      "state": "READ",
      "timestamp": "2025-11-18T10:35:00Z",
      "sequence_number": 1
    },
    {
      "message_id": "8d0f7780-8536-51f0-c05c-f18ae2g01bf8",
      "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
      "from": "userB",
      "to": ["userA"],
      "payload": {
        "type": "text",
        "text": "I'm good, thanks for asking!"
      },
      "state": "DELIVERED",
      "timestamp": "2025-11-18T10:36:15Z",
      "sequence_number": 2
    }
  ],
  "pagination": {
    "total": 47,
    "limit": 50,
    "has_more": false
  }
}
```

**Error** (404 Not Found):
```json
{
  "error": "conversation_not_found",
  "error_description": "Conversation 550e8400-e29b-41d4-a716-446655440000 does not exist"
}
```

---

### Send Text Message

**Endpoint**: `POST /v1/messages`

**Purpose**: Send a text message to a conversation

**Request** (Text Message - Internal Only):
```json
{
  "message_id": "9e1g8891-9647-62g1-d16d-g29bf3h12cg9",
  "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
  "from": "userA",
  "to": ["userB"],
  "payload": {
    "type": "text",
    "text": "Let's sync up tomorrow at 3 PM"
  },
  "metadata": {
    "priority": "normal"
  }
}
```

**Request** (Multi-Platform Routing - P4):
```json
{
  "message_id": "af2h9902-0758-73h2-e27e-h30cg4i23dh0",
  "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
  "from": "userA",
  "to": ["userB"],
  "channels": ["whatsapp", "instagram"],
  "payload": {
    "type": "text",
    "text": "Check out this link!"
  },
  "metadata": {
    "priority": "high"
  }
}
```

**Request** (Broadcast to All Channels):
```json
{
  "message_id": "bg3i0013-1869-84i3-f38f-i41dh5j34ei1",
  "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
  "from": "userA",
  "to": ["userB"],
  "channels": ["all"],
  "payload": {
    "type": "text",
    "text": "Important announcement!"
  }
}
```

**Response** (202 Accepted):
```json
{
  "status": "accepted",
  "message_id": "9e1g8891-9647-62g1-d16d-g29bf3h12cg9",
  "state": "SENT",
  "timestamp": "2025-11-18T15:42:00Z"
}
```

**Error** (400 Bad Request - Duplicate):
```json
{
  "error": "duplicate_message",
  "error_description": "Message with message_id 9e1g8891-9647-62g1-d16d-g29bf3h12cg9 already exists"
}
```

**Error** (413 Payload Too Large):
```json
{
  "error": "message_too_large",
  "error_description": "Message text exceeds maximum size of 100 KB"
}
```

**Error** (429 Too Many Requests):
```json
{
  "error": "rate_limit_exceeded",
  "error_description": "Maximum 100 messages per minute exceeded",
  "retry_after": 45
}
```

---

### File Upload (Resumable Protocol)

#### Step 1: Initiate Upload

**Endpoint**: `POST /v1/files/initiate`

**Purpose**: Request pre-signed upload URL and file_id

**Request**:
```json
{
  "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
  "filename": "project-presentation.pdf",
  "size_bytes": 524288000,
  "mime_type": "application/pdf",
  "checksum_md5": "e10adc3949ba59abbe56e057f20f883e"
}
```

**Response** (200 OK):
```json
{
  "file_id": "ch4j1124-2970-95j4-g49g-j52ei6k45fj2",
  "upload_url": "https://minio.example.com/uploads/ch4j1124-2970-95j4-g49g-j52ei6k45fj2?X-Amz-Signature=...",
  "upload_expires_at": "2025-11-18T16:42:00Z",
  "chunk_size": 5242880,
  "resumable": true
}
```

**Error** (413 Payload Too Large):
```json
{
  "error": "file_too_large",
  "error_description": "File size 2147483648 bytes exceeds maximum of 2 GB"
}
```

---

#### Step 2: Upload Chunks to Pre-Signed URL

**Endpoint**: `PUT {upload_url}` (MinIO/S3-compatible)

**Purpose**: Upload file chunks using resumable protocol

**Headers**:
```
Content-Type: application/octet-stream
Content-Range: bytes 0-5242879/524288000
X-Upload-ID: ch4j1124-2970-95j4-g49g-j52ei6k45fj2
```

**Body**: Binary file chunk (5 MB)

**Response** (200 OK):
```json
{
  "uploaded_bytes": 5242880,
  "total_bytes": 524288000,
  "progress_percent": 1.0
}
```

---

#### Step 3: Complete Upload

**Endpoint**: `POST /v1/files/complete`

**Purpose**: Finalize file upload and create file message

**Request**:
```json
{
  "file_id": "ch4j1124-2970-95j4-g49g-j52ei6k45fj2",
  "checksum_md5": "e10adc3949ba59abbe56e057f20f883e",
  "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
  "from": "userA",
  "to": ["userB"]
}
```

**Response** (200 OK):
```json
{
  "message_id": "di5k2235-30a1-a6k5-h5ah-k63fj7l56gk3",
  "file_id": "ch4j1124-2970-95j4-g49g-j52ei6k45fj2",
  "state": "SENT",
  "download_url": "https://api.example.com/v1/files/ch4j1124-2970-95j4-g49g-j52ei6k45fj2/download",
  "download_expires_at": "2025-11-18T17:42:00Z"
}
```

**Error** (400 Bad Request):
```json
{
  "error": "checksum_mismatch",
  "error_description": "Uploaded file checksum does not match provided MD5"
}
```

---

### Download File

**Endpoint**: `GET /v1/files/{file_id}/download`

**Purpose**: Get pre-signed download URL

**Response** (302 Found):
```
Location: https://minio.example.com/files/ch4j1124-2970-95j4-g49g-j52ei6k45fj2?X-Amz-Signature=...
```

**Response** (200 OK - Direct Download):
```json
{
  "download_url": "https://minio.example.com/files/ch4j1124-2970-95j4-g49g-j52ei6k45fj2?X-Amz-Signature=...",
  "expires_at": "2025-11-18T16:42:00Z",
  "filename": "project-presentation.pdf",
  "size_bytes": 524288000
}
```

**Error** (404 Not Found):
```json
{
  "error": "file_not_found",
  "error_description": "File ch4j1124-2970-95j4-g49g-j52ei6k45fj2 does not exist"
}
```

**Error** (403 Forbidden):
```json
{
  "error": "unauthorized",
  "error_description": "User is not a participant in conversation"
}
```

---

### Register Webhook

**Endpoint**: `POST /v1/webhooks`

**Purpose**: Register callback URL for message delivery/read events

**Request**:
```json
{
  "user_id": "userA",
  "callback_url": "https://app.example.com/webhooks/messages",
  "event_types": ["message_delivered", "message_read"],
  "secret": "webhook_secret_abc123"
}
```

**Response** (201 Created):
```json
{
  "webhook_id": "ej6l3346-41b2-b7l6-i6bi-l74gk8m67hl4",
  "user_id": "userA",
  "callback_url": "https://app.example.com/webhooks/messages",
  "event_types": ["message_delivered", "message_read"],
  "active": true,
  "created_at": "2025-11-18T10:30:00Z"
}
```

---

### Webhook Callback Payload (Delivery Event)

**Endpoint**: `POST {callback_url}` (Client-provided)

**Purpose**: Notify client of message delivery state change

**Headers**:
```
Content-Type: application/json
X-Webhook-Signature: sha256=5d41402abc4b2a76b9719d911017c592
X-Event-Type: message_delivered
```

**Payload**:
```json
{
  "event_id": "fk7m4457-52c3-c8m7-j7cj-m85hl9n78im5",
  "event_type": "message_delivered",
  "timestamp": "2025-11-18T15:45:00Z",
  "data": {
    "message_id": "9e1g8891-9647-62g1-d16d-g29bf3h12cg9",
    "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "DELIVERED",
    "recipient_id": "userB",
    "delivered_at": "2025-11-18T15:45:00Z"
  }
}
```

---

### Webhook Callback Payload (Read Event)

**Payload**:
```json
{
  "event_id": "gl8n5568-63d4-d9n8-k8dk-n96im0o89jn6",
  "event_type": "message_read",
  "timestamp": "2025-11-18T15:47:00Z",
  "data": {
    "message_id": "9e1g8891-9647-62g1-d16d-g29bf3h12cg9",
    "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "READ",
    "recipient_id": "userB",
    "read_at": "2025-11-18T15:47:00Z"
  }
}
```

---

### Notes on API Design

1. **Idempotency**: All `POST` requests include client-generated UUIDs (`message_id`, `file_id`) to enable safe retries.

2. **Authentication**: All endpoints require `Authorization: Bearer {access_token}` header (obtained from `/auth/token`).

3. **Pagination**: List endpoints support `limit` and `offset` query parameters; responses include `pagination` metadata.

4. **Error Format**: Consistent error response structure with `error` code and human-readable `error_description`.

5. **Webhook Security**: Webhooks include `X-Webhook-Signature` header (HMAC-SHA256 of payload + secret) for verification.

6. **Rate Limiting**: Enforced via HTTP 429 responses with `retry_after` field indicating seconds until retry is allowed.

7. **Versioning**: API version included in path (`/v1/`) to support backward-compatible changes in future versions (`/v2/`).

## Assumptions

- **A-001**: MVP focuses on internal clients (web/mobile/CLI) only; multi-platform routing (WhatsApp, Instagram, Telegram) is deferred to post-MVP phases.
- **A-002**: Users are pre-authenticated; this specification does NOT cover user authentication/authorization implementation (assumes OAuth2/JWT tokens are provided by separate auth service).
- **A-003**: MongoDB, RabbitMQ, and MinIO infrastructure is deployed and operational (Docker Compose for development, managed services for production).
- **A-004**: File storage quota is NOT enforced in MVP—file size limit is 2 GB per file, but total storage per user is unlimited.
- **A-005**: Message retention is indefinite—no automatic deletion of old messages (archival/retention policy is post-MVP).
- **A-006**: Read receipts (READ state) are opt-in—users can disable read receipts in future iterations, but MVP always sends them.
- **A-007**: Group conversation member limit is 100 participants in MVP (scalability testing for larger groups is post-MVP).
- **A-008**: External platform adapters (WhatsApp, Instagram) require API keys/credentials—this spec assumes those are provisioned externally.
- **A-009**: Network latency between services (gRPC API ↔ RabbitMQ ↔ MongoDB) is <10ms on average (collocated in same data center for MVP).
- **A-010**: Rate limiting is enforced at 100 messages/minute per user to prevent abuse (configurable via environment variables).

## Out of Scope (Post-MVP)

- **Voice/video calling**: MVP is text and file messaging only
- **End-to-end encryption**: Messages are encrypted in transit (TLS) and at rest (MongoDB encryption), but E2E encryption is deferred
- **Message editing/deletion**: MVP does not support editing or deleting sent messages
- **Search functionality**: Full-text search across message history is post-MVP
- **Rich media previews**: Link previews, embedded images, GIF support are post-MVP
- **Typing indicators**: Real-time "user is typing" indicators are post-MVP
- **Message reactions**: Emoji reactions to messages are post-MVP
- **User presence (online/offline status)**: Presence indicators are post-MVP
- **Push notifications**: Mobile push notifications (APNs, FCM) are post-MVP—MVP uses webhooks and gRPC streaming only
- **Multi-device sync**: Syncing conversation state across multiple devices is post-MVP

## Notes

This specification prioritizes **incremental delivery** aligned with the constitution's educational principles:

1. **P1 (MVP Core)**: Text messaging, status tracking, and 1:1 conversations demonstrate the fundamental distributed systems patterns (async messaging, persistence, eventual consistency, idempotency).

2. **P2 (Early Enhancement)**: File uploads and webhooks add complexity but build on the established foundation.

3. **P3/P4 (Future Phases)**: Group conversations and multi-platform routing introduce significant additional complexity (fan-out delivery, adapter architecture, external API integration) and should only be tackled after the core platform is stable and scalable.

This phased approach ensures students learn concepts incrementally, with each phase delivering working software that can be tested, deployed, and demonstrated independently.
