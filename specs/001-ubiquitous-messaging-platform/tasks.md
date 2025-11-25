# Tasks: Ubiquitous Messaging Platform

**Input**: Design documents from `/specs/001-ubiquitous-messaging-platform/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: NOT requested in feature specification - TDD approach not specified. Test tasks are EXCLUDED from this breakdown.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [ID] [P?] [Story?] Description`

- **Checkbox**: `- [ ]` (markdown task checkbox)
- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Based on plan.md project structure:
- Source code: `src/main/java/com/chat/`
- Protobuf: `src/main/proto/`
- Resources: `src/main/resources/`
- Configuration: Root directory (`pom.xml`, `docker-compose.yml`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure required before any implementation can begin

- [ ] T001 Configure Maven dependencies in pom.xml (Spring Boot 3.2.5, gRPC 1.64.0, Protobuf 3.25.3, Spring Kafka, Spring Data MongoDB, Spring Security OAuth2)
- [ ] T002 [P] Configure Protobuf compiler plugin in pom.xml (protobuf-maven-plugin for Java generation from .proto files)
- [ ] T003 [P] Create Spring Boot main application class in src/main/java/com/chat/ChatApiApplication.java
- [ ] T004 [P] Configure application.yml with profile-based settings (dev, docker, test) in src/main/resources/
- [ ] T005 Create Docker Compose configuration in docker-compose.yml (Kafka, Zookeeper, MongoDB, Kafka UI per quickstart.md)
- [ ] T006 [P] Configure Dockerfile for Chat API service container
- [ ] T007 [P] Create .gitignore with Java/Maven/IntelliJ patterns
- [ ] T008 [P] Configure logging patterns and levels in src/main/resources/logback-spring.xml (JSON structured logging per NFR-017)

---

## Phase 2: *-* (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Infrastructure Configuration

- [ ] T009 Configure MongoDB connection in src/main/java/com/chat/config/MongoConfig.java (replica set, write concern majority per FR-029)
- [ ] T010 [P] Configure Kafka producer in src/main/java/com/chat/config/KafkaProducerConfig.java (JSON serialization, acks=all per research.md Decision 2)
- [ ] T011 [P] Configure Kafka consumer in src/main/java/com/chat/config/KafkaConsumerConfig.java (manual offset commits, consumer group settings per research.md Decision 2)
- [ ] T012 [P] Configure gRPC server in src/main/java/com/chat/config/GrpcServerConfig.java (port 9090, enable reflection per quickstart.md)
- [ ] T013 [P] Configure Spring Security OAuth2 resource server in src/main/java/com/chat/config/SecurityConfig.java (JWT validation, extract user_id from claims)

### Domain Models (Foundational Entities)

- [ ] T014 [P] Create User entity in src/main/java/com/chat/model/User.java (user_id UUID, username, email, created_at per data-model.md Entity 1)
- [ ] T015 [P] Create Conversation entity in src/main/java/com/chat/model/Conversation.java (conversation_id UUID, type enum, participants list, timestamps per data-model.md Entity 2)
- [ ] T016 [P] Create Message entity in src/main/java/com/chat/model/Message.java (message_id UUID, conversation_id, sender_id, message_text, timestamp, sequence_number per data-model.md Entity 3)
- [ ] T017 [P] Create MessageStateTransition embedded entity in src/main/java/com/chat/model/MessageStateTransition.java (state enum, timestamp, recipient_id per data-model.md Entity 4)
- [ ] T018 [P] Create MessageStatus enum in src/main/java/com/chat/model/MessageStatus.java (SENT, DELIVERED, READ per FR-007)
- [ ] T019 [P] Create ConversationType enum in src/main/java/com/chat/model/ConversationType.java (PRIVATE, GROUP per FR-012)

### MongoDB Repositories

- [ ] T020 [P] Create UserRepository in src/main/java/com/chat/repository/UserRepository.java (findByUserId, findByUsername, findByEmail methods with indexes per data-model.md Entity 1)
- [ ] T021 [P] Create ConversationRepository in src/main/java/com/chat/repository/ConversationRepository.java (findByConversationId, findByParticipantsContainingOrderByLastMessageAtDesc with compound indexes per data-model.md Entity 2)
- [ ] T022 [P] Create MessageRepository in src/main/java/com/chat/repository/MessageRepository.java (findByMessageId, findByConversationIdOrderByTimestampDesc, findByConversationIdOrderBySequenceNumber with indexes per data-model.md Entity 3)

### Protobuf Contracts

- [ ] T023 [P] Copy common_types.proto to src/main/proto/common_types.proto (MessageStatus, ConversationType, UserInfo, MessageStateTransition, PaginationInfo per contracts/)
- [ ] T024 [P] Copy chat_service.proto to src/main/proto/chat_service.proto (SendMessage, StreamMessages, GetMessageStatus, MarkMessageAsRead RPCs per contracts/)
- [ ] T025 [P] Copy conversation_service.proto to src/main/proto/conversation_service.proto (CreateConversation, ListConversations, GetConversation, GetConversationHistory RPCs per contracts/)
- [ ] T026 Compile Protobuf definitions to generate Java classes (mvn clean compile generates gRPC stubs in target/generated-sources/protobuf/)

### Error Handling & Utilities

- [ ] T027 [P] Create global gRPC exception handler in src/main/java/com/chat/grpc/GlobalExceptionHandler.java (maps domain exceptions to gRPC Status codes with ErrorDetail)
- [ ] T028 [P] Create UUID validator utility in src/main/java/com/chat/util/UuidValidator.java (validates message_id, conversation_id, user_id format per FR-006)
- [ ] T029 [P] Create authentication interceptor in src/main/java/com/chat/security/AuthenticationInterceptor.java (extracts user_id from JWT metadata, validates token per FR-002)

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Send and Receive Text Messages (Priority: P1) 🎯 MVP

**Goal**: Users can send text messages via gRPC API, messages are persisted in MongoDB, and delivered asynchronously via Kafka workers

**Independent Test**: Send message via ChatService.SendMessage RPC, verify message persisted in MongoDB with state SENT, confirm message_id idempotency rejection works

### Implementation for User Story 1

- [X] T030 [P] [US1] Create MessageService in src/main/java/com/chat/service/MessageService.java (submitMessage method with idempotency check, Kafka publish, sequence number generation per FR-004, FR-006)
- [X] T031 [P] [US1] Create ConversationService basic methods in src/main/java/com/chat/service/ConversationService.java (getConversation, validateParticipant for authorization per FR-013)
- [X] T032 [US1] Implement ChatServiceImpl.SendMessage in src/main/java/com/chat/grpc/ChatServiceImpl.java (validate request, call MessageService.submitMessage, map to SendMessageResponse per chat_service.proto)
- [X] T033 [US1] Create Kafka message event DTO in src/main/java/com/chat/worker/dto/MessageEventDto.java (message_id, conversation_id, sender_id, message_text for Kafka topic serialization)
- [X] T034 [US1] Implement MessageDeliveryWorker Kafka consumer in src/main/java/com/chat/worker/MessageDeliveryWorker.java (listen to message-events topic, persist to MongoDB, manual offset commit per research.md Decision 2)
- [X] T035 [US1] Add validation for message_text size in MessageService (reject messages >100 KB per edge case spec)
- [X] T036 [US1] Add logging for message submission in MessageService (log message_id, conversation_id, sender_id at INFO level per NFR-017)
- [X] T037 [US1] Add logging for message persistence in MessageDeliveryWorker (log successful persistence and Kafka offset commit per NFR-017)

**Checkpoint**: At this point, User Story 1 should be fully functional - messages can be sent via gRPC and persisted in MongoDB

---

## Phase 4: User Story 2 - Track Message Status Lifecycle (Priority: P1) 🎯 MVP

**Goal**: Users can query message status history and mark messages as read, with state transitions tracked (SENT → DELIVERED → READ)

**Independent Test**: Send message (US1), query GetMessageStatus to verify SENT state, call MarkMessageAsRead, re-query to verify READ state transition with timestamp

### Implementation for User Story 2

- [X] T038 [P] [US2] Add state transition methods to MessageService in src/main/java/com/chat/service/MessageService.java (addStateTransition method, getCurrentStatus helper per data-model.md Entity 4)
- [X] T039 [P] [US2] Create MessageStateUpdateWorker Kafka consumer in src/main/java/com/chat/worker/MessageStateUpdateWorker.java (listen to state-update-events topic, update Message.state_history array in MongoDB)
- [X] T040 [US2] Implement ChatServiceImpl.GetMessageStatus in src/main/java/com/chat/grpc/ChatServiceImpl.java (query MessageRepository, map state_history to GetMessageStatusResponse per chat_service.proto)
- [X] T041 [US2] Implement ChatServiceImpl.MarkMessageAsRead in src/main/java/com/chat/grpc/ChatServiceImpl.java (validate user is participant, publish READ state event to Kafka, return MarkMessageAsReadResponse per chat_service.proto)
- [X] T042 [US2] Add authorization check in MessageService.markAsRead (verify user_id is in conversation.participants per FR-013)
- [X] T043 [US2] Add logging for state transitions in MessageStateUpdateWorker (log message_id, old_status, new_status, recipient_id per NFR-017)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work - messages can be sent, persisted, and status lifecycle tracked independently

---

## Phase 5: User Story 3 - Create Private Conversations (Priority: P1) 🎯 MVP

**Goal**: Users can create 1:1 private conversations and query conversation list with message previews

**Independent Test**: Create conversation via ConversationService.CreateConversation with 2 participants, send message (US1), list conversations to verify preview appears

### Implementation for User Story 3

- [X] T044 [P] [US3] Add createConversation method to ConversationService in src/main/java/com/chat/service/ConversationService.java (validate exactly 2 participants for PRIVATE type, generate conversation_id UUID per FR-012)
- [X] T045 [P] [US3] Add listConversations method to ConversationService in src/main/java/com/chat/service/ConversationService.java (query by participants containing user_id, sort by last_message_at descending, paginate per FR-014)
- [X] T046 [P] [US3] Add updateLastMessage method to ConversationService in src/main/java/com/chat/service/ConversationService.java (update last_message_at and last_message_preview when new message arrives)
- [X] T047 [US3] Implement ConversationServiceImpl.CreateConversation in src/main/java/com/chat/grpc/ConversationServiceImpl.java (validate request, call ConversationService.createConversation, map to CreateConversationResponse per conversation_service.proto)
- [X] T048 [US3] Implement ConversationServiceImpl.ListConversations in src/main/java/com/chat/grpc/ConversationServiceImpl.java (validate pagination limits max 100, call ConversationService.listConversations, map to ListConversationsResponse with PaginationInfo per conversation_service.proto)
- [X] T049 [US3] Implement ConversationServiceImpl.GetConversation in src/main/java/com/chat/grpc/ConversationServiceImpl.java (validate user is participant, call ConversationRepository, map to GetConversationResponse per conversation_service.proto)
- [X] T050 [US3] Implement ConversationServiceImpl.GetConversationHistory in src/main/java/com/chat/grpc/ConversationServiceImpl.java (validate user is participant, query MessageRepository with pagination, map to GetConversationHistoryResponse per conversation_service.proto and FR-011)
- [X] T051 [US3] Update MessageDeliveryWorker to call ConversationService.updateLastMessage after persisting message (maintain conversation preview in src/main/java/com/chat/worker/MessageDeliveryWorker.java)
- [X] T052 [US3] Add logging for conversation creation in ConversationService (log conversation_id, type, participant_ids per NFR-017)

**Checkpoint**: All MVP user stories (US1, US2, US3) are now independently functional - complete text messaging platform with status tracking and conversation management

---

## Phase 6: User Story 4 - Upload and Download Files (Priority: P2) ⚠️ DEFERRED

**Note**: File upload/download is P2 priority and DEFERRED from MVP per plan.md Phase 2. These tasks are listed for post-MVP implementation.

**Goal**: Users can upload files up to 2 GB with resumable protocol and download via pre-signed URLs

**Independent Test**: Initiate file upload, upload chunks with interruption, resume from offset, complete upload, download file via pre-signed URL

### Implementation for User Story 4 (DEFERRED)

- [ ] T053 [P] [US4] Create FileMetadata embedded entity in src/main/java/com/chat/model/FileMetadata.java (file_id UUID, filename, size_bytes, mime_type, storage_url per data-model.md Entity 5)
- [ ] T054 [P] [US4] Update Message entity to support FileMetadata as alternative to message_text (add oneof pattern equivalent in Java)
- [ ] T055 [P] [US4] Configure MinIO client in src/main/java/com/chat/config/MinioConfig.java (connection settings, bucket configuration per quickstart.md)
- [ ] T056 [P] [US4] Create FileStorageService in src/main/java/com/chat/service/FileStorageService.java (initiate upload, store chunk, complete upload, generate pre-signed download URL using tus protocol per research.md Decision 4)
- [ ] T057 [US4] Implement file upload endpoints (POST /v1/files/initiate, PATCH /upload_url, POST /v1/files/complete per spec.md API contracts)
- [ ] T058 [US4] Implement file download endpoint (GET /v1/files/{file_id}/download returns pre-signed URL valid 1 hour per FR-023)
- [ ] T059 [US4] Add file size validation in FileStorageService.initiateUpload (reject files >2 GB per FR-024)
- [ ] T060 [US4] Add checksum validation in FileStorageService.completeUpload (MD5 verification per quickstart.md example)

---

## Phase 7: User Story 5 - Create Group Conversations (Priority: P3) ⚠️ DEFERRED

**Note**: Group conversations are P3 priority and DEFERRED from MVP per plan.md. These tasks are listed for post-MVP implementation.

**Goal**: Users can create group conversations with multiple participants and manage membership with admin roles

**Independent Test**: Create group with 4 participants, send message to group, verify all participants receive message, add/remove member as admin

### Implementation for User Story 5 (DEFERRED)

- [ ] T061 [P] [US5] Update Conversation entity in src/main/java/com/chat/model/Conversation.java (add admin_user_ids list, creator_id field per data-model.md Entity 2)
- [ ] T062 [P] [US5] Update ConversationService.createConversation to support GROUP type (validate n participants, set creator as initial admin per FR-016)
- [ ] T063 [P] [US5] Add addMember method to ConversationService in src/main/java/com/chat/service/ConversationService.java (validate requester is admin, update participants list per FR-017)
- [ ] T064 [P] [US5] Add removeMember method to ConversationService in src/main/java/com/chat/service/ConversationService.java (validate requester is admin, update participants list per FR-017)
- [ ] T065 [P] [US5] Add promoteToAdmin method to ConversationService in src/main/java/com/chat/service/ConversationService.java (validate requester is admin, update admin_user_ids list per FR-018)
- [ ] T066 [US5] Implement ConversationServiceImpl.AddMember in src/main/java/com/chat/grpc/ConversationServiceImpl.java (per conversation_service.proto)
- [ ] T067 [US5] Implement ConversationServiceImpl.RemoveMember in src/main/java/com/chat/grpc/ConversationServiceImpl.java (per conversation_service.proto)
- [ ] T068 [US5] Update MessageDeliveryWorker to support fan-out delivery for group messages (track per-recipient DELIVERED state per FR-019)
- [ ] T069 [US5] Add authorization checks for group operations (only admins can add/remove members per FR-017)

---

## Phase 8: User Story 6 - Multi-Platform Message Routing (Priority: P4) ⚠️ DEFERRED

**Note**: Multi-platform routing is P4 priority and DEFERRED from MVP per plan.md. These tasks are listed for post-MVP implementation.

**Goal**: Messages can be routed to external platforms (Telegram real, WhatsApp/Instagram mocked) via adapter pattern

**Independent Test**: Link Telegram account, send message to user with Telegram linked, verify message delivered via Telegram Bot API, receive reply from Telegram webhook

### Implementation for User Story 6 (DEFERRED)

- [X] T070 [P] [US6] Create LinkedAccount entity in src/main/java/com/chat/model/LinkedAccount.java (user_id, platform enum, external_id per data-model.md Entity 7)
- [X] T071 [P] [US6] Create LinkedAccountRepository in src/main/java/com/chat/repository/LinkedAccountRepository.java (findByUserIdAndPlatform, findByPlatformAndExternalId with indexes)
- [X] T072 [P] [US6] Create PlatformAdapter interface in src/main/java/com/chat/adapter/PlatformAdapter.java (connect, sendMessage, sendFile, webhookHandler methods per research.md Decision 5)
- [ ] T073 [P] [US6] Implement TelegramBotAdapter in src/main/java/com/chat/adapter/TelegramBotAdapter.java (real integration using telegrambots library per research.md Decision 5)
- [X] T074 [P] [US6] Implement WhatsAppMockAdapter in src/main/java/com/chat/adapter/WhatsAppMockAdapter.java (95% success rate simulation, random 100-300ms latency via Thread.sleep, throw connection_timeout/rate_limit_exceeded/invalid_recipient errors per FR-039, validate E.164 phone format per Session 2025-11-24 Q5)
- [X] T075 [P] [US6] Implement InstagramMockAdapter in src/main/java/com/chat/adapter/InstagramMockAdapter.java (90% success rate simulation, random 150-400ms latency via Thread.sleep, throw connection_timeout/rate_limit_exceeded/invalid_recipient errors per FR-039, validate @username pattern per Session 2025-11-24 Q5)
- [X] T076 [P] [US6] Create AdapterRegistry in src/main/java/com/chat/service/AdapterRegistry.java (Spring component registry with @Qualifier injection for platform selection)
- [ ] T077 [US6] Create PlatformRoutingService in src/main/java/com/chat/service/PlatformRoutingService.java (route message to selected platforms, handle adapter failures with circuit breaker per FR-038)
- [ ] T078 [US6] Update MessageService to support platform routing (add channels parameter, call PlatformRoutingService per FR-036)
- [ ] T079 [US6] Implement webhook endpoint for Telegram in src/main/java/com/chat/controller/TelegramWebhookController.java (parse incoming messages, route to internal recipients per FR-037)
- [ ] T080 [US6] Add webhook signature validation in TelegramWebhookController (verify X-Telegram-Bot-Api-Secret-Token per research.md Decision 5)

---

## Phase 9: Observability & Monitoring (Priority: P2)

**Purpose**: Implement comprehensive observability stack (Prometheus, Grafana, Jaeger) for production monitoring

**Note**: Observability is NFR-017 through NFR-021, implemented after MVP core functionality is stable

- [ ] T081 [P] Configure Prometheus metrics exporter in src/main/java/com/chat/config/MetricsConfig.java (Spring Boot Actuator with Micrometer)
- [ ] T082 [P] Add custom metrics in MessageService (messages_sent_total counter, message_latency_seconds histogram per NFR-018)
- [ ] T083 [P] Add custom metrics in MessageDeliveryWorker (kafka_consumer_lag gauge, messages_processed_total counter per NFR-018)
- [ ] T084 [P] Configure Jaeger distributed tracing in src/main/java/com/chat/config/TracingConfig.java (OpenTelemetry integration for request flows per NFR-019)
- [ ] T085 [P] Add Jaeger tracing spans in gRPC services (trace message submission → Kafka publish → MongoDB persistence flow)
- [ ] T086 Create Grafana dashboard configuration in docs/observability/grafana-dashboard.json (messages/second, latency p95, error rates, Kafka consumer lag per NFR-020)
- [ ] T087 [P] Configure Grafana alerting rules in docs/observability/grafana-alerts.yml (alert when p95 latency >100ms or error rate >1% per NFR-021)
- [ ] T088 Update docker-compose.yml to add Prometheus, Grafana, Jaeger containers (per quickstart.md extension)

---

## Phase 10: Real-Time Streaming (Priority: P1 Enhancement)

**Purpose**: Implement gRPC bidirectional streaming for real-time message delivery (enhances US1/US2)

**Note**: Streaming is FR-008 for real-time delivery to online users, complements async Kafka-based delivery

- [X] T089 [P] Create StreamingService in src/main/java/com/chat/service/StreamingService.java (manage active user streams, subscribe/unsubscribe methods)
- [X] T090 [US1] Implement ChatServiceImpl.StreamMessages in src/main/java/com/chat/grpc/ChatServiceImpl.java (server-side streaming, push MessageEvent to subscribers per chat_service.proto)
- [X] T091 [US1] Update MessageDeliveryWorker to notify StreamingService when message persisted (push to online users via stream)
- [X] T092 [US2] Update MessageStateUpdateWorker to notify StreamingService of state changes (push StatusUpdateEvent to sender per chat_service.proto)
- [X] T093 [US1] Add connection lifecycle management in StreamingService (handle stream errors, client disconnections, reconnection logic)
- [X] T094 [US1] Add logging for stream events in StreamingService (log user_id, stream_start, stream_end, messages_pushed per NFR-017)

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories and final MVP refinement

- [ ] T095 [P] Create comprehensive README.md in repository root (project overview, quickstart, architecture links, contribution guidelines)
- [ ] T096 [P] Create architecture documentation in docs/architecture/system-overview.md (high-level diagram, component responsibilities, message flow)
- [ ] T097 [P] Create deployment runbook in docs/runbooks/deployment.md (Kubernetes deployment steps, environment variables, health checks)
- [ ] T098 [P] Create troubleshooting guide in docs/runbooks/troubleshooting.md (common errors, log analysis, Kafka/MongoDB debug commands)
- [ ] T099 Add JavaDoc comments to all public methods in service layer (explain distributed systems concepts per Constitution Principle I)
- [ ] T100 Add README files to each package (src/main/java/com/chat/grpc/README.md explains gRPC patterns, src/main/java/com/chat/worker/README.md explains Kafka consumer patterns)
- [ ] T101 Review and optimize MongoDB indexes (analyze query patterns, add missing indexes, remove unused indexes per NFR-014)
- [ ] T102 Review and optimize Kafka consumer configurations (tune max.poll.records, fetch.min.bytes for throughput per NFR-004)
- [ ] T103 Run performance benchmarks (measure p95 latency under 10,000 concurrent users load, verify <100ms target per NFR-003)
- [ ] T104 Validate quickstart.md accuracy (follow quickstart steps on clean environment, update any outdated commands)
- [ ] T105 Add security hardening (rate limiting per user_id 100 msg/min per edge case, input sanitization, SQL injection prevention)
- [ ] T106 Create GitHub Actions CI/CD pipeline in .github/workflows/ci.yml (build, compile protobuf, run tests, Docker build)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion (T001-T008) - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational phase (T009-T029) - MVP core
- **User Story 2 (Phase 4)**: Depends on Foundational phase (T009-T029) - Can run parallel with US1 except T041 depends on T032
- **User Story 3 (Phase 5)**: Depends on Foundational phase (T009-T029) - T051 depends on T034 (MessageDeliveryWorker)
- **Real-Time Streaming (Phase 10)**: Depends on US1 completion (T030-T037) - T091 depends on T034
- **Observability (Phase 9)**: Can start after Foundational - no dependencies on user stories
- **User Stories 4-6 (Phases 6-8)**: DEFERRED to post-MVP - dependencies documented within each phase
- **Polish (Phase 11)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: No dependencies on other stories - can start immediately after Foundational
- **User Story 2 (P2)**: No dependencies on other stories - MarkMessageAsRead (T041) can integrate with SendMessage (T032) but operates independently
- **User Story 3 (P3)**: Soft dependency on US1 for preview updates (T051 depends on T034) but conversation CRUD works independently
- **User Story 4 (P2 - DEFERRED)**: No dependencies on messaging stories - file storage is independent subsystem
- **User Story 5 (P3 - DEFERRED)**: Depends on US3 completion (extends Conversation entity and ConversationService)
- **User Story 6 (P4 - DEFERRED)**: Depends on US1/US2 completion (routes messages created by ChatService)

### Within Each User Story

**User Story 1** (T030-T037):
1. T030, T031 (services) → run in parallel
2. T032 (gRPC impl) → depends on T030
3. T033 (DTO) → run in parallel with services
4. T034 (Kafka worker) → depends on T030, T033
5. T035-T037 (validation, logging) → run in parallel after core implementation

**User Story 2** (T038-T043):
1. T038, T039 (state service methods, worker) → run in parallel
2. T040, T041 (gRPC impls) → run in parallel, depend on T038
3. T042, T043 (authorization, logging) → run in parallel after core implementation

**User Story 3** (T044-T052):
1. T044, T045, T046 (service methods) → run in parallel
2. T047, T048, T049, T050 (gRPC impls) → run in parallel, depend on T044-T046
3. T051 (integration with worker) → depends on T034 (US1 worker), T046
4. T052 (logging) → run in parallel with gRPC impls

### Parallel Opportunities

- **Setup phase**: T002, T003, T004, T006, T007, T008 can run in parallel (different files)
- **Foundational - Config**: T009-T013 can run in parallel (different config files)
- **Foundational - Models**: T014-T019 can run in parallel (different entity files)
- **Foundational - Repositories**: T020-T022 can run in parallel (different repository files)
- **Foundational - Protobuf**: T023-T025 can run in parallel (different proto files)
- **Foundational - Utilities**: T027-T029 can run in parallel (different utility files)
- **User Stories 1, 2, 3**: After Foundational completes, these can run in parallel with separate developers (independent subsystems)
- **Observability phase**: T081-T085 can run in parallel (metrics, tracing are independent)
- **Polish phase**: T095-T098 (documentation) can run in parallel

---

## Parallel Example: User Story 1

After Foundational phase (T009-T029) completes:

```bash
# Developer A: Core services
Task T030: Create MessageService
Task T031: Create ConversationService basic methods

# Developer B: Integration layer (can run simultaneously)
Task T033: Create Kafka DTO

# After T030 completes:
# Developer A: gRPC implementation
Task T032: Implement ChatServiceImpl.SendMessage (depends on T030)

# Developer C: Async processing
Task T034: Implement MessageDeliveryWorker (depends on T030, T033)

# Final polish (can run in parallel):
Task T035: Add message size validation
Task T036: Add logging for message submission
Task T037: Add logging for worker persistence
```

---

## Implementation Strategy

### MVP First (User Stories 1-3 Only - 8 Week Timeline)

**Weeks 1-2**: Setup + Foundational
1. Complete Phase 1: Setup (T001-T008) → ~3 days
2. Complete Phase 2: Foundational (T009-T029) → ~7 days
3. **CHECKPOINT**: Foundation validated - Maven builds, Docker Compose runs, Protobuf compiles

**Weeks 3-4**: User Story 1 (Text Messaging)
1. Complete Phase 3: US1 (T030-T037) → ~8 days
2. **STOP and VALIDATE**: Send message via grpcurl, verify MongoDB persistence, test idempotency
3. Deploy to staging if ready

**Weeks 5-6**: User Story 2 (Status Tracking)
1. Complete Phase 4: US2 (T038-T043) → ~6 days
2. **STOP and VALIDATE**: Query status, mark as read, verify state transitions
3. Integration test US1 + US2 together

**Weeks 7-8**: User Story 3 (Conversations) + Real-Time Streaming
1. Complete Phase 5: US3 (T044-T052) → ~7 days
2. Complete Phase 10: Streaming (T089-T094) → ~3 days (enhances US1/US2)
3. **FINAL VALIDATION**: Full E2E test - create conversation, send message, stream delivery, track status
4. Deploy MVP to production

**MVP Deliverables** (8 weeks):
- ✅ Text messaging with persistence (US1)
- ✅ Status tracking SENT → DELIVERED → READ (US2)
- ✅ 1:1 private conversations (US3)
- ✅ Real-time streaming for online users (Phase 10)
- ✅ 10,000 concurrent users, <100ms p95 latency, 99.9% uptime targets met

### Post-MVP Incremental Delivery

**Iteration 1** (2 weeks): Observability
1. Complete Phase 9: Observability (T081-T088)
2. Deploy Prometheus + Grafana + Jaeger to production
3. Validate metrics collection and alerting

**Iteration 2** (3 weeks): File Uploads (P2)
1. Complete Phase 6: US4 (T053-T060)
2. Test with 500 MB file upload/resume/download
3. Deploy file storage feature

**Iteration 3** (2 weeks): Group Conversations (P3)
1. Complete Phase 7: US5 (T061-T069)
2. Test with 50-member group, message fan-out delivery
3. Deploy group messaging

**Iteration 4** (4 weeks): Multi-Platform Routing (P4)
1. Complete Phase 8: US6 (T070-T080)
2. Test Telegram integration with real bot, validate webhook
3. Deploy multi-platform feature (Telegram real, WhatsApp/Instagram mocked)

**Iteration 5** (1 week): Polish
1. Complete Phase 11: Polish (T095-T106)
2. Final performance optimization, documentation updates
3. Production hardening

### Parallel Team Strategy (3 Developers - 6 Week MVP)

**Week 1**: Setup + Foundational (All developers together)
- Dev A: T001-T004 (Maven, Spring Boot, config)
- Dev B: T005-T008 (Docker, logging)
- Dev C: T014-T019 (Domain models)
- Together: T009-T013 (infrastructure config), T020-T029 (repositories, protobuf, utilities)

**Weeks 2-3**: Parallel User Story Implementation
- Dev A: Phase 3 - User Story 1 (T030-T037) → Text messaging
- Dev B: Phase 4 - User Story 2 (T038-T043) → Status tracking
- Dev C: Phase 5 - User Story 3 (T044-T052) → Conversations
- Each developer owns end-to-end implementation of their story

**Week 4**: Integration + Real-Time Streaming
- Dev A: Phase 10 - Streaming (T089-T094) → Real-time delivery
- Dev B: Integration testing US1 + US2 + US3 together
- Dev C: Performance testing, observability setup (Phase 9: T081-T085)

**Weeks 5-6**: Polish + Production Readiness
- All devs: Phase 11 (T095-T106) → Documentation, runbooks, CI/CD
- Final E2E testing, load testing (10,000 concurrent users)
- Production deployment preparation

---

## Summary

**Total Tasks**: 106 tasks across 11 phases
- **MVP Core** (Phases 1-5, 10): 71 tasks → 8 weeks solo / 6 weeks with 3 developers
- **Deferred** (Phases 6-8): 28 tasks → Post-MVP iterations
- **Observability** (Phase 9): 8 tasks → Post-MVP iteration 1
- **Polish** (Phase 11): 12 tasks → Final iteration

**Task Breakdown by User Story**:
- Setup: 8 tasks
- Foundational: 21 tasks (BLOCKS all stories)
- User Story 1 (P1): 8 tasks → MVP
- User Story 2 (P1): 6 tasks → MVP
- User Story 3 (P1): 9 tasks → MVP
- Real-Time Streaming: 6 tasks → MVP enhancement
- User Story 4 (P2): 8 tasks → DEFERRED
- User Story 5 (P3): 9 tasks → DEFERRED
- User Story 6 (P4): 11 tasks → DEFERRED
- Observability: 8 tasks → Post-MVP
- Polish: 12 tasks → Final phase

**Parallelization Potential**:
- Setup phase: 6 of 8 tasks can run in parallel
- Foundational phase: 18 of 21 tasks can run in parallel (within sub-phases)
- User Stories 1-3: Can run in parallel after Foundational completes (3 developers → 6 weeks instead of 8)
- Within each story: 50%+ tasks can run in parallel (services, gRPC impls, logging)

**Independent Test Criteria**:
- User Story 1: Send message via gRPC → verify MongoDB persistence → verify idempotency rejection
- User Story 2: Query message status → mark as read → verify state transition with timestamp
- User Story 3: Create conversation → send message → list conversations with preview
- Combined MVP: Create conversation → send message → stream to recipient → mark as read → query history

**Suggested MVP Scope** (8 weeks solo):
- ✅ Phase 1: Setup
- ✅ Phase 2: Foundational
- ✅ Phase 3: User Story 1 (Text Messaging)
- ✅ Phase 4: User Story 2 (Status Tracking)
- ✅ Phase 5: User Story 3 (Private Conversations)
- ✅ Phase 10: Real-Time Streaming
- ⏸️ Phases 6-8: DEFER to post-MVP
- ⏸️ Phase 9: Observability → Post-MVP iteration 1
- ⏸️ Phase 11: Polish → Final phase

**Format Validation**: ✅ All tasks follow checklist format (checkbox, ID, optional [P] for parallel, optional [Story] label, file paths included)
