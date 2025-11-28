# Feature Specification: Ubiquitous Messaging Platform

**Feature Branch**: `001-ubiquitous-messaging-platform`  
**Created**: 2025-11-18  
**Status**: Draft  
**Input**: User description: "desenvolver uma plataforma de comunicação ubíqua (API) capaz de rotear mensagens e arquivos entre usuários em múltiplas plataformas (ex.: WhatsApp, Instagram Direct, Messenger, Telegram) e entre clientes internos (web/mobile/CLI). Suporta comunicação privada e em grupo, persistência no servidor, controle de envio/recebimento/leitura, entrega de arquivos até 2 GB e operação em escala (milhões de usuários)."

## Clarifications

### Session 2025-11-26

- Q: Qual deve ser o timeout máximo para operações de **persistência no MongoDB** (ex: salvar mensagem, atualizar conversation)? → A: 5 segundos com retry automático (1 tentativa)
- Q: Qual deve ser o **formato padrão de timestamp** nos logs estruturados JSON? → A: ISO-8601 com timezone UTC (ex: "2025-11-26T14:35:22.123Z")
- Q: Quanto tempo o servidor deve **manter o estado de upload parcial** antes de considerar o upload abandonado e limpar recursos? → A: 24 horas
- Q: Qual deve ser o **limite máximo de participantes** em conversas de grupo no MVP? → A: 100 participantes
- Q: Quando o circuit breaker está **aberto** (60s timeout após falhas), qual deve ser o comportamento ao receber novas requisições para aquele adapter? → A: Rejeitar imediatamente com erro "Adapter unavailable - circuit breaker open"
- Q: Como o sistema deve tratar conversas duplicadas quando Alice envia mensagem para Bob (criando conversation_id: conv-123) e depois Bob envia mensagem para Alice (tentando criar conversation_id: conv-456)? → A: Prevenir duplicatas normalizando participants array (ordenar user_ids alfabeticamente) e criar índice único no MongoDB em (participants_sorted, type)
- Q: O que deve acontecer quando um cliente envia mensagem **sem** conversation_id (para auto-criação) mas **também sem** recipient_id? → A: Rejeitar com erro "recipient_id is required when conversation_id is not provided"
- Q: Mensagens em grupos devem ter campo group_id separado do conversation_id, ou conversation_id é suficiente para identificar o grupo? → A: Usar apenas conversation_id (Conversation entity tem campo type: PRIVATE/GROUP que diferencia)
- Q: O que deve acontecer quando um admin tenta adicionar o 101º membro a um grupo que já tem 100 participantes (limite máximo FR-015a)? → A: Rejeitar com erro "Group member limit reached (100/100). Remove members before adding new ones"
- Q: Como deve ser persistido o delivery state per-recipient em mensagens de grupo (100 membros × múltiplas transições): array embedded no documento da mensagem ou collection separada? → A: Array embedded no Message document (state_history: [{recipient_id, state, timestamp}]) - simples e eficiente para limite de 100 membros no MVP

### Session 2025-11-24

- Q: Adapter failure handling strategy for multi-platform routing - FR-038 states "best-effort with retry" but doesn't define retry parameters. What should be the retry policy? → A: 3 retry attempts with exponential backoff (2s, 4s, 8s) then circuit breaker opens for 60s
- Q: Platform selection behavior on partial failure - FR-036 allows channels: ["whatsapp", "instagram"] but doesn't specify system behavior when one adapter succeeds and another fails. How should the system respond? → A: Return partial success with per-platform results {whatsapp: success, instagram: failed}
- Q: Multiple accounts per platform - FR-035 mentions mapping user_id to external accounts but doesn't specify if a user can have multiple accounts on the same platform (e.g., 2 WhatsApp numbers). How many accounts per platform are allowed? → A: One account per platform per user (simplifies MVP implementation, can extend post-MVP if needed)
- Q: Mock failure simulation - The spec defines that mocks must exist for WhatsApp and Instagram but doesn't specify whether they should simulate realistic failure rates or always succeed. What should the mock behavior be? → A: Simulate realistic failure rates (WhatsApp 95% success, Instagram 90% success, random latency 100-400ms)
- Q: Adapter ID format validation - FR-034 lists adapter interface methods including sendMessage() but doesn't specify whether external ID format validation (phone number for WhatsApp, username for Instagram) is the adapter's responsibility or the service layer's. Where should validation occur? → A: Adapter validates external_id format (WhatsApp checks E.164 phone, Instagram checks @username pattern)

### Session 2025-11-23

- Q: The spec requires "sequence_number" for message ordering within conversations (FR-010), but doesn't specify who generates it. Should the system: → A: Server generates sequence_number atomically on message persistence (MongoDB findAndModify on conversation counter)
- Q: The spec defines DELIVERED state as "reached recipient device" (FR-007), but doesn't specify the exact trigger. When should the system transition a message from SENT to DELIVERED? → A: On successful gRPC ACK from client (client confirms message received in memory)
- Q: FR-031 specifies webhook retry logic with "3 attempts with exponential backoff" but doesn't define the backoff parameters. What should be the retry intervals? → A: 3 attempts with exponential backoff (2^retry seconds: 2s, 4s, 8s)
- Q: The spec doesn't define conversation lifecycle states. What should happen when a user wants to delete/leave a 1:1 private conversation? → A: Mark conversation as deleted, hide from ListConversations, reject new messages with "Conversation deleted" error
- Q: NFR-017 requires structured logging but doesn't specify which message fields can be logged for observability vs privacy. What should be included in logs? → A: Only message_id, conversation_id, sender_id, timestamp, sequence_number (exclude message_text and file content)

### Session 2025-11-22

- Q: The spec assumes users are "pre-authenticated" but doesn't specify the authentication mechanism. Which approach should the system use? → A: OAuth 2.0 with JWT tokens
- Q: For user identity, the spec mentions `user_id`, `username`, and `email` but doesn't clarify the primary identifier. What should be the canonical user identifier throughout the system? → A: UUID-based user_id
- Q: The spec requires observability (structured logging, metrics, tracing, dashboards) but doesn't specify tools. Which observability stack should be used? → A: Prometheus + Grafana + Jaeger
- Q: For group conversations, the spec doesn't specify permission models. Should groups have admin roles with special privileges (add/remove members, change settings)? → A: Yes, with admin roles - creator is admin, can promote others, control membership
- Q: The spec targets "millions of users" but doesn't specify the initial baseline. What should be the MVP performance target for concurrent active users? → A: 10,000 concurrent users
- Q: Should the message broker be RabbitMQ or Kafka? → A: Apache Kafka (better for high-throughput event streaming, partition-based scaling, and log-based message persistence)
- Q: The spec mentions "message_text OR file_metadata" but doesn't define how to handle messages with BOTH text and file. Should the system support combined text+file messages? → A: B
- Q: Auto-creation of conversations when first message is sent - who should be included as participants besides sender_id? → A: recipient_id obrigatório
- Q: Rate limiting defines 100 messages/minute but doesn't specify behavior during legitimate bursts (e.g., copy/paste 150 messages). Should system: reject immediately, queue with backpressure, or something else? → A: Queue up to 200 messages (2x limit) with backpressure, process respecting rate limit
- Q: Group messages track state when different recipients reach different states at different times - how? → A: Per-recipient state in state_history array
- Q: Database partitioning strategy for millions of users - partition by user_id, conversation_id, or timestamp? → A: B (conversation_id hash)

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
- **What happens when a user tries to send a message to a deleted conversation?** System MUST return "Conversation deleted" error and reject message submission.
- **What happens when recipient user_id is invalid?** System MUST return "Recipient not found" error during conversation creation.
- **What happens when a user tries to send messages faster than rate limit?** System MUST queue messages up to 200 (2x the 100 messages/minute limit) with backpressure, processing them at the rate limit pace. Requests exceeding 200 queued messages return "Rate limit queue full" error with retry-after timestamp.
- **What happens when Object Storage (MinIO) is unavailable during file upload?** System MUST return "Storage unavailable" error and allow user to retry upload later.
- **What happens when a message is in SENT state for >24 hours (recipient never comes online)?** Message remains persisted with SENT state indefinitely until recipient connects (no automatic expiration in MVP).
- **What happens when network partitions occur between MongoDB replicas?** System uses MongoDB write concern "majority" to ensure at-least-once delivery guarantee even during partitions (eventual consistency model).
- **What happens when duplicate message_id is submitted within 1 second (race condition)?** MongoDB unique index on message_id enforces deduplication at database level, rejecting duplicates immediately.
- **What happens when a group conversation has 500 members and a message is sent?** System uses Kafka topic partitioning to deliver message to all members asynchronously via consumer groups, tracking delivery state per recipient.
- **What happens when a user requests conversation history for a conversation with 1 million messages?** System enforces pagination (max 100 messages per request) and uses database indexes on (conversation_id, timestamp) for efficient queries.

## Requirements *(mandatory)*

### Functional Requirements

#### Authentication & Authorization (P1 - MVP)

- **FR-001**: System MUST implement OAuth 2.0 authentication with JWT token-based authorization for all API endpoints
- **FR-002**: System MUST validate JWT tokens on every request and extract user_id (UUID) from token claims
- **FR-003**: System MUST issue access tokens with 1-hour expiration and support refresh tokens for client re-authentication

#### Core Messaging (P1 - MVP)

- **FR-004**: System MUST accept text messages via gRPC API with fields: sender_id, recipient_id (required for auto-created conversations), conversation_id, message_text, message_id (UUID)
- **FR-004a**: When conversation_id does not exist, system MUST auto-create PRIVATE conversation with both sender_id and recipient_id as initial participants
- **FR-004b**: System MUST validate that recipient_id is provided when conversation_id is not provided (auto-creation scenario). If both are missing, system MUST reject request with error "recipient_id is required when conversation_id is not provided" (HTTP 400 / gRPC INVALID_ARGUMENT).
- **FR-005**: System MUST persist message metadata (message_id, conversation_id, sender_id, timestamp, state, message_text) in MongoDB
- **FR-006**: System MUST guarantee message idempotency using unique message_id—duplicate submissions MUST be rejected
- **FR-007**: System MUST support message states: SENT (accepted by server), DELIVERED (confirmed by recipient client via gRPC ACK), READ (opened by recipient)
- **FR-008**: System MUST deliver messages in real-time to online users via gRPC bidirectional streaming within 2 seconds
- **FR-009**: System MUST store messages for offline users and deliver when they reconnect (store-and-forward pattern)
- **FR-010**: System MUST preserve message ordering within a conversation using per-conversation sequence numbers (server-generated atomically via MongoDB findAndModify on conversation counter to prevent race conditions)
- **FR-011**: System MUST provide conversation history API with pagination (default 50 messages, max 100 per request)

#### Conversation Management (P1 - MVP)

- **FR-012**: System MUST allow users to create 1:1 private conversations with unique conversation_id
- **FR-012a**: System MUST prevent duplicate private conversations between the same participants by normalizing the participants array (sort user_ids alphabetically) before persistence and enforcing a unique compound index on (participants_sorted, type) in MongoDB. When auto-creating a conversation (FR-004a), system MUST first check for existing conversation using normalized participants to reuse existing conversation_id.
- **FR-013**: System MUST restrict conversation access to participants only (authorization check on all read/write operations)
- **FR-014**: System MUST enforce rate limiting (100 messages/minute per user) to prevent abuse
- **FR-014a**: System MUST queue up to 200 messages per user when burst traffic exceeds rate limit, processing them at 100 messages/minute pace. Requests exceeding 200 queued messages return error with backpressure signal.
- **FR-015**: System MUST provide API to list user's conversations with most recent message preview and timestamp
- **FR-015a**: System MUST allow users to create group conversations with multiple participants (maximum 100 members in MVP to prevent hot partitions and ensure manageable fan-out delivery)
- **FR-016**: System MUST assign creator of group conversation as initial admin with permissions to add/remove members and promote other admins
- **FR-017**: System MUST enforce that only admins can add/remove members from group conversations
- **FR-017a**: System MUST validate member count before adding new members to groups. When group already has 100 members (maximum limit), AddMember operation MUST reject with error "Group member limit reached (100/100). Remove members before adding new ones" (HTTP 400 / gRPC FAILED_PRECONDITION). System MUST include current member count in error response for client visibility.
- **FR-018**: System MUST enforce that only admins can promote other members to admin role or revoke admin privileges
- **FR-019**: Group messages MUST be fan-out delivered to all participants with per-recipient delivery tracking

#### File Handling (P2)

- **FR-020**: System MUST accept file uploads up to 2 GB using chunked upload protocol
- **FR-021**: System MUST implement resumable upload—clients can resume from last successful chunk after network interruption
- **FR-021a**: System MUST retain upload state (file_id, uploaded chunks) for 24 hours after last activity. After expiration, upload is considered abandoned and resources are cleaned up. Client attempting to resume expired upload receives "Upload expired" error.
- **FR-022**: System MUST store files in Object Storage (MinIO) and persist metadata (filename, size, storage_url, conversation_id) in MongoDB
- **FR-023**: System MUST generate pre-signed download URLs valid for 1 hour for authorized users
- **FR-024**: System MUST reject files exceeding 2 GB with clear error message
- **FR-025**: File messages MUST follow same state lifecycle as text messages (SENT → DELIVERED → READ)

#### Delivery Guarantees (P1 - MVP)

- **FR-026**: System MUST provide at-least-once delivery guarantee using Kafka consumer acknowledgments and offset commits
- **FR-027**: System MUST support idempotency to enable effectively-once semantics via message_id deduplication
- **FR-028**: System MUST maintain causal ordering within conversations using conversation_id as Kafka partition key + sequence_number
- **FR-029**: System MUST use MongoDB write concern "majority" for message persistence to ensure durability

#### Webhooks & Events (P2)

- **FR-030**: System MUST expose webhook API for clients to register callback URLs for events (message_delivered, message_read)
- **FR-031**: System MUST publish events to registered webhooks with retry logic (3 attempts with exponential backoff: 2s, 4s, 8s)
- **FR-032**: System MUST provide gRPC streaming API for real-time event subscription (alternative to webhooks)

#### Multi-Platform Routing (P4 - Post-MVP)

- **FR-033**: System MUST support plugin architecture for external platform adapters (WhatsApp, Instagram, Telegram)
- **FR-034**: Adapters MUST implement standard interface: connect(), sendMessage(), sendFile(), webhookHandler(). Each adapter is responsible for validating external_id format according to platform requirements (e.g., WhatsApp validates E.164 phone format, Instagram validates @username pattern)
- **FR-035**: System MUST allow users to link external accounts (user_id mapped to whatsapp_number, instagram_username, etc.). Each user can link ONE account per platform (enforced via unique index on (user_id, platform) in LinkedAccount collection)
- **FR-036**: System MUST route messages to selected platforms when user specifies channels: ["whatsapp", "instagram"] or "all". On partial failures, system returns detailed per-platform results: {whatsapp: {success: true, platformMessageId: "wamid.123"}, instagram: {success: false, error: "connection_timeout"}}
- **FR-037**: System MUST capture incoming messages from external platforms via webhooks and route to internal recipients
- **FR-038**: Adapter failures MUST NOT block internal message delivery—external routing is best-effort with retry (3 attempts with exponential backoff: 2s, 4s, 8s, then circuit breaker opens for 60s to allow adapter recovery)
- **FR-038a**: When circuit breaker is OPEN (during 60s cooldown period), new requests to that adapter MUST be rejected immediately with error "Adapter unavailable - circuit breaker open" without attempting delivery. Circuit breaker automatically transitions to HALF-OPEN after 60s, allowing one test request to verify adapter recovery.
- **FR-039**: Mock adapters (WhatsApp, Instagram) MUST simulate realistic production behavior: WhatsAppMockAdapter achieves 95% success rate with 100-300ms latency, InstagramMockAdapter achieves 90% success rate with 150-400ms latency. Failures simulate common errors: connection_timeout, rate_limit_exceeded, invalid_recipient

### Non-Functional Requirements

#### Scalability (P1 - MVP)

- **NFR-001**: System MUST support minimum 10,000 concurrent active users in MVP with horizontal scaling capability to millions
- **NFR-002**: System MUST handle 1,000 concurrent gRPC connections per service instance
- **NFR-003**: System MUST achieve <100ms p95 latency for text message submission (measured at gRPC endpoint)
- **NFR-004**: System MUST support thousands of messages per second per node with horizontal partitioning via Kafka topic partitions
- **NFR-005**: System MUST be stateless—all session state persisted in MongoDB or distributed cache
- **NFR-006**: System MUST auto-scale horizontally without downtime (add new service instances dynamically)

#### Availability & Reliability (P1 - MVP)

- **NFR-007**: System MUST target 99.9% uptime (max 43 minutes downtime per month)
- **NFR-008**: System MUST implement failover for critical components (Kafka cluster with replication factor 3, MongoDB with replica sets)
- **NFR-009**: System MUST detect node failures via heartbeats and replace failed instances automatically
- **NFR-010**: System MUST use Kafka topic replication and log persistence to prevent message loss during broker restarts
- **NFR-011**: System MUST use MongoDB replica sets (minimum 3 nodes) for data durability

#### Performance (P1 - MVP)

- **NFR-012**: Text message submission MUST complete within <100ms p95 latency
- **NFR-012a**: MongoDB write operations (message persistence, conversation updates) MUST complete within 5 seconds with 1 automatic retry attempt on transient failures
- **NFR-013**: File upload throughput MUST support at least 10 MB/s per connection
- **NFR-014**: Conversation history queries MUST return within <200ms for pages of 50 messages
- **NFR-014a**: MongoDB read operations (queries) MUST timeout after 3 seconds to prevent blocking UX
- **NFR-015**: Message delivery to online users MUST occur within 2 seconds of submission
- **NFR-016**: System MUST handle traffic spikes of 10x normal load without degradation

#### Observability (P2)

- **NFR-017**: System MUST implement structured logging (JSON format) with centralized aggregation via Prometheus. Logs MUST include message_id, conversation_id, sender_id, timestamp, sequence_number for observability but MUST exclude message_text and file content to preserve privacy.
- **NFR-017a**: Structured logs MUST use ISO-8601 format with UTC timezone for all timestamp fields (example: "2025-11-26T14:35:22.123Z"). Required log fields: service_name, log_level, timestamp, trace_id, message_id, conversation_id, sender_id, event_type.
- **NFR-018**: System MUST expose metrics via Prometheus exporters: messages/second, latency percentiles, error rates, Kafka consumer lag, MongoDB connection pool usage
- **NFR-019**: System MUST implement distributed tracing using Jaeger for request flows across services
- **NFR-020**: System MUST provide Grafana dashboards for real-time monitoring of key metrics
- **NFR-021**: System MUST send alerts via Grafana Alerting when SLOs are breached (latency >100ms p95, error rate >1%)

#### API & Extensibility (P3)

- **NFR-022**: System MUST version APIs using Protobuf package versioning (chat_api.v1, chat_api.v2)
- **NFR-023**: System MUST generate API documentation from Protobuf definitions
- **NFR-024**: System MUST provide clear adapter interface documentation for adding new platforms
- **NFR-025**: System MUST support backward-compatible API changes without breaking existing clients

### Key Entities

- **User**: Represents a platform user with unique user_id (UUID, primary identifier). Attributes: user_id (UUID), username (unique, human-readable), email (unique), created_at, linked_accounts (for multi-platform mapping). Authentication via OAuth 2.0 JWT tokens containing user_id claim.
- **Conversation**: Represents a messaging context (1:1 or group). Attributes: conversation_id (UUID), type (PRIVATE/GROUP), participants (list of user_id), admin_user_ids (list of user_id with admin privileges, only for GROUP type), creator_id (user_id of conversation creator), created_at, last_message_at, status (ACTIVE/DELETED - when DELETED, conversation is hidden from ListConversations and new messages are rejected).
- **Message**: Abstract base entity with common attributes: message_id (UUID, unique), conversation_id (references Conversation entity - no separate group_id field needed since Conversation.type differentiates PRIVATE vs GROUP), sender_id, timestamp, state (SENT/DELIVERED/READ), sequence_number (per conversation), state_history (array embedded in Message document containing {recipient_id, state, timestamp} for per-recipient tracking in group messages - optimized for MVP limit of 100 members per group; array size bounded at ~300 entries max: 100 recipients × 3 states). Two concrete subtypes:
  - **TextMessage**: Contains message_text field (string, max 100 KB). Used for text-only messages.
  - **FileMessage**: Contains file_metadata reference. Used for file-only messages (no text caption).
- **MessageState**: Tracks state transitions for a message. Attributes: message_id, state, timestamp, recipient_id (for group messages, tracks per-recipient state in state_history array).
- **FileMetadata**: Represents file attachments. Attributes: file_id (UUID), filename, size_bytes, mime_type, storage_url (MinIO reference), uploaded_at, conversation_id.
- **Webhook**: Represents a registered callback endpoint. Attributes: webhook_id, user_id, callback_url, event_types (list: message_delivered, message_read), active (boolean).
- **LinkedAccount**: Maps internal user to external platform accounts (one account per platform per user). Attributes: user_id, platform (WHATSAPP/INSTAGRAM/TELEGRAM), external_id (phone_number, username, etc.), linked_at. Unique index: (user_id, platform) ensures one account per platform; secondary unique index: (platform, external_id) prevents account duplication.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can send and receive text messages with <2 second delivery latency for online recipients (measured via instrumentation)
- **SC-002**: System handles 10,000 concurrent users sending messages without degradation in latency (<100ms p95 maintained)
- **SC-003**: Message delivery guarantee: 99.9% of messages transition from SENT to DELIVERED within 24 hours (for online or reconnecting users)
- **SC-004**: File uploads up to 500 MB complete successfully with resumable protocol (measured via test suite with simulated network interruptions)
- **SC-005**: Conversation history queries return within <200ms for 50-message pages with 10,000 messages in conversation (database performance test)
- **SC-006**: System achieves 99.9% uptime over 30-day period (monitored via health checks and uptime tracking)
- **SC-007**: Zero data loss during planned failover tests (MongoDB replica set leader election, Kafka broker restart with topic replication)
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
- **A-002**: System uses OAuth 2.0 with JWT tokens for authentication; user identity is based on UUID user_id extracted from JWT claims. This specification covers token validation but NOT the OAuth 2.0 authorization server implementation (assumes separate auth service issues tokens).
- **A-003**: MongoDB, Kafka cluster (with Zookeeper/KRaft), and MinIO infrastructure is deployed and operational (Docker Compose for development, managed services for production).
- **A-004**: File storage quota is NOT enforced in MVP—file size limit is 2 GB per file, but total storage per user is unlimited.
- **A-005**: Message retention is indefinite—no automatic deletion of old messages (archival/retention policy is post-MVP).
- **A-006**: Read receipts (READ state) are opt-in—users can disable read receipts in future iterations, but MVP always sends them.
- **A-007**: Group conversation member limit is 100 participants in MVP (scalability testing for larger groups is post-MVP).
- **A-008**: External platform adapters (WhatsApp, Instagram) require API keys/credentials—this spec assumes those are provisioned externally.
- **A-009**: Network latency between services (gRPC API ↔ Kafka ↔ MongoDB) is <10ms on average (collocated in same data center for MVP).
- **A-010**: Rate limiting is enforced at 100 messages/minute per user to prevent abuse (configurable via environment variables).
- **A-011**: MongoDB collections (messages, conversations) are partitioned using conversation_id hash-based sharding. This ensures conversation locality (all messages for a conversation reside on same shard), simplifies queries, and enables efficient sequence number generation.

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
