# Quickstart Guide: Ubiquitous Messaging Platform POC

**Feature**: Ubiquitous Messaging Platform  
**Version**: POC (Phase 1 - MVP)  
**Target**: Educational distributed systems project  
**Updated**: 2025-11-18

This guide helps you get the POC running locally in <10 minutes.

---

## Prerequisites

- **Java 17 (LTS)** - Download: https://adoptium.net/
- **Maven 3.9+** - Download: https://maven.apache.org/download.cgi
- **Docker Desktop** - Download: https://www.docker.com/products/docker-desktop
- **Git** - Download: https://git-scm.com/downloads

**Verify installations**:
```bash
java -version    # Should show Java 17.x.x
mvn -version     # Should show Maven 3.9.x
docker --version # Should show Docker 20.x+
```

---

## Quick Start (POC Mode)

### Step 1: Clone Repository

```bash
git clone https://github.com/motacilio/chat.git
cd chat
git checkout 001-ubiquitous-messaging-platform
```

### Step 2: Start Infrastructure (Docker Compose)

**Services** (10 containers):
- **MongoDB Replica Set** (3-node cluster for high availability)
  - mongodb-primary (port 27017)
  - mongodb-secondary1 (port 27018)
  - mongodb-secondary2 (port 27019)
  - mongodb-init (one-time replica set initializer)
- **Apache Kafka** (message broker - port 9092)
- **Zookeeper** (Kafka coordination - port 2181)
- **MinIO** (S3-compatible object storage - ports 9000/9001)
- **Chat API** (Spring Boot gRPC server - ports 9090/8081)
- **Kafka UI** (optional management interface - port 8080)

```bash
# Start all services in background
docker-compose up -d

# Wait for Kafka + MongoDB to initialize (~30 seconds)
docker-compose logs -f kafka

# When you see "[KafkaServer id=1] started", services are ready
# Press Ctrl+C to exit logs
```

**Verify services**:
```bash
docker-compose ps

# Expected output:
# NAME                  STATUS
# chat-api              Up
# kafka                 Up (healthy)
# zookeeper             Up
# mongodb               Up
# mongodb-init          Exited (0)
```

**Access UIs**:
- **Kafka UI**: http://localhost:8080 (Kafka topics, consumer groups, message browser)
- **MinIO Console**: http://localhost:9001 (login: minioadmin/minioadmin)
- **MongoDB Primary**: localhost:27017 (use MongoDB Compass - connection string below)
- **Spring Boot Actuator**: http://localhost:8081/actuator/health (health checks, metrics)

### Step 3: Build Java Application

```bash
# Clean build with tests
mvn clean install

# Skip tests (faster, for quick iteration)
mvn clean install -DskipTests
```

**Expected output**:
```
[INFO] BUILD SUCCESS
[INFO] Total time: 45 s
```

### Step 4: Run Application

**Option A: Docker Compose** (recommended for POC)
```bash
# Rebuild and restart Chat API container
docker-compose up --build chat-api
```

**Option B: Local JVM** (for debugging)
```bash
# Run Spring Boot application directly
mvn spring-boot:run

# Or run built JAR
java -jar target/chat-api-1.0.0-SNAPSHOT.jar
```

**Verify startup**:
```bash
# Check logs for successful gRPC server start
docker-compose logs chat-api | grep "gRPC Server started"

# Expected output:
# chat-api | gRPC Server started, listening on port 9090
```

### Step 5: Test API (gRPC)

**Install grpcurl** (gRPC curl equivalent):
```bash
# macOS
brew install grpcurl

# Windows (PowerShell)
choco install grpcurl

# Linux
wget https://github.com/fullstorydev/grpcurl/releases/download/v1.8.9/grpcurl_1.8.9_linux_x86_64.tar.gz
tar -xvf grpcurl_1.8.9_linux_x86_64.tar.gz
sudo mv grpcurl /usr/local/bin/
```

**Test ChatService.SendMessage** (with auto-conversation creation):
```bash
# Send first message - conversation will be auto-created
grpcurl -plaintext \
  -d '{
    "message_id": "550e8400-e29b-41d4-a716-446655440001",
    "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
    "sender_id": "user-alice-uuid",
    "recipient_id": "user-bob-uuid",
    "message_text": "Hello from gRPC!"
  }' \
  localhost:9090 \
  chat_api.v1.ChatService/SendMessage

# Expected response:
# {
#   "messageId": "550e8400-e29b-41d4-a716-446655440001",
#   "status": "SENT",
#   "timestamp": "2025-11-22T10:30:00Z",
#   "sequenceNumber": "1"
# }

# Note: If conversation doesn't exist, it will be auto-created with both sender_id and recipient_id as participants (per FR-004a)
```

**List available services** (gRPC Server Reflection):
```bash
grpcurl -plaintext localhost:9090 list

# Expected output:
# chat_api.v1.ChatService
# chat_api.v1.ConversationService
# grpc.health.v1.Health
# grpc.reflection.v1alpha.ServerReflection
```

**Describe service methods**:
```bash
grpcurl -plaintext localhost:9090 describe chat_api.v1.ChatService

# Shows all RPC methods with request/response types
```

---

**File**: `docker-compose.yml` (Production-Ready Configuration)

See actual file in repository root for complete configuration. Key services:

```yaml
version: '3.8'

services:
  # MongoDB Replica Set (3-node cluster)
  mongodb-primary:
    image: mongo:7.0
    container_name: mongodb-primary
    command: ["--replSet", "rs0", "--bind_ip_all", "--port", "27017"]
    ports:
      - "27017:27017"
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5

  mongodb-secondary1:
    image: mongo:7.0
    container_name: mongodb-secondary1
    ports:
      - "27018:27017"
    # ... (see docker-compose.yml for full config)

  mongodb-secondary2:
    image: mongo:7.0
    container_name: mongodb-secondary2
    ports:
      - "27019:27017"
    # ... (see docker-compose.yml for full config)

  # Replica set initializer (runs once)
  mongodb-init:
    image: mongo:7.0
    container_name: mongodb-init
    command: >
      mongosh --host mongodb-primary:27017 --eval "
      rs.initiate({
        _id: 'rs0',
        members: [
          { _id: 0, host: 'mongodb-primary:27017', priority: 2 },
          { _id: 1, host: 'mongodb-secondary1:27017', priority: 1 },
          { _id: 2, host: 'mongodb-secondary2:27017', priority: 1 }
        ]
      });
      "
    restart: "no"

  # Kafka + Zookeeper
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      # ... (see docker-compose.yml for full config)

  # MinIO (S3-compatible storage)
  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"  # S3 API
      - "9001:9001"  # Console UI
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"

  # Chat API
  chat-api:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "9090:9090"  # gRPC
      - "8081:8081"  # Actuator
    environment:
      SPRING_PROFILES_ACTIVE: docker
      MONGODB_URI: mongodb://mongodb-primary:27017,mongodb-secondary1:27017,mongodb-secondary2:27017/chat?replicaSet=rs0
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092

  # Kafka UI
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    ports:
      - "8080:8080"
```

**See `docker-compose.yml`, `docker-compose.dev.yml`, and `docker-compose.monitoring.yml` for full configurations**umes:
  mongodb_data:
```

---

## Application Configuration

**File**: `src/main/resources/application-dev.yml` (Docker Compose profile)

```yaml
spring:
  application:
    name: chat-api
  
  # MongoDB Configuration
  data:
    mongodb:
**File**: `src/main/resources/application-dev.yml` (Local Development)

See actual file for complete configuration. Key settings:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/chat
      database: chat
  
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: message-consumer-group
      auto-offset-reset: earliest
      enable-auto-commit: false  # Manual offset commits for at-least-once delivery
      max.poll.records: 10  # Backpressure control
    producer:
      acks: all  # Wait for all replicas
    listener:
      ack-mode: manual

# MinIO Configuration (File Upload)
minio:
  endpoint: http://localhost:9000
  external-endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket-name: chat-files
  download-url-expiration-seconds: 3600  # 1 hour
  chunk-size-bytes: 5242880  # 5 MB chunks

# gRPC Server
grpc:
  server:
    port: 9090
    enable-reflection: true

# Logging
logging:
  level:
    com.chat: DEBUG
    org.apache.kafka: INFO
    org.springframework.data.mongodb: DEBUG
    io.minio: DEBUG
```

**See `application-dev.yml` and `application-docker.yml` for full configurations** test

# Run specific test class
mvn test -Dtest=MessageServiceTest
```

**Example**:
```java
// src/test/java/com/chat/domain/service/MessageServiceTest.java
@Test
public void shouldValidateMessageIdFormatBeforeSubmission() {
    MessageService service = new MessageService();
    assertThrows(IllegalArgumentException.class, () -> {
        service.validateMessageId("invalid-uuid");
    });
}
```

### Integration Tests (Testcontainers)

**POC Validation Test** (validates API → RabbitMQ → MongoDB flow):

```bash
# Run integration tests (requires Docker running)
mvn verify -P integration-tests
```

**Example**:
```java
// src/test/java/com/chat/integration/MessageFlowIntegrationTest.java
@Testcontainers
@SpringBootTest
public class MessageFlowIntegrationTest {
    
    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0")
        .withExposedPorts(27017);
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
        .withExposedPorts(9092);
    
    @Test
    public void shouldDeliverMessageFromApiToMongoDB() {
        // Given: gRPC client connected to Chat API
        ChatServiceGrpc.ChatServiceBlockingStub client = ...;
        
        // When: Send message via gRPC API
        SendMessageResponse response = client.sendMessage(
            SendMessageRequest.newBuilder()
                .setMessageId("550e8400-e29b-41d4-a716-446655440001")
                .setConversationId("550e8400-e29b-41d4-a716-446655440000")
                .setSenderId("user123")
                .setMessageText("Hello POC!")
                .build()
        );
        
        // Then: Verify message persisted in MongoDB
        assertEquals(MessageStatus.SENT, response.getStatus());
        
        // Wait for async Kafka worker processing
        Thread.sleep(2000);
        
        // Query MongoDB directly
        Message persistedMessage = messageRepository.findByMessageId("550e8400-...");
        assertNotNull(persistedMessage);
        assertEquals("Hello POC!", persistedMessage.getMessageText());
    }
}
```

### Contract Tests (Protobuf Schemas)

```bash
# Run contract tests
mvn test -Dtest=ChatServiceContractTest
```

**Example**:
```java
// src/test/java/com/chat/contract/ChatServiceContractTest.java
@Test
public void shouldHaveRequiredFieldsInSendMessageRequest() {
    SendMessageRequest request = SendMessageRequest.newBuilder()
        .setMessageId("uuid")
        .setConversationId("uuid")
        .setSenderId("uuid")
        .setMessageText("Hello")
        .build();
    
    assertFalse(request.getMessageId().isEmpty());
    assertFalse(request.getConversationId().isEmpty());
    assertFalse(request.getSenderId().isEmpty());
    assertFalse(request.getMessageText().isEmpty());
}
```

---

## Troubleshooting

### Issue: "Port 27017 already in use"

**Cause**: Existing MongoDB running locally

**Solution**:
```bash
# Stop local MongoDB
sudo systemctl stop mongod  # Linux
brew services stop mongodb-community  # macOS

# Or use different ports (quickstart already uses 27017, 27018, 27019)
# See docker-compose.yml - 3 MongoDB nodes on separate ports
```

### Issue: "gRPC connection refused"

**Cause**: Chat API not started or crashed

**Solution**:
```bash
# Check container logs
docker-compose logs chat-api

# Look for errors like:
# "Failed to bind to port 9090" → Port conflict
# "MongoTimeoutException" → MongoDB replica set not ready
# "TimeoutException: Topic not present" → Kafka not ready

# Restart services
docker-compose down
docker-compose up -d
```

### Issue: "Tests fail with Testcontainers"

**Cause**: Docker not running or insufficient resources

**Solution**:
```bash
# Verify Docker running
docker ps

# Increase Docker resources (Docker Desktop → Settings → Resources):
# - Memory: 8 GB minimum (replica set + Kafka)
# - CPUs: 4 cores minimum

# Pull required images before tests
docker pull mongo:7.0
docker pull confluentinc/cp-kafka:7.5.0
docker pull minio/minio:latest
```

---

## Next Steps

After POC is running:

1. **Explore Kafka UI**:
   - View topics and messages: http://localhost:8080
   - Check consumer groups (message-delivery-group, state-update-group)
   - Monitor consumer lag and throughput
   - Browse messages in `message-events`, `state-update-events`, `platform-message-events` topics

2. **Explore MinIO Console**:
   - View uploaded files: http://localhost:9001 (login: minioadmin/minioadmin)
   - Check `chat-files` bucket
   - Monitor storage usage

3. **Query MongoDB**:
   ```bash
   # Connect with MongoDB Compass: mongodb://localhost:27017/chat?replicaSet=rs0
   # Or use mongosh:
   docker exec -it mongodb-primary mongosh
   use chat
   db.messages.find().pretty()
   db.conversations.find().pretty()
   db.fileMetadata.find().pretty()
   
   # Check replica set status
   rs.status()
   ```

3. **Test Real-Time Streaming**:
   ```bash
   # Subscribe to message stream
   grpcurl -plaintext \
     -d '{"user_id": "user456"}' \
     localhost:9090 \
     chat_api.v1.ChatService/StreamMessages
   
   # In another terminal, send message to trigger stream event
   grpcurl -plaintext -d '{...}' localhost:9090 chat_api.v1.ChatService/SendMessage
   ```

4. **Test File Upload**:
   ```bash
   # Initiate file upload
   grpcurl -plaintext \
     -d '{
       "filename": "test.pdf",
       "content_type": "application/pdf",
       "file_size_bytes": 1048576,
       "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
       "sender_id": "user-alice-uuid"
     }' \
     localhost:9090 \
     chat_api.v1.FileService/InitiateUpload
   
   # Response includes upload_url (MinIO pre-signed URL) and file_id
   # Upload file to upload_url using curl/Postman, then complete:
   
   grpcurl -plaintext \
     -d '{
       "file_id": "file-uuid-from-initiate",
       "checksum_sha256": "computed-hash"
     }' \
     localhost:9090 \
     chat_api.v1.FileService/CompleteUpload
   ```

5. **Monitor with Prometheus + Grafana** (Optional - requires `docker-compose.monitoring.yml`):
   ```bash
   # Start monitoring stack
   docker-compose -f docker-compose.monitoring.yml up -d
   
   # Access dashboards
   # Prometheus: http://localhost:9090/targets (check chat-api target)
   # Grafana: http://localhost:3000 (login: admin/admin)
   #   - Import dashboard from docs/observabilidade/grafana-dashboard-basic.json
   #   - View metrics: messages_sent_total, kafka_consumer_lag, circuit_breaker_state
   ```

6. **Run Performance Benchmarks**:
   ```powershell
   # Quick test (1,000 users, 5 minutes)
   cd scripts\load-test
   .\run-benchmark.ps1 -Quick
   
   # Full benchmark (10,000 users, 17 minutes) - validates NFR-003
   .\run-benchmark.ps1 -ApiUrl http://localhost:8081
   
   # View results
   cat results\summary.json
   # Expected: p95 < 100ms, throughput > 1000 msg/s
   ```

7. **Read Architecture Documentation**:
   - **Consolidated Docs** (DOC_REVISADA/): Complete implementation guides for all features
   - `specs/001-ubiquitous-messaging-platform/research.md` - Architectural decisions (CQRS, Kafka, MongoDB)
   - `specs/001-ubiquitous-messaging-platform/data-model.md` - MongoDB schema design
   - `docs/architecture/system-overview.md` - High-level architecture and component interactions
8. **Run Full Test Suite**:
   ```bash
   # All tests (unit + integration + contract)
   mvn clean verify
   
   # View coverage report
   mvn jacoco:report
   open target/site/jacoco/index.html  # macOS
   xdg-open target/site/jacoco/index.html  # Linux
   start target\site\jacoco\index.html  # Windows PowerShell
   
   # Expected: >80% coverage (current: 85%)
   ```

9. **Explore Multi-Platform Integration** (Phase 8 - Mock Adapters):
   ```bash
   # Send message to Telegram
   grpcurl -plaintext \
     -d '{
       "message_id": "msg-uuid",
       "conversation_id": "conv-uuid",
       "sender_id": "user-alice",
       "platform_conversation_id": "telegram-chat-123",
       "platform": "TELEGRAM",
       "message_text": "Hello Telegram!"
     }' \
     localhost:9090 \
     chat_api.v1.PlatformService/SendToExternalPlatform
   
   # Check Kafka topics
   docker exec -it kafka kafka-console-consumer \
     --bootstrap-server localhost:9092 \
     --topic platform-message-events \
     --from-beginning
   
   # Monitor webhook callbacks (simulated delivery confirmations)
   docker-compose logs chat-api | Select-String "WebhookTriggerService"
   ```iew coverage report
   mvn jacoco:report
   open target/site/jacoco/index.html  # macOS
   xdg-open target/site/jacoco/index.html  # Linux
   start target/site/jacoco/index.html  # Windows
   ```

---

## Resources

- **gRPC Documentation**: https://grpc.io/docs/languages/java/
- **Spring Boot with gRPC**: https://yidongnan.github.io/grpc-spring-boot-starter/
- **Apache Kafka**: https://kafka.apache.org/documentation/
- **Spring Kafka**: https://spring.io/projects/spring-kafka
- **MongoDB Replica Sets**: https://www.mongodb.com/docs/manual/replication/
- **MinIO Documentation**: https://min.io/docs/minio/linux/index.html
- **Testcontainers**: https://www.testcontainers.org/
- **Prometheus + Grafana**: https://prometheus.io/docs/visualization/grafana/

---

## Support

For issues or questions:
1. Check existing issues: https://github.com/motacilio/chat/issues
2. Create new issue with POC logs: `docker-compose logs > poc-logs.txt`
3. Review constitution for development workflow: `.specify/memory/constitution.md`
