# API Contracts (Protobuf Definitions)

**Feature**: Ubiquitous Messaging Platform  
**Version**: chat_api.v1  
**Status**: POC Design (P1 MVP scope)

This directory contains Protobuf definitions for all gRPC services in the Chat API.

---

## Files

### 1. `common_types.proto`
Shared message definitions used across all services:
- **Enums**: `MessageStatus` (SENT/DELIVERED/READ), `ConversationType` (PRIVATE/GROUP)
- **Messages**: `UserInfo`, `MessageStateTransition`, `FileMetadata` (P2), `PaginationInfo`, `ErrorDetail`

### 2. `chat_service.proto`
Core messaging operations (User Stories 1-2, P1 MVP):
- **SendMessage**: Submit text message with idempotency (client-generated message_id)
- **StreamMessages**: Real-time bidirectional streaming for message delivery
- **GetMessageStatus**: Query state lifecycle (SENT → DELIVERED → READ)
- **MarkMessageAsRead**: Transition message to READ state

### 3. `conversation_service.proto`
Conversation management (User Story 3 P1, User Story 5 P3):
- **CreateConversation**: Create PRIVATE (1:1) or GROUP conversation
- **ListConversations**: Fetch user's conversation list with pagination
- **GetConversation**: Query conversation details (participants, metadata)
- **GetConversationHistory**: Fetch message history with pagination (default 50 messages, max 100)
- **AddMember / RemoveMember**: Group membership management (P3 - deferred)

---

## Usage

### Maven Code Generation

Add to `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-netty-shaded</artifactId>
    <version>1.64.0</version>
  </dependency>
  <dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-protobuf</artifactId>
    <version>1.64.0</version>
  </dependency>
  <dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-stub</artifactId>
    <version>1.64.0</version>
  </dependency>
</dependencies>

<build>
  <extensions>
    <extension>
      <groupId>kr.motd.maven</groupId>
      <artifactId>os-maven-plugin</artifactId>
      <version>1.7.1</version>
    </extension>
  </extensions>
  <plugins>
    <plugin>
      <groupId>org.xolstice.maven.plugins</groupId>
      <artifactId>protobuf-maven-plugin</artifactId>
      <version>0.6.1</version>
      <configuration>
        <protocArtifact>com.google.protobuf:protoc:3.25.3:exe:${os.detected.classifier}</protocArtifact>
        <pluginId>grpc-java</pluginId>
        <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.64.0:exe:${os.detected.classifier}</pluginArtifact>
        <protoSourceRoot>${project.basedir}/src/main/proto</protoSourceRoot>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>compile</goal>
            <goal>compile-custom</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

### Generate Java Code

```bash
mvn clean compile
```

Generated classes will be in `target/generated-sources/protobuf/java/` and `target/generated-sources/protobuf/grpc-java/`.

---

## Contract Testing

Per Constitution Principle IV (TDD), all Protobuf schemas MUST have contract tests to catch breaking changes:

```java
@Test
public void chatServiceContractTest() {
    // Verify SendMessageRequest has required fields
    SendMessageRequest.Builder builder = SendMessageRequest.newBuilder();
    assertNotNull(builder.setMessageId("uuid"));
    assertNotNull(builder.setConversationId("uuid"));
    assertNotNull(builder.setSenderId("uuid"));
    assertNotNull(builder.setMessageText("Hello"));
}
```

---

## Versioning Strategy

**Current version**: `chat_api.v1`

**Backward-compatible changes** (can be added without version bump):
- Add optional fields to existing messages
- Add new RPC methods to existing services
- Add new enum values (must not change existing values)

**Breaking changes** (require new version `chat_api.v2`):
- Rename or delete fields
- Change field types
- Change field numbers
- Rename RPC methods

**Migration path**:
1. Implement `chat_api.v2` in new proto files (`chat_service_v2.proto`)
2. Keep `chat_api.v1` running (dual versioning)
3. Deprecate v1 after 3 months, providing migration guide

---

## Alignment with Spec API Examples

These Protobuf definitions are **gRPC equivalents** of the REST API examples in `spec.md` (Section: API Contract Examples). Mapping:

| REST Endpoint | gRPC Method | Protobuf File |
|---------------|-------------|---------------|
| `POST /v1/messages` | `ChatService.SendMessage` | `chat_service.proto` |
| `GET /v1/conversations/{id}/messages` | `ConversationService.GetConversationHistory` | `conversation_service.proto` |
| `POST /v1/conversations` | `ConversationService.CreateConversation` | `conversation_service.proto` |
| `GET /v1/conversations` | `ConversationService.ListConversations` | `conversation_service.proto` |

**Note**: File upload endpoints (P2) will use REST with tus protocol, not gRPC (large binary transfers better suited to HTTP chunking).

---

## Next Steps

1. Copy proto files to `src/main/proto/` in Java project
2. Run `mvn compile` to generate Java classes
3. Implement service stubs in `adapter/in/grpc/` (e.g., `ChatServiceGrpcImpl.java`)
4. Write contract tests in `test/java/.../contract/`
5. Enable gRPC Server Reflection for runtime API discovery (per NFR-023)
