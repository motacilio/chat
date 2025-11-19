# Implementation Plan: Ubiquitous Messaging Platform

**Branch**: `001-ubiquitous-messaging-platform` | **Date**: 2025-11-18 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-ubiquitous-messaging-platform/spec.md`

**Note**: This plan follows Constitution v1.1.0 Principle VIII (Incremental Delivery & Pragmatic Scope), implementing POC-first development with layer-by-layer validation.

## Summary

Build a ubiquitous messaging platform (API) capable of routing messages and files between users across multiple platforms (WhatsApp, Instagram, Telegram) and internal clients (web/mobile/CLI). System supports private/group conversations, message state tracking (SENT/DELIVERED/READ), file uploads up to 2 GB, and operation at scale (millions of users).

**POC Scope (Phase 1 - Week 1-2)**: Validate core message flow with minimal local components: gRPC API → RabbitMQ Worker → MongoDB persistence. Demonstrates text messaging with state tracking for P1 user stories only (no Kafka, no MinIO, no external platform integrations).

**Technical Approach**: Hexagonal architecture with Spring Boot microservices, gRPC for inter-service communication, RabbitMQ for async message processing, MongoDB for persistence. POC proves message delivery patterns before adding distributed components (Kafka) or external integrations (Telegram Bot API for P4).

## Technical Context

**Language/Version**: Java 17 (LTS - per Constitution educational standards)  
**Primary Dependencies**: Spring Boot 3.2.5, gRPC 1.64.0 + Protobuf 3.25.3, Spring AMQP (RabbitMQ), Spring Data MongoDB  
**Storage**: MongoDB 7.0+ (replica set for durability, write concern: majority)  
**Testing**: JUnit 5 + Testcontainers (RabbitMQ, MongoDB - no mocked infrastructure per Constitution Principle IV)  
**Target Platform**: Linux server (Docker containers, Kubernetes for horizontal scaling)  
**Project Type**: Single Java project with hexagonal architecture (domain logic isolated from infrastructure)  
**Performance Goals**: <100ms p95 latency for message submission, 1000 concurrent gRPC connections per instance  
**Constraints**: <2 second message delivery for online users, 99.9% uptime, stateless services (horizontal scaling)  
**Scale/Scope**: MVP targets 10k daily active users; architecture designed for millions via horizontal scaling

**POC Technology Subset** (Phase 1):
- ✅ Java 17 + Spring Boot 3.2.5 (core framework)
- ✅ gRPC 1.64.0 for API endpoints (validates strongly-typed contracts)
- ✅ RabbitMQ via Spring AMQP (async message processing, at-least-once delivery)
- ✅ MongoDB (message persistence, conversation management)
- ✅ Testcontainers (integration testing with real infrastructure)
- ✅ Docker Compose (local orchestration of 4 services: API, Worker, RabbitMQ, MongoDB)

**Deferred to Post-POC** (per Constitution Principle VIII):
- ❌ Apache Kafka (add ONLY after RabbitMQ message flow validated - high-throughput event streaming)
- ❌ MinIO (add ONLY after file metadata persistence proven - S3-compatible object storage for P2 file uploads)
- ❌ Telegram Bot API (ONE real integration for P4 multi-platform routing, deferred until internal messaging stable)
- ❌ WhatsApp/Instagram adapters (mocked implementations only - commercial APIs impractical for educational project)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Design Validation

- ✅ **Principle I (Educational Code Quality)**: Plan emphasizes code as learning resource. Each service will demonstrate one distributed systems pattern (async messaging, eventual consistency, idempotency). JavaDoc headers will explain patterns explicitly (e.g., "Event-Driven Architecture: RabbitMQ consumer with at-least-once delivery").

- ✅ **Principle II (Explicit Responsibility Declaration)**: Hexagonal architecture enforces clear boundaries. Each service will have `Responsibility:` JavaDoc statement. Example: `MessagePublisherService` - "Responsibility: Publishes validated messages to RabbitMQ exchange. Does NOT: validate payloads (see MessageValidator), persist to DB (see MessageRepository)".

- ✅ **Principle III (Descriptive Naming)**: No abbreviations except HTTP/gRPC/AMQP/UUID. Method names follow verb-noun pattern: `validateMessagePayload()`, `enqueueToRabbitMQ()`, `fetchConversationHistory()`. Class suffixes indicate role: `*Service`, `*Repository`, `*Controller`, `*Worker`.

- ✅ **Principle IV (Test-Driven Development)**: Red-Green-Refactor mandatory. Integration tests use Testcontainers for RabbitMQ + MongoDB (no mocks). Contract tests for all Protobuf definitions. Minimum 80% coverage, 100% for critical paths (message delivery, state transitions).

- ✅ **Principle V (Performance with Scalability)**: Services are stateless (session state in MongoDB). Async I/O via Spring WebFlux for non-blocking gRPC streaming. RabbitMQ connection pools sized for load. Performance tests validate <100ms p95 latency. MongoDB indexes documented for all queries.

- ✅ **Principle VI (Clear Architecture & Transparency)**: Technology choices documented in `docs/architecture/decisions.md` (gRPC for typed contracts, RabbitMQ for async patterns, MongoDB for flexible schemas). Architecture diagrams required in `docs/architecture/` (system overview, message flow, deployment topology). No custom serialization (Protobuf for services).

- ✅ **Principle VII (Documentation-First Culture)**: Feature documentation in `docs/features/message-delivery.md` with use case, implementation approach, testing strategy, performance notes. API docs auto-generated from Protobuf via gRPC Server Reflection. Code comments explain "why" (business logic), not "what" (self-documenting code).

- ✅ **Principle VIII (Incremental Delivery & Pragmatic Scope)**: **POC-FIRST APPROACH ENFORCED**. Phase 1 (Week 1-2) delivers minimal local flow: gRPC API → RabbitMQ Worker → MongoDB with <5 Docker Compose services. POC validates text messaging (P1 user stories) before adding Kafka/MinIO. External integrations limited to ONE real platform (Telegram Bot API for P4, deferred to post-POC). Standard libraries mandated: tus protocol or S3 multipart for resumable uploads (P2), springdoc-openapi for REST docs.

### Violations Requiring Justification

**NONE** - All principles satisfied. POC-first approach per Principle VIII mitigates architectural risk.

---

### Post-Phase 1 Validation

*GATE: Re-check constitution compliance after design artifacts generated (research.md, data-model.md, contracts/, quickstart.md).*

**Validation Date**: 2025-11-18 (after Phase 0 research + Phase 1 design complete)

- ✅ **Principle VIII (POC-First) - Artifacts Evidence**:
  - `quickstart.md` Docker Compose config: **4 services only** (RabbitMQ, MongoDB, MongoDB Init, Chat API) - passes <5 service limit ✅
  - No Kafka containers in docker-compose.yml (deferred to post-POC) ✅
  - No MinIO containers in docker-compose.yml (deferred to P2 file uploads) ✅
  - `research.md` Decision 4: **tus protocol** documented for resumable uploads (standard library, not custom implementation) ✅
  - `research.md` Decision 5: **Telegram Bot API** chosen as ONE real integration (P4 deferred), WhatsApp/Instagram mocked ✅
  - Project structure shows `adapter/storage/MinIOFileStorage.java` marked as "P2 - deferred" ✅

- ✅ **Principle VI (Transparency) - Documentation Evidence**:
  - `research.md`: **6 architectural decisions** documented with alternatives considered (gRPC vs REST/GraphQL/WebSocket, RabbitMQ vs Kafka/SQS/Google Pub/Sub, MongoDB schemas vs relational, tus vs S3 multipart, Telegram vs WhatsApp/Signal, OAuth2 deferred with justification) ✅
  - Each decision has "Alternatives Considered" section with 2-4 options evaluated ✅

- ✅ **Principle VII (Documentation-First) - Artifact Structure**:
  - `data-model.md`: **7 entities** documented with purpose, domain model, MongoDB schema, indexes, relationships ✅
  - `contracts/`: **3 Protobuf files** with comprehensive comments (common_types.proto defines enums/shared messages, chat_service.proto documents 4 RPCs, conversation_service.proto documents 6 RPCs) ✅
  - `contracts/README.md`: Maven configuration, contract testing strategy, versioning policy documented ✅
  - `quickstart.md`: Complete setup guide (prerequisites, 5-step quickstart, Docker Compose config, testing examples, troubleshooting) ✅

- ✅ **Principle IV (TDD) - Testing Strategy**:
  - `quickstart.md` testing section: Unit tests (domain logic), integration tests with Testcontainers (no mocked RabbitMQ/MongoDB), contract tests (Protobuf schema validation) ✅
  - Project structure defines `test/contract/`, `test/integration/`, `test/unit/` separation ✅

**Result**: **PASSED** - All Phase 1 design artifacts comply with Constitution v1.1.0. Zero violations identified.

## Project Structure

### Documentation (this feature)

```text
specs/001-ubiquitous-messaging-platform/
├── spec.md              # Feature specification (completed)
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (architecture decisions, alternatives)
├── data-model.md        # Phase 1 output (MongoDB schemas, entity relationships)
├── quickstart.md        # Phase 1 output (getting started guide, Docker Compose setup)
├── contracts/           # Phase 1 output (Protobuf definitions)
│   ├── chat_service.proto
│   ├── conversation_service.proto
│   └── common_types.proto
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

**Structure Decision**: Single Java project with **Hexagonal Architecture (Ports & Adapters)** per Constitution requirement. Domain logic isolated from infrastructure, enabling testability and demonstrating separation of concerns for educational purposes.

```text
src/
├── main/
│   ├── java/com/chat/
│   │   ├── ChatApiApplication.java              # Spring Boot entry point
│   │   ├── domain/                              # Core business logic (PURE - no framework dependencies)
│   │   │   ├── model/                           # Entities: Message, Conversation, User, MessageState
│   │   │   ├── port/                            # Interfaces (ports)
│   │   │   │   ├── in/                          # Input ports (use cases)
│   │   │   │   │   ├── SendMessageUseCase.java
│   │   │   │   │   ├── FetchConversationHistoryUseCase.java
│   │   │   │   │   └── TrackMessageStatusUseCase.java
│   │   │   │   └── out/                         # Output ports (repositories, external services)
│   │   │   │       ├── MessageRepository.java
│   │   │   │       ├── ConversationRepository.java
│   │   │   │       ├── MessagePublisher.java    # RabbitMQ abstraction
│   │   │   │       └── FileStorage.java         # MinIO abstraction (P2 - deferred)
│   │   │   └── service/                         # Domain services implementing use cases
│   │   │       ├── MessageService.java
│   │   │       └── ConversationService.java
│   │   ├── adapter/                             # Infrastructure adapters (FRAMEWORK-AWARE)
│   │   │   ├── in/                              # Input adapters (API controllers)
│   │   │   │   ├── grpc/                        # gRPC endpoints
│   │   │   │   │   ├── ChatServiceGrpcImpl.java
│   │   │   │   │   └── ConversationServiceGrpcImpl.java
│   │   │   │   └── rest/                        # REST controllers (optional, for P2 file uploads)
│   │   │   │       └── FileUploadController.java
│   │   │   └── out/                             # Output adapters (persistence, messaging)
│   │   │       ├── persistence/                 # MongoDB implementations
│   │   │       │   ├── MongoMessageRepository.java
│   │   │       │   └── MongoConversationRepository.java
│   │   │       ├── messaging/                   # RabbitMQ implementations
│   │   │       │   ├── RabbitMQMessagePublisher.java
│   │   │       │   └── RabbitMQMessageConsumer.java  # Worker listening to queues
│   │   │       └── storage/                     # MinIO implementation (P2 - deferred)
│   │   │           └── MinIOFileStorage.java
│   │   ├── config/                              # Spring Boot configuration
│   │   │   ├── GrpcConfig.java
│   │   │   ├── RabbitMQConfig.java
│   │   │   ├── MongoConfig.java
│   │   │   └── SecurityConfig.java              # Authentication/authorization (assumed external per spec)
│   │   └── worker/                              # Background workers (RabbitMQ consumers)
│   │       ├── MessageDeliveryWorker.java       # Processes messages from queue → MongoDB
│   │       └── MessageStateTransitionWorker.java # Handles state updates (SENT → DELIVERED → READ)
│   ├── proto/                                   # Protobuf definitions (gRPC contracts)
│   │   └── chat_api.v1.proto                    # Existing proto file (will be versioned)
│   └── resources/
│       ├── application.properties               # Spring Boot config (profiles: dev, prod)
│       └── application-dev.yml                  # Docker Compose environment variables
└── test/
    ├── java/com/chat/
    │   ├── contract/                            # Protobuf contract tests (breaking change detection)
    │   │   └── ChatServiceContractTest.java
    │   ├── integration/                         # End-to-end tests with Testcontainers
    │   │   ├── MessageFlowIntegrationTest.java  # POC validation: API → RabbitMQ → MongoDB
    │   │   └── ConversationManagementTest.java
    │   └── unit/                                # Domain logic unit tests (pure business logic)
    │       ├── MessageServiceTest.java
    │       └── ConversationServiceTest.java
    └── resources/
        └── testcontainers/
            └── docker-compose-test.yml          # Testcontainers config (RabbitMQ + MongoDB)

docker-compose.yml                               # Local development orchestration (4 services for POC)
pom.xml                                          # Maven dependencies (Spring Boot, gRPC, RabbitMQ, MongoDB)
README.md                                        # Project overview (Constitution compliance, setup instructions)
```

**Architecture Highlights**:
- **Hexagonal core**: `domain/` package has ZERO Spring/gRPC/MongoDB dependencies (pure Java POJOs)
- **Input adapters**: gRPC controllers implement generated Protobuf stubs, delegate to use cases
- **Output adapters**: MongoDB repositories implement domain `port/out` interfaces
- **Worker separation**: `worker/` package contains RabbitMQ consumers (separate concern from API)
- **Testing strategy**: `unit/` tests domain logic, `integration/` tests full flow with Testcontainers, `contract/` tests Protobuf schemas

## Complexity Tracking

**No violations** - Constitution Check passed all 8 principles. POC-first approach per Principle VIII eliminates architectural complexity in initial implementation.

---

## Phase Outputs

### Phase 0: Architecture Research (✅ Complete)

**Deliverable**: `research.md` - Architectural decisions with alternatives considered

**Contents**:
- Decision 1: gRPC + Protobuf vs REST/GraphQL/WebSocket (30-40% latency improvement, strong typing)
- Decision 2: RabbitMQ (POC) vs Kafka/SQS/Google Pub/Sub (at-least-once delivery, topic exchange)
- Decision 3: MongoDB with embedded documents vs relational schemas (flexible schema, embedded state_history)
- Decision 4: tus protocol (P2) vs S3 multipart/custom resumable upload (industry standard, client libraries)
- Decision 5: Telegram Bot API (P4) vs WhatsApp/Signal (free, webhook support; mocked adapters for others)
- Decision 6: Spring Security OAuth2 (deferred) - assume pre-authenticated per spec A-002

**Learning Value**: Documents trade-offs for each decision, demonstrating architectural reasoning process.

---

### Phase 1: Design (✅ Complete)

**Deliverables**: `data-model.md`, `contracts/`, `quickstart.md`

**`data-model.md`** (535 lines):
- **7 entities** with MongoDB schemas: User, Conversation, Message, MessageState (embedded), FileMetadata (P2), Webhook (P2), LinkedAccount (P4)
- **Indexes**: Unique on message_id, compound on (conversation_id, timestamp), (conversation_id, sequence_number)
- **Relationships**: User ↔ Conversation (many-to-many via participants array), Conversation ↔ Message (one-to-many), Message ↔ MessageState (one-to-many embedded)
- **Write concern**: "majority" for durability across replica set

**`contracts/`** (4 files):
- `common_types.proto`: Enums (MessageStatus, ConversationType), shared messages (UserInfo, MessageStateTransition, FileMetadata, PaginationInfo, ErrorDetail)
- `chat_service.proto`: ChatService with 4 RPCs (SendMessage, StreamMessages bidirectional, GetMessageStatus, MarkMessageAsRead)
- `conversation_service.proto`: ConversationService with 6 RPCs (CreateConversation, ListConversations, GetConversation, GetConversationHistory, AddMember P3, RemoveMember P3)
- `README.md`: Maven protobuf plugin config, contract testing strategy, versioning policy (chat_api.v1 → chat_api.v2)

**`quickstart.md`** (541 lines):
- **Prerequisites**: Java 17, Maven 3.9+, Docker Desktop, Git
- **5-step quickstart**: Clone repo → Start Docker Compose → Build project → Run API → Test with grpcurl
- **Docker Compose config**: 4 services (RabbitMQ with management UI, MongoDB replica set, Chat API)
- **Application config**: application-dev.yml with MongoDB URI, RabbitMQ connection, gRPC server port 9090
- **Testing guide**: Unit tests (domain logic), integration tests (Testcontainers validating API → RabbitMQ → MongoDB flow), contract tests (Protobuf validation)
- **Troubleshooting**: Port conflicts, connection refused, Testcontainers Docker socket issues

**Learning Value**: Provides complete development environment setup, demonstrates integration testing with real infrastructure (Testcontainers), teaches gRPC contract-first development.

---

### Phase 2: Task Breakdown (⏭️ Next Step)

**Command**: Execute `/speckit.tasks` to generate `tasks.md`

**Expected Deliverable**: Work breakdown structure with:
- Phase 1 (Setup): Maven project initialization, Docker Compose config, Protobuf code generation
- Phase 2 (Foundational): Domain model POJOs, MongoDB repositories, RabbitMQ configuration
- Phase 3 (P1 User Story 1): Send/receive text messages implementation
- Phase 4 (P1 User Story 2): Track message status lifecycle (SENT → DELIVERED → READ)
- Phase 5 (P1 User Story 3): Create private conversations
- Phase 6+ (P2-P4): Deferred user stories (file uploads with MinIO, group conversations, multi-platform routing via Telegram)

**Task Structure** (per Constitution):
- TDD requirements: Write failing test first (Red), implement minimum code (Green), refactor (Refactor)
- Acceptance criteria: Clear definition of done for each task
- Dependencies: Identify parallel vs sequential work (e.g., RabbitMQ config and MongoDB repositories can be parallelized)
- Estimated effort: T-shirt sizing (S/M/L) for workload planning

**Next Action**: User should review Phase 0/1 artifacts (especially `research.md` decisions and `data-model.md` schemas) before approving progression to task breakdown. If approved, execute `/speckit.tasks` to generate work items.

---

## Summary & Recommendations

### Plan Status: ✅ COMPLETE (Ready for Task Breakdown)

**Generated Artifacts**:
- ✅ `plan.md`: Technical Context, Constitution Check (8 principles validated), Project Structure (hexagonal architecture)
- ✅ `research.md`: 6 architectural decisions with alternatives (Phase 0)
- ✅ `data-model.md`: 7 entities with MongoDB schemas and indexes (Phase 1)
- ✅ `contracts/`: 3 Protobuf service definitions + README (Phase 1)
- ✅ `quickstart.md`: Docker Compose setup and testing guide (Phase 1)
- ✅ `.github/agents/copilot-instructions.md`: Agent context with Java 17, Spring Boot, MongoDB technologies

**POC Validation (Principle VIII)**:
- ✅ Docker Compose limited to 4 services (RabbitMQ, MongoDB, MongoDB Init, Chat API)
- ✅ No Kafka in POC (deferred to post-validation)
- ✅ No MinIO in POC (deferred to P2 file uploads)
- ✅ No Telegram integration in POC (deferred to P4 multi-platform routing)
- ✅ Standard libraries documented: tus protocol (P2), springdoc-openapi (deferred)
- ✅ ONE real integration planned: Telegram Bot API (P4, deferred); WhatsApp/Instagram mocked

**Constitution Compliance**: All 8 principles validated pre-design and post-design. Zero violations.

**Recommended Next Steps**:
1. **Review architecture decisions** in `research.md` - ensure alignment with educational goals (especially Decision 1: gRPC vs REST trade-offs)
2. **Validate entity design** in `data-model.md` - confirm MongoDB schemas support spec requirements (message state tracking, conversation pagination)
3. **Inspect Protobuf contracts** in `contracts/` - verify RPCs align with spec API examples (SendMessage, GetConversationHistory)
4. **Execute `/speckit.tasks`** - generate work breakdown structure for POC implementation
5. **Start POC development** - implement Phase 1 foundational tasks (Maven setup, Docker Compose, Protobuf code generation) before user stories

**Critical Reminders for Implementation**:
- **TDD mandatory**: Write failing test → implement → refactor for EVERY code change
- **Integration tests with Testcontainers**: No mocked RabbitMQ/MongoDB (validate real infrastructure)
- **POC-first progression**: Do NOT add Kafka/MinIO until RabbitMQ message flow is proven with integration tests
- **Hexagonal architecture**: Keep `domain/` package free of Spring/gRPC/MongoDB dependencies (educational value)
- **One real integration limit**: Telegram Bot API only for P4; mock WhatsApp/Instagram adapters

**Branch**: `001-ubiquitous-messaging-platform` (current)  
**Plan Path**: `specs/001-ubiquitous-messaging-platform/plan.md`  
**Status**: Planning phase complete, ready for task breakdown via `/speckit.tasks`
