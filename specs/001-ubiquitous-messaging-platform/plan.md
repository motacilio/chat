# Implementation Plan: Ubiquitous Messaging Platform

**Branch**: `001-ubiquitous-messaging-platform` | **Date**: 2025-11-22 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-ubiquitous-messaging-platform/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Build a scalable real-time messaging platform (MVP) supporting text messaging, conversation management (1:1 private and group), and message status tracking (SENT/DELIVERED/READ) for internal clients (web/mobile/CLI). The system uses event-driven architecture with Kafka for async message processing, MongoDB for persistence, and gRPC for high-performance APIs. MVP targets 10,000 concurrent users with <100ms p95 latency and 99.9% uptime.

## Technical Context

**Language/Version**: Java 17 (LTS)  
**Primary Dependencies**: Spring Boot 3.2.5, gRPC 1.64.0, Protobuf 3.25.3, Spring Kafka, Spring Data MongoDB  
**Storage**: MongoDB (NoSQL for message persistence, conversation state, user profiles)  
**Message Broker**: Apache Kafka (event streaming, async message delivery, high-throughput pub/sub)  
**Object Storage**: MinIO (S3-compatible, for P2 file uploads - deferred from MVP)  
**Testing**: JUnit 5, Testcontainers (MongoDB, Kafka), gRPC testing framework  
**Target Platform**: Linux server (Docker containers, Kubernetes deployment)  
**Project Type**: Multi-service distributed system (gRPC API service + Kafka consumers + MongoDB)  
**Performance Goals**: 10,000 concurrent users, 1,000 concurrent gRPC connections/instance, <100ms p95 latency for message submission  
**Constraints**: <2 second message delivery for online users, at-least-once delivery guarantee, 99.9% uptime  
**Scale/Scope**: MVP with 3 P1 user stories (text messaging, status tracking, 1:1 conversations), OAuth 2.0 JWT auth, Prometheus/Grafana/Jaeger observability

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I - Educational Code Quality
✅ **PASS** - All code will include JavaDoc with distributed systems concepts explained  
✅ **PASS** - Each service demonstrates clear patterns (async messaging, event-driven architecture)

### Principle II - Explicit Responsibility Declaration
✅ **PASS** - Services follow bounded context: API (validation/auth), Kafka Consumer (message processing), Repository (persistence)  
✅ **PASS** - Each component declares what it does NOT handle

### Principle III - Descriptive Naming & Self-Documenting Code
✅ **PASS** - No abbreviations policy enforced (messageRepository not msgRepo)  
✅ **PASS** - Boolean naming: isMessageValid, hasActiveConnection

### Principle IV - Test-Driven Development
✅ **PASS** - TDD workflow required for all service-layer logic  
✅ **PASS** - Integration tests with Testcontainers (MongoDB, Kafka) - no mocked infrastructure  
✅ **PASS** - Minimum 80% coverage target, 100% for critical paths (auth, message delivery)

### Principle V - Performance with Scalability
✅ **PASS** - Stateless services (session state in MongoDB)  
✅ **PASS** - MongoDB indexes documented for all queries  
✅ **PASS** - Non-blocking I/O via Spring WebFlux for gRPC streaming  
✅ **PASS** - Kafka consumer concurrency configurable  
✅ **PASS** - Performance targets defined: <100ms p95, 10K concurrent users

### Principle VI - Clear Architecture & Transparency
✅ **PASS** - Technology choices documented (gRPC for RPC, Kafka for messaging, MongoDB for NoSQL)  
✅ **PASS** - Architecture diagrams will be in docs/architecture/  
✅ **PASS** - API versioning via Protobuf packages (chat_api.v1)

### Principle VII - Documentation-First Culture
✅ **PASS** - Feature documentation in docs/features/  
✅ **PASS** - README with setup instructions, Docker Compose commands  
✅ **PASS** - API docs auto-generated from Protobuf via gRPC Server Reflection  
✅ **PASS** - Runbooks for deployment, scaling, troubleshooting

### Principle VIII - Incremental Delivery & Pragmatic Scope
⚠️ **NEEDS CLARIFICATION** - Constitution recommends RabbitMQ → Kafka progression, but spec uses Kafka from start  
**JUSTIFICATION**: Kafka chosen for MVP due to superior partition-based scaling, log persistence, and industry relevance. Team has Kafka experience from prior coursework. Will follow POC-first approach: API → Kafka Consumer → MongoDB validated before adding distributed components.

✅ **PASS** - POC workflow: Local message flow (API → Kafka → DB) before external integrations  
✅ **PASS** - Standard libraries: Spring Kafka, Spring Security OAuth2, springdoc-openapi  
✅ **PASS** - Limited external integrations: Telegram only (P4, post-MVP), others mocked

**OVERALL**: ✅ PASS with one justified deviation (Kafka instead of RabbitMQ-first)

## Project Structure

### Documentation (this feature)

```text
specs/001-ubiquitous-messaging-platform/
├── spec.md              # Feature specification with user stories
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   ├── chat_service.proto
│   ├── conversation_service.proto
│   └── common_types.proto
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── chat/
│   │           ├── ChatApiApplication.java        # Spring Boot main
│   │           ├── config/                         # Spring configs (Kafka, MongoDB, gRPC, Security)
│   │           ├── grpc/                           # gRPC service implementations
│   │           │   ├── ChatServiceImpl.java
│   │           │   └── ConversationServiceImpl.java
│   │           ├── model/                          # Domain entities (Message, Conversation, User)
│   │           ├── repository/                     # MongoDB repositories
│   │           │   ├── MessageRepository.java
│   │           │   ├── ConversationRepository.java
│   │           │   └── UserRepository.java
│   │           ├── worker/                         # Kafka consumers
│   │           │   ├── MessageDeliveryWorker.java
│   │           │   └── MessageStateUpdateWorker.java
│   │           ├── service/                        # Business logic layer
│   │           │   ├── MessageService.java
│   │           │   ├── ConversationService.java
│   │           │   └── AuthService.java
│   │           └── security/                       # OAuth2/JWT auth filters
│   ├── proto/                                      # Protobuf definitions
│   │   ├── chat_service.proto
│   │   ├── conversation_service.proto
│   │   └── common_types.proto
│   └── resources/
│       ├── application.properties                   # Spring Boot config
│       └── application-docker.properties            # Docker-specific overrides
└── test/
    ├── java/
    │   └── com/
    │       └── chat/
    │           ├── integration/                     # Testcontainers tests (MongoDB + Kafka)
    │           │   ├── MessageDeliveryIntegrationTest.java
    │           │   └── ConversationFlowIntegrationTest.java
    │           ├── contract/                        # Protobuf contract tests
    │           │   └── ChatServiceContractTest.java
    │           └── unit/                            # Unit tests for services/repositories
    │               ├── MessageServiceTest.java
    │               └── ConversationServiceTest.java
    └── resources/
        └── application-test.properties              # Test-specific config

docker-compose.yml                                    # Local dev environment (Kafka, Zookeeper, MongoDB, MinIO)
pom.xml                                               # Maven dependencies
docs/
├── architecture/
│   ├── system-overview.md                           # High-level architecture
│   ├── data-flow.md                                 # Message flow diagrams
│   ├── deployment.md                                # Kubernetes deployment topology
│   └── decisions.md                                 # Technology decision log
├── features/
│   └── message-delivery.md                          # Feature implementation details
└── runbooks/
    ├── deployment.md                                # Deployment procedures
    ├── scaling.md                                   # Horizontal scaling guide
    └── troubleshooting.md                           # Common issues & fixes
```

**Structure Decision**: Multi-service distributed system using Spring Boot monolith with clear internal module separation. Services are logically separated (gRPC API, Kafka consumers, repositories) but deployed as single artifact for MVP simplicity. Hexagonal architecture with domain models isolated from infrastructure (gRPC, Kafka, MongoDB adapters).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |

| Using Kafka instead of RabbitMQ-first (Principle VIII) | High-throughput event streaming with partition-based scaling required for 10K concurrent users target. Kafka's log-based persistence and consumer group model better fit the message delivery patterns. | RabbitMQ-first approach: Team already has Kafka experience, and migration from RabbitMQ to Kafka mid-project would require rewriting message flow logic. Starting with Kafka avoids throwaway work while still following POC-first validation. |
