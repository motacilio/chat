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

**POC Services** (4 containers):
- RabbitMQ (message broker)
- MongoDB (persistence)
- MongoDB Replica Set Initializer (one-time setup)
- Chat API (Spring Boot gRPC server)

```bash
# Start all services in background
docker-compose up -d

# Wait for RabbitMQ + MongoDB to initialize (~30 seconds)
docker-compose logs -f mongodb

# When you see "waiting for connections on port 27017", services are ready
# Press Ctrl+C to exit logs
```

**Verify services**:
```bash
docker-compose ps

# Expected output:
# NAME                  STATUS
# chat-api              Up
# rabbitmq              Up (healthy)
# mongodb               Up
# mongodb-init          Exited (0)
```

**Access UIs**:
- RabbitMQ Management: http://localhost:15672 (guest/guest)
- MongoDB: localhost:27017 (use MongoDB Compass)

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

**Test ChatService.SendMessage**:
```bash
# Send text message
grpcurl -plaintext \
  -d '{
    "message_id": "550e8400-e29b-41d4-a716-446655440001",
    "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
    "sender_id": "user123",
    "message_text": "Hello from gRPC!"
  }' \
  localhost:9090 \
  chat_api.v1.ChatService/SendMessage

# Expected response:
# {
#   "messageId": "550e8400-e29b-41d4-a716-446655440001",
#   "status": "SENT",
#   "timestamp": "2025-11-18T10:30:00Z",
#   "sequenceNumber": "1"
# }
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

## Docker Compose Configuration

**File**: `docker-compose.yml` (POC version)

```yaml
version: '3.8'

services:
  # MongoDB (Persistence)
  mongodb:
    image: mongo:7.0
    container_name: mongodb
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: password
    volumes:
      - mongodb_data:/data/db
    command: ["--replSet", "rs0"]
    healthcheck:
      test: ["CMD", "mongo", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5

  # MongoDB Replica Set Initializer (one-time)
  mongodb-init:
    image: mongo:7.0
    container_name: mongodb-init
    depends_on:
      - mongodb
    command: >
      bash -c "
        sleep 10 &&
        mongo --host mongodb:27017 -u admin -p password --authenticationDatabase admin --eval '
          rs.initiate({
            _id: \"rs0\",
            members: [{ _id: 0, host: \"mongodb:27017\" }]
          })
        '
      "

  # RabbitMQ (Message Broker)
  rabbitmq:
    image: rabbitmq:3.12-management
    container_name: rabbitmq
    ports:
      - "5672:5672"    # AMQP
      - "15672:15672"  # Management UI
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Chat API (Spring Boot gRPC Server)
  chat-api:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: chat-api
    ports:
      - "9090:9090"  # gRPC
      - "8080:8080"  # Spring Boot Actuator (health checks)
    environment:
      SPRING_PROFILES_ACTIVE: dev
      MONGODB_URI: mongodb://admin:password@mongodb:27017/chat?authSource=admin&replicaSet=rs0
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: guest
      RABBITMQ_PASSWORD: guest
    depends_on:
      rabbitmq:
        condition: service_healthy
      mongodb:
        condition: service_healthy
    restart: unless-stopped

volumes:
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
      uri: ${MONGODB_URI}
      database: chat
  
  # RabbitMQ Configuration
  rabbitmq:
    host: ${RABBITMQ_HOST}
    port: ${RABBITMQ_PORT}
    username: ${RABBITMQ_USERNAME}
    password: ${RABBITMQ_PASSWORD}
    listener:
      simple:
        concurrency: 5          # Number of concurrent workers
        max-concurrency: 10
        prefetch: 10            # Messages per worker (backpressure)
        acknowledge-mode: manual  # Manual acknowledgment for at-least-once delivery

# gRPC Server Configuration
grpc:
  server:
    port: 9090
    enable-reflection: true  # Enable gRPC Server Reflection (for grpcurl)

# Logging Configuration
logging:
  level:
    com.chat: DEBUG
    org.springframework.amqp: INFO
    io.grpc: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %logger{36} - %msg%n"

# Actuator (Health Checks)
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

---

## Running Tests

### Unit Tests (Domain Logic)

```bash
# Run all unit tests
mvn test

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
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0");
    
    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management");
    
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
        
        // Wait for async worker processing
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

# Or change port in docker-compose.yml
ports:
  - "27018:27017"  # Use different host port
```

### Issue: "gRPC connection refused"

**Cause**: Chat API not started or crashed

**Solution**:
```bash
# Check container logs
docker-compose logs chat-api

# Look for errors like:
# "Failed to bind to port 9090" → Port conflict
# "MongoTimeoutException" → MongoDB not ready
# "AmqpConnectException" → RabbitMQ not ready

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
# - Memory: 4 GB minimum
# - CPUs: 2 cores minimum

# Pull required images before tests
docker pull mongo:7.0
docker pull rabbitmq:3.12-management
```

---

## Next Steps

After POC is running:

1. **Explore RabbitMQ Management UI**:
   - View message queues: http://localhost:15672/#/queues
   - Check message delivery rates
   - Monitor worker connections

2. **Query MongoDB**:
   ```bash
   # Connect with MongoDB Compass: mongodb://admin:password@localhost:27017/chat?authSource=admin
   # Or use mongo shell:
   docker exec -it mongodb mongo -u admin -p password --authenticationDatabase admin
   use chat
   db.messages.find().pretty()
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

4. **Read Architecture Documentation**:
   - `specs/001-ubiquitous-messaging-platform/research.md` - Architectural decisions
   - `specs/001-ubiquitous-messaging-platform/data-model.md` - MongoDB schema design
   - `.specify/memory/constitution.md` - Development principles (TDD, hexagonal architecture, etc.)

5. **Run Full Test Suite**:
   ```bash
   # All tests (unit + integration + contract)
   mvn clean verify
   
   # View coverage report
   mvn jacoco:report
   open target/site/jacoco/index.html  # macOS
   xdg-open target/site/jacoco/index.html  # Linux
   start target/site/jacoco/index.html  # Windows
   ```

---

## Resources

- **gRPC Documentation**: https://grpc.io/docs/languages/java/
- **Spring Boot with gRPC**: https://github.com/LogNet/grpc-spring-boot-starter
- **Testcontainers**: https://www.testcontainers.org/
- **RabbitMQ Tutorials**: https://www.rabbitmq.com/getstarted.html
- **MongoDB University (Free)**: https://university.mongodb.com/

---

## Support

For issues or questions:
1. Check existing issues: https://github.com/motacilio/chat/issues
2. Create new issue with POC logs: `docker-compose logs > poc-logs.txt`
3. Review constitution for development workflow: `.specify/memory/constitution.md`
