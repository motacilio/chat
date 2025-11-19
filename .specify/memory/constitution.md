<!--
╔═══════════════════════════════════════════════════════════════════════════╗
║                        SYNC IMPACT REPORT                                 ║
║                    Constitution Amendment Record                          ║
╚═══════════════════════════════════════════════════════════════════════════╝

VERSION CHANGE: 1.0.0 → 1.1.0

RATIONALE: MINOR version bump adding Principle VIII (Incremental Delivery &
Pragmatic Scope) to enforce POC-first development approach. This amendment
establishes concrete guidelines for building complex features incrementally,
starting with minimal local flows before adding distributed components.

Amendment addresses critical educational risk: students attempting to build
entire distributed system at once without validating core assumptions first.
New principle mandates proof-of-concept validation before architectural
complexity, reduces dependency on commercial APIs (Telegram only, others mocked),
and standardizes reuse of industry libraries (tus protocol, S3 multipart).

═══════════════════════════════════════════════════════════════════════════

PRINCIPLES MODIFIED:

  [ADDED] VIII. Incremental Delivery & Pragmatic Scope (NON-NEGOTIABLE)
    Focus: POC-first development, layer-by-layer validation, standard library
           reuse, realistic external dependencies (1 real integration + mocks)
    Distributed Systems Concept: Validates architectural assumptions early,
                                  reduces integration risk, teaches iterative
                                  refinement over big-bang deployment

  [UNCHANGED] I-VII: All existing principles remain intact

═══════════════════════════════════════════════════════════════════════════

SECTIONS MODIFIED:

  • Distributed Systems Educational Standards
    [UPDATED] Technology Stack: Clarified Kafka as post-POC addition
              (start with RabbitMQ, add Kafka only after message flow validated)
    
  • Development Workflow  
    [UPDATED] Feature Development Process: Added "POC Validation Gate" as
              Step 3.5 between Planning and Architecture Review
              - Requires working local flow before distributed components
              - Must demonstrate core value proposition with minimal dependencies

═══════════════════════════════════════════════════════════════════════════

TEMPLATE CONSISTENCY VALIDATION:

  ✅ .specify/templates/plan-template.md
     - Already supports phased delivery via "Implementation Phases" section
     - POC-first approach aligns with existing incremental guidance
     - No structural updates required
     
  ✅ .specify/templates/spec-template.md
     - User story prioritization (P1/P2/P3) naturally supports POC definition
     - Acceptance criteria format accommodates "POC validates" scenarios
     - No structural updates required
     
  ✅ .specify/templates/tasks-template.md
     - Phase structure perfectly supports POC → Layer 2 → Layer 3 sequencing
     - Task organization by user story enables POC subset selection
     - No structural updates required

  ⚠️  .specify/templates/commands/*.md
     - Review for any hardcoded "all features at once" language
     - Ensure prompts encourage POC-first thinking

═══════════════════════════════════════════════════════════════════════════

FOLLOW-UP ACTIONS:

  [x] Define Principle VIII with concrete POC requirements
  [x] Update Technology Stack to clarify RabbitMQ → Kafka progression
  [x] Insert POC Validation Gate into Development Workflow
  [ ] Review command prompts for alignment with incremental approach
  [ ] Update docs/architecture/decisions.md with Kafka deferral rationale
  
DEFERRED ITEMS:

  • Command template review - can be done in separate amendment if needed
  • Architecture decision documentation - will be addressed in feature planning

═══════════════════════════════════════════════════════════════════════════

SUGGESTED COMMIT MESSAGE:

  docs(constitution): amend to v1.1.0 - add incremental delivery principle
  
  - Add Principle VIII: Incremental Delivery & Pragmatic Scope
  - Mandate POC validation before distributed complexity (API→worker→DB first)
  - Require layer-by-layer construction with independent testing
  - Standardize library reuse (tus/S3 multipart, OpenAPI generation)
  - Limit external integrations (1 real channel + mocks for educational projects)
  - Insert POC Validation Gate in development workflow (step 3.5)
  - Clarify RabbitMQ-first approach, Kafka as post-POC enhancement
  
  BREAKING: None - additive change only
  MIGRATION: Existing features unaffected; applies to new work

═══════════════════════════════════════════════════════════════════════════
Generated: 2025-11-18
Last Amended: 2025-11-18
-->

# Chat API Constitution

**Project Context**: Educational distributed systems platform for Information Systems students at Universidade Federal de Goiás (UFG), demonstrating scalable backend architecture patterns through a real-time messaging API built with Java, Spring Boot, gRPC, RabbitMQ, and MongoDB.

## Core Principles

### I. Educational Code Quality (NON-NEGOTIABLE)

Every line of code MUST serve as a learning resource. This project exists to teach distributed systems concepts—code quality is not negotiable.

**Non-negotiable rules**:
- Code MUST be readable by students encountering distributed systems for the first time
- Each class/method MUST demonstrate one clear concept or pattern
- Complex algorithms MUST include step-by-step inline comments explaining the **why**, not just the what
- Design patterns MUST be explicitly named in class documentation (e.g., `/** Factory Pattern: creates message handlers based on type */`)
- No "clever" code that sacrifices clarity for brevity—if it needs explanation in a comment, refactor it to be self-evident
- Every service class MUST include a usage example in its JavaDoc header

**Rationale**: Code is the primary teaching material. Students learn by reading implementation details, not just using APIs. Obscure or terse code defeats the educational mission. When a student opens any file, they should understand both the implementation AND the distributed systems principle being demonstrated (e.g., "This class shows async message processing with backpressure handling").

**Example compliant documentation**:
```java
/**
 * Responsibility: Validates message payloads before enqueueing to RabbitMQ.
 * Pattern: Strategy Pattern for validation rules.
 * Distributed Systems Concept: Input validation at API boundary prevents
 * invalid data propagation through async message flows.
 * 
 * Does NOT handle: Persistence, authorization, delivery confirmation.
 */
public class MessagePayloadValidator { ... }
```

### II. Explicit Responsibility Declaration (NON-NEGOTIABLE)

Every component MUST declare its responsibility in a single, unambiguous sentence. Boundaries MUST be explicit.

**Non-negotiable rules**:
- Every class MUST have a JavaDoc header starting with `Responsibility: [single sentence]`
- Services MUST document their bounded context and explicitly state what they do NOT handle
- Methods with side effects (I/O, state mutation, messaging) MUST declare those effects in JavaDoc
- Configuration files MUST include comments explaining their system-wide impact
- Every package MUST have `package-info.java` describing its role and relationships

**Rationale**: Distributed systems fail when component boundaries blur. Explicit responsibilities prevent:
- Service coupling (knowing what a service does NOT do is as important as what it does)
- Debugging confusion (side effects must be traceable)
- Architecture drift (clear boundaries make violations obvious)

In educational context, this teaches students to think in terms of contracts and bounded contexts from day one.

**Example**:
```java
/**
 * Responsibility: Publishes validated messages to RabbitMQ exchange.
 * Side Effects: Network I/O to RabbitMQ broker, may throw ConnectionException.
 * Does NOT: Validate payloads (see MessagePayloadValidator), persist to DB, handle retries.
 */
public void publishMessage(ValidatedMessage message) throws ConnectionException { ... }
```

### III. Descriptive Naming & Self-Documenting Code

Variable, method, and class names MUST eliminate the need for clarifying comments. Code should read like technical prose.

**Non-negotiable rules**:
- No abbreviations except industry-standard acronyms (HTTP, gRPC, AMQP, UUID are allowed; `msg`, `usr`, `req`, `conn` are PROHIBITED)
- Boolean variables MUST use `is`, `has`, `should`, `can` prefixes (`isMessageDelivered`, `hasActiveSubscription`, `shouldRetryOnFailure`)
- Methods MUST use verb-noun pairs describing the action (`validateMessagePayload()`, `enqueueToRabbitMQ()`, `fetchConversationHistory()`)
- Class names MUST indicate role via suffix: `*Service`, `*Repository`, `*Controller`, `*Validator`, `*Config`, `*Worker`, `*Handler`
- Constants MUST include units or context (`MAX_RETRY_ATTEMPTS`, `CONNECTION_TIMEOUT_SECONDS`, `DEFAULT_PAGE_SIZE`)
- Collections MUST use plural nouns (`activeConnections`, `pendingMessages`—NOT `connectionList`, `messageArray`)
- Temporary variables in loops MUST have descriptive names (`for (Message incomingMessage : messageQueue)` NOT `for (Message m : queue)`)

**Rationale**: Self-documenting code reduces cognitive load and makes the codebase accessible regardless of experience level. When names are precise, the code reads like natural language, which is critical for educational environments where students need to understand intent quickly.

**Examples**:
```java
// ❌ PROHIBITED
int retry = 3;
boolean f = checkMsg(m);
List<Msg> msgs = repo.get();

// ✅ REQUIRED
int maximumRetryAttempts = 3;
boolean isMessageValid = validateMessageFormat(incomingMessage);
List<Message> conversationMessages = messageRepository.findByConversationId(conversationId);
```

### IV. Test-Driven Development (NON-NEGOTIABLE)

Tests are contracts, not afterthoughts. All user-facing functionality MUST follow strict Red-Green-Refactor discipline.

**Non-negotiable rules**:
- Unit tests MUST be written BEFORE implementation for all service-layer logic
- Tests MUST be reviewed and approved by peer/instructor before implementation begins
- Integration tests MUST verify contracts between services (gRPC endpoints, RabbitMQ message schemas, MongoDB queries)
- Contract tests MUST exist for all Protobuf definitions to catch breaking changes
- Test names MUST follow pattern: `should[ExpectedBehavior]When[StateUnderTest]` (e.g., `shouldEnqueueMessageWhenPayloadIsValid()`)
- Tests MUST NOT use mocks for infrastructure—use Testcontainers for MongoDB, RabbitMQ, MinIO
- Minimum coverage: 80% line coverage across the project, 100% coverage for critical paths (authentication, message delivery, payment processing)
- Every test MUST include a comment explaining the distributed systems concept being validated

**Test execution workflow**:
1. Write failing test based on user story acceptance criteria
2. Review test with peer/instructor—does it correctly validate the requirement?
3. Run test suite—verify new test FAILS (Red)
4. Implement minimum code to pass the test (Green)
5. Refactor for clarity, performance, and adherence to principles (Refactor)
6. Run full test suite—verify no regressions
7. Commit test + implementation together

**Rationale**: Distributed systems fail in subtle, non-deterministic ways. Tests document expected behavior under specific conditions, enable confident refactoring, and catch regressions before they reach production. TDD enforces API-first thinking, which is essential for service-oriented architectures.

**Example**:
```java
/**
 * Distributed Systems Concept: Validates at-least-once delivery guarantee
 * when RabbitMQ broker acknowledges message receipt.
 */
@Test
public void shouldReceiveAcknowledgmentWhenMessageIsSuccessfullyEnqueued() {
    // Given: A valid message and connected RabbitMQ broker
    // When: Message is published to exchange
    // Then: Broker returns acknowledgment within timeout
}
```

### V. Performance with Scalability

Code MUST be designed for horizontal scalability and high availability from the start. Performance is not an afterthought.

**Non-negotiable rules**:
- Services MUST be stateless—session state belongs in MongoDB or distributed cache (Redis), NOT in-memory
- Database queries MUST use indexes—all `find()` operations MUST document the index being used in comments
- Blocking I/O is PROHIBITED in request-handling paths—use async/reactive patterns (Spring WebFlux, CompletableFuture, @Async)
- Connection pools MUST have explicit size limits based on documented load calculations
- RabbitMQ consumers MUST have configurable concurrency (`spring.rabbitmq.listener.simple.concurrency`) and prefetch limits
- Performance tests MUST verify target metrics before merging to main branch
- Every service MUST define its scaling strategy in `docs/architecture/scaling-[service].md`

**Performance targets**:
- **Latency**: <100ms p95 for text message submission (measured at gRPC endpoint)
- **Throughput**: Support 1,000 concurrent gRPC connections per service instance
- **Availability**: 99.9% uptime via stateless design + Kubernetes horizontal pod autoscaling
- **Data durability**: At-least-once delivery guarantee for messages via RabbitMQ acknowledgments

**Rationale**: Students MUST understand that distributed systems design decisions have direct performance implications. Learning to design for scalability early prevents costly re-architecture later. Horizontal scaling is only possible when services are stateless and I/O is non-blocking.

**Required documentation per service**:
```markdown
## Scaling Strategy: Message Publisher Service

**Load assumptions**: 10,000 daily active users, 500 concurrent connections per instance
**Scaling trigger**: CPU > 70% or connection count > 800
**Scaling method**: Kubernetes HorizontalPodAutoscaler (min: 2, max: 10 replicas)
**State management**: Session-less; uses MongoDB for message persistence
**Bottlenecks**: RabbitMQ connection pool (max 50 connections/instance)
```

### VI. Clear Architecture & Transparency (NON-NEGOTIABLE)

No obscure frameworks, patterns, or dependencies without explicit justification and educational value. Complexity MUST be defended.

**Non-negotiable rules**:
- Technology choices MUST be documented in `docs/architecture/decisions.md` with: purpose, alternatives considered, trade-offs, learning resources (link to official docs)
- Adding a new dependency MUST include a `JUSTIFICATION.md` file in the PR describing why it's necessary
- Architecture diagrams MUST exist in `docs/architecture/` for: system overview, data flow, deployment topology, message flow
- Breaking changes MUST increment API version (Protobuf package versioning: `chat_api.v1` → `chat_api.v2`) and include migration guide
- Deprecated features MUST have removal timeline (minimum 2 weeks notice with clear migration path)
- If a pattern introduces complexity, a `COMPLEXITY.md` file MUST justify it with: problem being solved, alternatives considered, maintenance implications

**Prohibited without written justification**:
- Custom serialization formats (use Protobuf for services, JSON for REST APIs)
- Reflection-heavy frameworks beyond Spring Boot's standard usage
- Code generation without committing generated sources to the repository
- "Magic" configuration via annotations without documented behavior (e.g., `@EnableSomethingMagic` requires explanation)
- Database schema changes without migration scripts

**Rationale**: Students need to understand WHY technologies were chosen, not just HOW to use them. Transparency about trade-offs builds critical thinking skills. In educational settings, every dependency is a learning opportunity—random additions dilute focus.

**Example `docs/architecture/decisions.md` entry**:
```markdown
### Decision: Use gRPC instead of REST for inter-service communication

**Date**: 2025-11-18
**Status**: Accepted

**Context**: Need efficient binary protocol for high-throughput message delivery.

**Alternatives considered**:
- REST with JSON: Simpler, but higher latency due to text serialization
- GraphQL: Flexible queries, but overkill for simple CRUD + pub/sub

**Decision**: gRPC with Protobuf

**Rationale**:
- 30-40% lower latency than JSON (binary serialization)
- Strong typing via Protobuf prevents contract drift
- Streaming support for real-time message delivery
- Industry-standard for microservices (teaches relevant skills)

**Trade-offs**:
- Steeper learning curve than REST
- Requires Protobuf schema management

**Learning resources**: https://grpc.io/docs/languages/java/quickstart/
```

### VII. Documentation-First Culture

Documentation is not optional—it is a deliverable equal in importance to code. Undocumented code is incomplete code.

**Non-negotiable rules**:
- `README.md` MUST include: project purpose, architecture overview (with diagram link), Docker Compose setup instructions, run commands, testing guide
- API documentation MUST be auto-generated from Protobuf definitions and served via gRPC Server Reflection
- Every feature MUST have a corresponding `docs/features/[feature-name].md` file explaining: use case, implementation approach, testing strategy, performance considerations
- Runbooks MUST exist for common operations in `docs/runbooks/`: deployment, scaling, troubleshooting, rollback procedures
- Code comments MUST explain "why" (business logic, edge cases, design decisions), NOT "what" (that's what self-documenting code does)
- Commit messages MUST follow Conventional Commits format: `type(scope): description` (e.g., `feat(messaging): add message delivery confirmation`)

**Documentation standards**:
- Use Markdown for all documentation
- Diagrams MUST be source-controlled (use Mermaid, PlantUML, or commit PNGs with source files)
- Update documentation in the SAME commit as code changes (not separate commits)
- No `TODO` comments without a corresponding GitHub issue number and deadline

**Rationale**: Documentation enables asynchronous learning and serves as a knowledge base for future students. Well-documented systems are maintainable systems. In educational contexts, documentation is how students learn the "why" behind design decisions.

**Example feature documentation structure** (`docs/features/message-delivery.md`):
```markdown
# Feature: Message Delivery with At-Least-Once Guarantee

## Use Case
Users MUST receive all messages even if temporarily offline. System MUST guarantee no message loss.

## Implementation Approach
- **Pattern**: Event-driven with persistent message queue (RabbitMQ)
- **Flow**: Client → gRPC API → RabbitMQ → Worker → MongoDB → Push Notification
- **Guarantee**: RabbitMQ acknowledgment only after MongoDB persistence

## Testing Strategy
- Unit: Validate message format and size limits
- Integration: End-to-end flow with Testcontainers (RabbitMQ + MongoDB)
- Performance: 1000 msg/sec throughput test

## Performance Considerations
- RabbitMQ prefetch limit: 10 (prevents worker overload)
- MongoDB write concern: majority (ensures durability)
```

---

### VIII. Incremental Delivery & Pragmatic Scope (NON-NEGOTIABLE)

Complex distributed systems MUST be built incrementally, validating core flows locally before adding distributed components. Educational projects MUST limit external dependencies to one real integration, using mocks/simulators for others.

**Non-negotiable rules**:

**POC-First Development**:
- Every feature with distributed components MUST start with a Proof-of-Concept (POC) demonstrating local flow: API → Worker → Database
- POC MUST deliver end-to-end value (e.g., "send message via API, store in DB, retrieve via API") WITHOUT distributed infrastructure (Kafka, object storage, external APIs)
- POC acceptance criteria: Working flow with automated tests proving the core concept, runnable in single `docker-compose.yaml` with <5 services
- Only after POC validation can distributed components be added layer-by-layer (see Layer-by-Layer Construction below)

**Layer-by-Layer Construction**:
- After POC validation, add ONE distributed component at a time: RabbitMQ → Kafka → MinIO → External integrations
- Each layer addition MUST include: independent tests validating the new component, updated architecture diagram showing new dependencies, performance benchmarks comparing before/after
- Pull requests MUST NOT introduce multiple distributed components simultaneously (exception: related components like Kafka + Zookeeper)
- Example progression for file upload feature:
  1. **POC (Week 1)**: REST upload endpoint → save to filesystem → return download URL (validates upload logic, chunking, metadata persistence)
  2. **Layer 2 (Week 2)**: Replace filesystem with MinIO (validates object storage integration, pre-signed URLs)
  3. **Layer 3 (Week 3)**: Add resumable upload via `tus` protocol (validates fault-tolerant upload)

**Standard Library Reuse**:
- MUST use industry-standard libraries for common patterns—do NOT implement from scratch:
  - **Resumable file uploads**: `tus` protocol (Java client: `tus-java-client`) OR S3 multipart upload API (via AWS SDK or MinIO client)
  - **API documentation**: OpenAPI 3.0 specification auto-generated from Spring REST controllers (use `springdoc-openapi`) OR gRPC Server Reflection
  - **Authentication/Authorization**: Spring Security with OAuth2/JWT (do NOT implement custom token systems)
  - **Rate limiting**: Resilience4j rate limiter OR Bucket4j (do NOT implement custom token bucket)
- Adding a custom implementation MUST include `JUSTIFICATION.md` explaining why standard libraries are insufficient

**Realistic External Dependencies**:
- Educational projects MUST limit external platform integrations to ONE real implementation + mock adapters for others
- Example for multi-platform messaging: Integrate Telegram Bot API (real), use mock adapters simulating WhatsApp/Instagram/Facebook Messenger
- Rationale: Commercial APIs (WhatsApp Business API, Instagram Graph API) require expensive subscriptions, lengthy approvals, and strict usage policies—impractical for student projects
- Mock adapters MUST implement the same interface as real adapters, enabling transparent replacement in production

**Rationale**: 
Students learning distributed systems often attempt "big bang" implementations (build entire architecture at once), leading to:
- Integration paralysis (can't test anything until everything works)
- Debugging complexity (10 moving parts, 100 possible failure points)
- Wasted effort (architectural assumptions proven wrong after weeks of implementation)

Incremental delivery validates assumptions early, reduces risk, and teaches iterative refinement—core skills for production engineering. Limiting external dependencies ensures projects are completable within semester timelines without budget constraints.

**Distributed Systems Concept**: 
This principle teaches **iterative system design** and **risk reduction via layered validation**. Students learn that complex systems are not built top-down from architecture diagrams—they evolve from simple working prototypes that are progressively enhanced. This mirrors real-world development where MVPs validate product-market fit before scaling infrastructure.

**Example POC Validation Gate** (from `/speckit.plan`):
```markdown
## POC Deliverable (Week 1-2)

**Goal**: Validate core message flow without distributed complexity

**Components**:
- Spring Boot gRPC server (message submission endpoint)
- RabbitMQ worker (message processor)
- MongoDB (message persistence)
- Integration test: client → API → worker → DB → query API (round-trip verification)

**Acceptance Criteria**:
✅ User can send message via gRPC API
✅ Message is enqueued to RabbitMQ (manual verification via RabbitMQ Management UI)
✅ Worker processes message and persists to MongoDB
✅ User can retrieve message via query API
✅ Full flow completes in <500ms (p95)
✅ Integration test passes with Testcontainers

**Blocked Components (deferred to Layer 2)**:
❌ Kafka (not needed until throughput >10k msg/sec proven necessary)
❌ MinIO (file uploads deferred to P2 user story)
❌ External platform integrations (multi-platform routing deferred to P4)

**Decision Point**: Only if POC passes all acceptance criteria do we proceed to add Kafka/MinIO in subsequent phases.
```

## Distributed Systems Educational Standards

### Technology Stack (Fixed for Consistency)

**Core Technologies** (MUST NOT change without constitution amendment):
- **Language**: Java 17 (LTS for stability and enterprise relevance)
- **Framework**: Spring Boot 3.2.5 (industry-standard for microservices, extensive ecosystem)
- **RPC Protocol**: gRPC 1.64.0 with Protobuf 3.25.3 (efficient binary protocol, demonstrates strongly-typed service contracts)
- **Message Broker (POC)**: RabbitMQ via Spring AMQP (demonstrates async messaging, pub/sub patterns, at-least-once delivery)
- **Message Broker (Post-POC)**: Apache Kafka (add ONLY after RabbitMQ message flow validated; demonstrates high-throughput event streaming, partition-based scaling)
- **Database**: MongoDB via Spring Data MongoDB (demonstrates NoSQL for event-sourced chat data, flexible schema for message types)
- **Object Storage (Post-POC)**: MinIO (S3-compatible, add ONLY after basic file metadata persistence validated; demonstrates separation of metadata vs. binary storage)
- **Testing**: JUnit 5 + Testcontainers (integration testing with real infrastructure, not mocks)
- **Build Tool**: Maven 3.9+ (standard Java build tool, declarative dependency management)
- **Containerization**: Docker + Docker Compose (local multi-service orchestration, production-like environment)

**Rationale for stack choices**:
Each component teaches a specific distributed systems concept while remaining accessible for educational purposes. This stack is representative of real-world enterprise architectures (Fortune 500 companies use similar stacks), giving students marketable skills.

**POC-First Technology Progression** (see Principle VIII):
1. **Phase 1 (POC)**: API → RabbitMQ Worker → MongoDB (validates core message flow locally)
2. **Phase 2**: Add Kafka for high-throughput event streaming (after RabbitMQ patterns proven)
3. **Phase 3**: Add MinIO for file storage (after metadata persistence validated with MongoDB)

### Required Architectural Patterns

- **Hexagonal Architecture (Ports & Adapters)**: Domain logic isolated from infrastructure; use interfaces (ports) for repositories, messaging, external APIs. Makes testing easier and teaches separation of concerns.

- **CQRS Lite**: Separate read models from write models for message retrieval vs. publishing. Demonstrates eventual consistency and read/write optimization patterns without full event sourcing complexity.

- **Event-Driven Architecture**: RabbitMQ for async message processing. Demonstrates decoupling, temporal independence, and scalability via buffering.

- **API Versioning**: Protobuf package versioning (`chat_api.v1`, `chat_api.v2`) ensures backward compatibility. Teaches contract evolution in distributed systems.

### Performance & Scalability Targets

- **Latency**: <100ms p95 for text message submission (measured at gRPC endpoint, includes validation + RabbitMQ enqueue)
- **Throughput**: Support 1,000 concurrent gRPC streaming connections per service instance
- **Availability**: 99.9% uptime (43 minutes downtime/month) via stateless design + horizontal pod autoscaling
- **Data Durability**: At-least-once message delivery guarantee via RabbitMQ acknowledgments + MongoDB write concern `majority`
- **Scalability**: Linear horizontal scaling up to 10 service instances (validated via load testing)

## Development Workflow

### Feature Development Process (9 Steps)

1. **Specification**: Create `specs/[###-feature-name]/spec.md` with prioritized user stories (P1, P2, P3) and acceptance criteria
2. **Clarification**: Run `/speckit.clarify` to identify and resolve ambiguities before implementation
3. **Planning**: Run `/speckit.plan` to generate implementation plan with architecture research, data model, contracts
4. **POC Validation Gate** ⚠️ **(NEW - Principle VIII)**:
   - Define POC scope: What is the minimal local flow demonstrating core value? (e.g., API → Worker → DB)
   - Implement POC with <5 Docker Compose services, no external APIs, no Kafka/object storage
   - Write integration tests validating end-to-end POC flow
   - **Decision Point**: Only if POC passes all acceptance criteria proceed to step 5
   - Document POC results in `docs/poc/[feature-name]-poc-results.md` with: working components, performance metrics, lessons learned, recommended next layer
5. **Architecture Review**: Review plan against constitution principles—does it comply with all 8 core principles?
6. **Task Breakdown**: Run `/speckit.tasks` to generate task list organized by user story (enables incremental delivery)
7. **TDD Implementation**: For each task: write tests → review tests → implement → refactor → validate
8. **Documentation**: Update `docs/features/[feature-name].md` with implementation details, runbooks, performance notes
9. **Code Review**: Verify adherence to constitution using checklist below before merge

**POC Examples by Feature Type**:
- **Messaging Feature**: API accepts message → RabbitMQ enqueue → worker persists to MongoDB → query API returns message
- **File Upload Feature**: API accepts file chunk → save to local filesystem → return download URL (validate chunking logic before adding MinIO)
- **Authentication Feature**: Login endpoint → generate JWT → validate JWT in protected endpoint (validate token logic before adding OAuth2 provider integration)

### Code Review Checklist (Constitution Compliance)

Every pull request MUST pass these gates:

- [ ] **Principle I**: All classes have JavaDoc explaining the distributed systems concept being demonstrated
- [ ] **Principle II**: Every class has explicit `Responsibility:` declaration and documents what it does NOT do
- [ ] **Principle III**: No abbreviations in variable/method names (except HTTP, gRPC, AMQP, UUID)
- [ ] **Principle IV**: Tests exist and were written BEFORE implementation (verify git history if needed)
- [ ] **Principle IV**: Integration tests use Testcontainers (no mocked infrastructure)
- [ ] **Principle V**: Performance implications documented (database indexes, async patterns, connection limits)
- [ ] **Principle VI**: No new dependencies without `docs/architecture/decisions.md` entry
- [ ] **Principle VII**: Feature documentation exists in `docs/features/[feature-name].md`
- [ ] **Principle VII**: Code comments explain "why", not "what"
- [ ] **Principle VIII**: POC validation completed before distributed components added (check `docs/poc/[feature-name]-poc-results.md` exists)
- [ ] **Principle VIII**: If using custom implementation instead of standard library, `JUSTIFICATION.md` exists explaining why
- [ ] **Principle VIII**: If adding external integration, limited to ONE real platform (others are mocked)
- [ ] All gRPC endpoints have contract tests validating Protobuf schemas
- [ ] All MongoDB queries use documented indexes (comment showing index name)

### Complexity Justification Requirement

If a feature introduces complexity (new pattern, architectural change, or non-trivial dependency), a `COMPLEXITY.md` file MUST be included in the pull request with:

1. **Problem Being Solved**: What specific issue requires this complexity?
2. **Alternatives Considered**: What simpler approaches were evaluated and why were they rejected?
3. **Educational Value**: What distributed systems concept does this teach?
4. **Learning Resources**: Links to official docs, tutorials, or academic papers
5. **Maintenance Implications**: How does this affect future development?
6. **Rollback Plan**: If this proves problematic, how do we revert?

**Example**: Introducing a distributed cache (Redis) would require justifying why MongoDB queries alone are insufficient, documenting cache invalidation strategy, and explaining how this teaches students about CAP theorem trade-offs.

## Governance

### Amendment Process

This constitution is a living document. To propose an amendment:

1. **Propose**: Open pull request modifying this file with rationale in PR description
2. **Discuss**: Review impact on existing codebase and educational goals in PR comments
3. **Update Templates**: Modify affected templates (`plan-template.md`, `spec-template.md`, `tasks-template.md`) in the same PR
4. **Version Bump**: Increment version according to semantic versioning (see below)
5. **Approval**: Merge only after instructor/peer approval (minimum 2 approvals)

### Versioning Policy

Constitution versions follow semantic versioning (`MAJOR.MINOR.PATCH`):

- **MAJOR**: Removes or fundamentally changes a core principle (e.g., removing TDD requirement). Rare; requires instructor approval and migration plan for existing code.
- **MINOR**: Adds new principle, materially expands guidance, or introduces new educational standards. Requires updating all dependent templates.
- **PATCH**: Clarifications, wording improvements, typo fixes, example additions. No functional impact.

**Current version decision**: This is **1.0.0** (initial ratification establishing complete governance framework).

### Compliance Review

- **Pre-merge**: Every pull request MUST pass the Code Review Checklist above
- **Monthly Architecture Review**: Review 3 recent features for alignment with constitution principles, identify patterns of non-compliance
- **Retrospective**: After each major feature (>20 tasks), hold retrospective to evaluate if principles helped or hindered—propose amendments if needed

### Living Document Philosophy

This constitution evolves with the project. As students encounter edge cases, discover new patterns, or identify ambiguities, amendments are ENCOURAGED—but only if they:
- Improve code clarity, maintainability, or educational value
- Are backed by concrete examples from the codebase
- Preserve the educational mission of the project

**Version**: 1.1.0 | **Ratified**: 2025-11-18 | **Last Amended**: 2025-11-18
