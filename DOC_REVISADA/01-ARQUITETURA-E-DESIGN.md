# 01 - Arquitetura e Design do Sistema

**Versão**: 1.0  
**Última Atualização**: 29/11/2025  
**Status**: ✅ Implementado

---

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Decisões Arquiteturais](#decisões-arquiteturais)
3. [Componentes do Sistema](#componentes-do-sistema)
4. [Fluxo de Mensagens](#fluxo-de-mensagens)
5. [Tecnologias Utilizadas](#tecnologias-utilizadas)
6. [Trade-offs e Justificativas](#trade-offs-e-justificativas)

---

## Visão Geral

### Objetivo do Sistema

Plataforma de mensagens distribuída capaz de:
- Enviar e receber mensagens de texto em conversas 1:1 e grupos
- Upload/download de arquivos até 2 GB
- Rastreamento de estado de mensagens (SENT → DELIVERED → READ)
- Streaming em tempo real para usuários online
- Integração com plataformas externas (WhatsApp, Instagram, Telegram)

### Arquitetura de Alto Nível

```
┌────────────────────────────────────────────────────────────────┐
│                    CAMADA DE CLIENTE                            │
│  Mobile Apps, Web Apps (gRPC clients)                          │
└─────────────────────┬──────────────────────────────────────────┘
                      │ gRPC/HTTP2 (porta 9090)
                      │ HTTP/REST (porta 8081)
┌─────────────────────▼──────────────────────────────────────────┐
│                  CHAT API (Spring Boot)                         │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  gRPC Services  │  │  REST Controllers│ │  StreamingService│ │
│  │  (porta 9090)   │  │  (porta 8081)   │ │  (in-memory)     │ │
│  └────────┬────────┘  └────────┬────────┘  └─────────────────┘ │
│           │                    │                                │
│  ┌────────▼────────────────────▼────────┐                       │
│  │      Business Services               │                       │
│  │  (MessageService, ConversationService)│                      │
│  └────────┬─────────────────────────────┘                       │
│           │                                                      │
│  ┌────────▼────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  Kafka Producer │  │  MongoDB     │  │  MinIO       │       │
│  │  (async events) │  │  Repository  │  │  Client      │       │
│  └────────┬────────┘  └──────┬───────┘  └──────┬───────┘       │
└───────────┼────────────────────┼─────────────────┼──────────────┘
            │                    │                 │
┌───────────▼────────┐  ┌────────▼───────┐  ┌─────▼────────┐
│   Apache Kafka     │  │    MongoDB     │  │    MinIO     │
│  (message broker)  │  │  (persistence) │  │  (storage)   │
└───────────┬────────┘  └────────────────┘  └──────────────┘
            │
┌───────────▼────────────────────────────────────────────────┐
│               KAFKA CONSUMERS (Workers)                     │
├─────────────────────────────────────────────────────────────┤
│  MessageDeliveryWorker     │  MessageStateUpdateWorker     │
│  WhatsAppMessageWorker     │  InstagramMessageWorker       │
└─────────────────────────────────────────────────────────────┘
```

---

## Decisões Arquiteturais

### 📌 Decision 1: gRPC com Protobuf (API Layer)

**Escolhido**: gRPC com Protocol Buffers 3.25.3

#### Como Está Implementado

**Arquivo**: `src/main/java/com/chat/grpc/ChatServiceImpl.java`
```java
@GRpcService
public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {
    
    @Override
    public void sendMessage(SendMessageRequest request, 
                           StreamObserver<SendMessageResponse> responseObserver) {
        // Implementação usando Protobuf types
    }
    
    @Override
    public void streamMessages(SubscribeRequest request,
                              StreamObserver<MessageEvent> responseObserver) {
        // Server-side streaming para tempo real
    }
}
```

**Configuração**: `application.yml`
```yaml
grpc:
  server:
    port: 9090
    enable-reflection: true  # Runtime API discovery
```

#### Por Que Foi Decidido Assim

**Vantagens**:
1. **30-40% menor latência** que REST/JSON (binário vs texto)
2. **Type safety** em compile-time via Protobuf
3. **Streaming nativo** (server-side, client-side, bidirecional)
4. **Code generation** automática (Java, Python, Go, etc.)
5. **HTTP/2** multiplexing (múltiplos requests em 1 TCP connection)

**Comparado com REST**:
- REST/JSON: ~250 bytes por mensagem
- gRPC/Protobuf: ~100 bytes (60% menor)
- Serialização: 2-3x mais rápida

**Referência**: `specs/001-ubiquitous-messaging-platform/research.md` - Decision 1

---

### 📌 Decision 2: Apache Kafka (Message Broker)

**Escolhido**: Apache Kafka 7.5.0 com Protocol Buffers serialization

#### Como Está Implementado

**Producer Configuration**: `src/main/java/com/chat/config/KafkaProducerConfig.java`
```java
@Configuration
public class KafkaProducerConfig {
    
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ProtobufSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");  // Durability
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(configProps);
    }
}
```

**Consumer Configuration**: `src/main/java/com/chat/config/KafkaConsumerConfig.java`
```java
@Configuration
public class KafkaConsumerConfig {
    
    @Bean
    public ConsumerFactory<String, MessageEvent> messageEventConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
            baseConsumerConfig(),
            new StringDeserializer(),
            new ProtobufDeserializer<>(MessageEvent.parser())  // Type-safe
        );
    }
}
```

**Tópicos Kafka**:
- `message-events` - Mensagens enviadas pelos clientes
- `state-update-events` - Atualizações de status (DELIVERED, READ)
- `whatsapp-messages` - Mensagens roteadas para WhatsApp
- `instagram-messages` - Mensagens roteadas para Instagram

#### Por Que Foi Decidido Assim

**Vantagens**:
1. **At-least-once delivery** via manual offset commits
2. **Partition-based ordering** (conversation_id como partition key)
3. **High throughput** (milhões de msg/s com horizontal scaling)
4. **Log persistence** (replay capability, durability)
5. **Consumer groups** (distribui carga automaticamente)

**Comparado com Alternativas**:
- **vs RabbitMQ**: Kafka tem 100x+ throughput (10K vs 1M+ msg/s)
- **vs AWS SQS**: Kafka evita vendor lock-in, ensina conceitos
- **vs Redis Streams**: Kafka tem melhor durabilidade e clustering

**Referência**: `specs/001-ubiquitous-messaging-platform/research.md` - Decision 2

---

### 📌 Decision 3: MongoDB (Persistence)

**Escolhido**: MongoDB 7.0 com embedded documents

#### Como Está Implementado

**Entity**: `src/main/java/com/chat/model/Message.java`
```java
@Document(collection = "messages")
public class Message {
    @Id
    private ObjectId id;
    
    @Indexed(unique = true)
    private String messageId;  // UUID para idempotency
    
    @Indexed
    private String conversationId;
    
    private String senderId;
    private String messageText;
    private Instant timestamp;
    private Long sequenceNumber;
    
    // Embedded array - evita JOINs
    private List<MessageStateTransition> stateHistory;
}
```

**Repository**: `src/main/java/com/chat/repository/MessageRepository.java`
```java
public interface MessageRepository extends MongoRepository<Message, ObjectId> {
    Optional<Message> findByMessageId(String messageId);
    
    @Query("{ 'conversationId': ?0 }")
    List<Message> findByConversationIdOrderByTimestampDesc(
        String conversationId, Pageable pageable);
}
```

**Índices MongoDB**:
```javascript
db.messages.createIndex({ "message_id": 1 }, { unique: true });
db.messages.createIndex({ "conversation_id": 1, "timestamp": -1 });
db.conversations.createIndex({ "participants": 1, "last_message_at": -1 });
```

#### Por Que Foi Decidido Assim

**Vantagens**:
1. **Schema flexível** (suporta texto, arquivos, voice - sem migrations)
2. **Embedded documents** (stateHistory array) - sem JOINs, queries rápidas
3. **Write concern: majority** (durabilidade em replica set)
4. **Índices compostos** (<200ms para 50 mensagens paginadas)

**Trade-off Embedded vs Referenced**:
- ✅ **Embedded** (escolhido): 1 query, atômico, mais rápido
- ❌ **Referenced**: Requer $lookup (JOIN), mais lento

**Referência**: `specs/001-ubiquitous-messaging-platform/research.md` - Decision 3

---

## Componentes do Sistema

### 1. API Layer (gRPC + REST)

| Componente | Porta | Tecnologia | Responsabilidade |
|------------|-------|------------|------------------|
| **ChatServiceImpl** | 9090 | gRPC | SendMessage, StreamMessages, MarkAsRead |
| **ConversationServiceImpl** | 9090 | gRPC | CreateConversation, ListConversations |
| **WebhookController** | 8081 | REST | Recebe callbacks de plataformas externas |
| **FileController** | 8081 | REST | Upload/download de arquivos |

**Arquivo**: `src/main/java/com/chat/ChatApiApplication.java`
```java
@SpringBootApplication
@EnableAsync
public class ChatApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatApiApplication.class, args);
    }
}
```

### 2. Message Broker (Kafka)

**Configuração**: `docker-compose.yml`
```yaml
kafka:
  image: confluentinc/cp-kafka:7.5.0
  environment:
    KAFKA_BROKER_ID: 1
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1  # Dev: 1, Prod: 3
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

**Partition Strategy**:
- **Key**: `conversation_id` (garante ordem de mensagens na mesma conversa)
- **Partitions**: 10 (permite até 10 consumers paralelos)
- **Replication**: 1 (dev), 3 (produção)

### 3. Persistence Layer (MongoDB)

**Configuração**: `src/main/java/com/chat/config/MongoConfig.java`
```java
@Configuration
public class MongoConfig {
    
    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory) {
        MongoTemplate template = new MongoTemplate(factory);
        template.setWriteConcern(WriteConcern.MAJORITY);  // Durability
        template.setWriteResultChecking(WriteResultChecking.EXCEPTION);
        return template;
    }
}
```

**Collections**:
- `messages` - Mensagens e histórico de estados
- `conversations` - Conversas 1:1 e grupos
- `users` - Informações de usuários

### 4. Workers (Kafka Consumers)

| Worker | Tópico Consume | Responsabilidade |
|--------|----------------|------------------|
| **MessageDeliveryWorker** | message-events | Persiste mensagens no MongoDB |
| **MessageStateUpdateWorker** | state-update-events | Atualiza status (DELIVERED/READ) |
| **WhatsAppMessageWorker** | whatsapp-messages | Envia para WhatsApp mock |
| **InstagramMessageWorker** | instagram-messages | Envia para Instagram mock |

**Arquivo**: `src/main/java/com/chat/worker/MessageDeliveryWorker.java`
```java
@Component
public class MessageDeliveryWorker {
    
    @KafkaListener(
        topics = "message-events",
        groupId = "message-delivery-workers",
        containerFactory = "messageEventKafkaListenerContainerFactory"
    )
    public void handleMessageEvent(MessageEvent event, Acknowledgment ack) {
        // Persiste no MongoDB
        messageRepository.save(message);
        
        // Notifica usuários online via gRPC stream
        streamingService.notifyUserMessage(recipientId, grpcEvent);
        
        // Commit manual após sucesso
        ack.acknowledge();
    }
}
```

---

## Fluxo de Mensagens

### Fluxo 1: Envio de Mensagem (Síncrono + Assíncrono)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Cliente → gRPC SendMessage                               │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. ChatServiceImpl                                          │
│    - Valida request (UUID, participantes)                   │
│    - Gera sequence_number                                   │
│    - Publica MessageEvent no Kafka                          │
│    - Retorna 202 Accepted (async processing)                │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Kafka Topic: message-events                              │
│    - Partition key: conversation_id                         │
│    - Serialization: Protobuf                                │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. MessageDeliveryWorker (Kafka Consumer)                   │
│    - Consome da partição                                    │
│    - Verifica idempotency (messageId unique index)          │
│    - Persiste no MongoDB                                    │
│    - Notifica usuários online (gRPC stream)                 │
│    - Roteia para plataformas externas (Kafka)               │
│    - Commit offset após sucesso                             │
└─────────────────────┬───────────────────────────────────────┘
                      │
       ┌──────────────┴──────────────┐
       ▼                             ▼
┌──────────────┐            ┌────────────────────┐
│   MongoDB    │            │  Usuários Online   │
│  (persist)   │            │  (gRPC stream)     │
└──────────────┘            └────────────────────┘
```

### Fluxo 2: Streaming Tempo Real

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Cliente → StreamMessages (gRPC)                          │
│    - Abre conexão persistente                               │
│    - Registra stream no StreamingService                    │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. StreamingService (in-memory)                             │
│    - ConcurrentHashMap<userId, StreamObserver>              │
│    - Mantém streams ativos                                  │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼ (quando nova mensagem chega)
┌─────────────────────────────────────────────────────────────┐
│ 3. MessageDeliveryWorker                                    │
│    - Persiste mensagem                                      │
│    - streamingService.notifyUserMessage(userId, event)      │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. stream.onNext(MessageEvent)                              │
│    - Envia pelo HTTP/2 stream                               │
│    - Cliente recebe instantaneamente (<100ms)               │
└─────────────────────────────────────────────────────────────┘
```

---

## Tecnologias Utilizadas

### Stack Completo

| Camada | Tecnologia | Versão | Justificativa |
|--------|------------|--------|---------------|
| **Language** | Java | 17 LTS | LTS, performance, ecossistema |
| **Framework** | Spring Boot | 3.2.5 | Produção-ready, DI, auto-config |
| **API** | gRPC | 1.64.0 | Baixa latência, streaming |
| **Serialization** | Protobuf | 3.25.3 | Binário, type-safe, 60% menor |
| **Message Broker** | Apache Kafka | 7.5.0 | High throughput, durável |
| **Database** | MongoDB | 7.0 | Schema flexível, embedded docs |
| **Object Storage** | MinIO | Latest | S3-compatible, on-premise |
| **Auth** | Spring Security + JWT | 3.2.5 | OAuth2, resource server |
| **Observability** | Prometheus + Grafana | - | Métricas, dashboards |
| **Testing** | JUnit 5 + k6 + ghz | - | Unit, load, gRPC tests |

### Dependências Maven (pom.xml)

```xml
<properties>
    <java.version>17</java.version>
    <grpc.version>1.64.0</grpc.version>
    <protobuf.version>3.25.3</protobuf.version>
    <kafka.version>3.6.1</kafka.version>
</properties>

<dependencies>
    <!-- gRPC + Protobuf -->
    <dependency>
        <groupId>io.github.lognet</groupId>
        <artifactId>grpc-spring-boot-starter</artifactId>
        <version>3.1.0</version>
    </dependency>
    
    <!-- Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    
    <!-- MongoDB -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    
    <!-- Observability -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
</dependencies>
```

---

## Trade-offs e Justificativas

### 1. gRPC vs REST

| Aspecto | gRPC (Escolhido) | REST/JSON |
|---------|------------------|-----------|
| **Latência** | ✅ <100ms p95 | ❌ +30-40% |
| **Payload** | ✅ 100 bytes | ❌ 250 bytes |
| **Streaming** | ✅ Nativo | ⚠️ SSE/WebSocket |
| **Debug** | ⚠️ Binário | ✅ curl friendly |
| **Type Safety** | ✅ Compile-time | ❌ Runtime |

**Decisão**: gRPC para atingir <100ms p95 (NFR-012)

### 2. Kafka vs RabbitMQ

| Aspecto | Kafka (Escolhido) | RabbitMQ |
|---------|-------------------|----------|
| **Throughput** | ✅ 1M+ msg/s | ❌ 10K msg/s |
| **Ordering** | ✅ Per-partition | ⚠️ Per-queue |
| **Persistence** | ✅ Log-based | ⚠️ Memory-first |
| **Setup** | ⚠️ Complexo | ✅ Simples |
| **Replay** | ✅ Sim | ❌ Não |

**Decisão**: Kafka para escalar a milhões de usuários (NFR-001)

### 3. MongoDB vs PostgreSQL

| Aspecto | MongoDB (Escolhido) | PostgreSQL |
|---------|---------------------|------------|
| **Schema** | ✅ Flexível | ❌ Rígido |
| **Embedded** | ✅ Arrays nativos | ⚠️ JSONB |
| **Horizontal Scale** | ✅ Sharding nativo | ⚠️ Complexo |
| **ACID** | ⚠️ Document-level | ✅ Full ACID |
| **Joins** | ❌ $lookup | ✅ Nativo |

**Decisão**: MongoDB para schema evolutivo (texto → arquivos → voice)

### 4. Protobuf vs JSON (Kafka)

| Aspecto | Protobuf (Escolhido) | JSON |
|---------|----------------------|------|
| **Tamanho** | ✅ 100 bytes | ❌ 250 bytes |
| **Speed** | ✅ 1-2ms | ❌ 3-5ms |
| **Type Safety** | ✅ Schema | ❌ Sem schema |
| **Debug** | ❌ Binário | ✅ Texto |
| **Evolution** | ✅ Field numbers | ⚠️ Breaking changes |

**Decisão**: Protobuf para reduzir 60% de payload e 2-3x velocidade

---

## Referências

- **Decisões Arquiteturais**: `specs/001-ubiquitous-messaging-platform/research.md`
- **Data Model**: `specs/001-ubiquitous-messaging-platform/data-model.md`
- **Contratos gRPC**: `specs/001-ubiquitous-messaging-platform/contracts/`
- **Quickstart**: `specs/001-ubiquitous-messaging-platform/quickstart.md`
- **Código**: `src/main/java/com/chat/`

---

**Próximo Documento**: [02-API-GRPC-E-CONTRATOS.md](02-API-GRPC-E-CONTRATOS.md)
