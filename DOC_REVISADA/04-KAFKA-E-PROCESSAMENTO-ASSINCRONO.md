# 04 - Kafka e Processamento Assíncrono

**Versão**: 1.0  
**Última Atualização**: 29/11/2025  
**Status**: ✅ Implementado com Protobuf

---

## Visão Geral

### Objetivo

Kafka funciona como **message broker assíncrono** para desacoplar produção e consumo de eventos:
- **Produtores**: gRPC services (ChatServiceImpl) publicam eventos
- **Consumidores**: Workers processam eventos em background
- **Benefícios**: Escalabilidade horizontal, at-least-once delivery, ordem por partição

### Tópicos Kafka

| Tópico | Producers | Consumers | Partition Key | Propósito |
|--------|-----------|-----------|---------------|-----------|
| `message-events` | ChatServiceImpl | MessageDeliveryWorker | conversation_id | Persistir mensagens no MongoDB |
| `state-update-events` | ChatServiceImpl | MessageStateUpdateWorker | message_id | Atualizar status (DELIVERED/READ) |
| `whatsapp-messages` | MessageDeliveryWorker | WhatsAppMessageWorker | conversation_id | Rotear para WhatsApp API |
| `instagram-messages` | MessageDeliveryWorker | InstagramMessageWorker | conversation_id | Rotear para Instagram API |

---

## Contratos Protobuf (Kafka Events)

**Arquivo**: `src/main/proto/kafka_events.proto`

### MessageEvent

```protobuf
message MessageEvent {
  string message_id = 1;         // UUID (idempotência)
  string conversation_id = 2;    // Partition key
  string sender_id = 3;
  repeated string recipient_ids = 4;
  
  oneof content {
    string message_text = 5;     // Texto OU
    string file_id = 6;          // Arquivo
  }
  
  int64 sequence_number = 7;     // Ordem na conversa
  google.protobuf.Timestamp timestamp = 8;
}
```

### StateUpdateEvent

```protobuf
message StateUpdateEvent {
  string message_id = 1;         // Partition key
  
  enum MessageStatus {
    MESSAGE_STATUS_UNSPECIFIED = 0;
    SENT = 1;
    DELIVERED = 2;
    READ = 3;
  }
  MessageStatus new_status = 2;
  
  string user_id = 3;            // Quem acionou (recipient)
  google.protobuf.Timestamp timestamp = 4;
  string conversation_id = 5;
}
```

---

## Producer Configuration

**Arquivo**: `src/main/java/com/chat/config/KafkaProducerConfig.java`

```java
@Configuration
public class KafkaProducerConfig {
    
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Serializers: String (key) + Protobuf (value)
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, 
                       StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
                       ProtobufSerializer.class);
        
        // Durabilidade: acks=all (espera replicação)
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        
        // Idempotência: previne duplicatas em retries
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Retries: 3 tentativas com backoff 100ms
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    public KafkaTemplate<String, MessageEvent> messageEventKafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

**Por Que `acks=all`**:
- Espera replicação em todas as réplicas in-sync
- Previne perda de mensagens se broker falhar
- Trade-off: +5-10ms latência, mas garante durabilidade

---

## Consumer Configuration

**Arquivo**: `src/main/java/com/chat/config/KafkaConsumerConfig.java`

```java
@Configuration
public class KafkaConsumerConfig {
    
    @Bean
    public ConsumerFactory<String, MessageEvent> messageEventConsumerFactory() {
        Map<String, Object> configProps = baseConsumerConfig();
        
        // Deserializer Protobuf type-safe
        return new DefaultKafkaConsumerFactory<>(
            configProps,
            new StringDeserializer(),
            new ProtobufDeserializer<>(MessageEvent.parser())  // Type-safe!
        );
    }
    
    private Map<String, Object> baseConsumerConfig() {
        Map<String, Object> props = new HashMap<>();
        
        // Manual offset commit (at-least-once)
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Deserializers
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
                 StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
                 ProtobufDeserializer.class);
        
        return props;
    }
}
```

**Por Que Manual Offset Commit**:
- Worker processa mensagem → persiste MongoDB → commit offset
- Se erro antes commit → reprocessa (idempotência via `message_id` unique index)
- At-least-once delivery (vs at-most-once com auto-commit)

---

## Workers (Consumers)

### MessageDeliveryWorker

**Arquivo**: `src/main/java/com/chat/worker/MessageDeliveryWorker.java`

**Responsabilidade**: Consome `message-events`, persiste MongoDB, notifica usuários online

```java
@Component
public class MessageDeliveryWorker {
    
    @KafkaListener(
        topics = "message-events",
        groupId = "message-delivery-workers",
        containerFactory = "messageEventKafkaListenerContainerFactory"
    )
    public void handleMessageEvent(MessageEvent event, Acknowledgment ack) {
        try {
            String messageId = event.getMessageId();
            String conversationId = event.getConversationId();
            
            // 1. Persiste no MongoDB (idempotência via unique index)
            Message message = Message.builder()
                    .messageId(messageId)
                    .conversationId(conversationId)
                    .senderId(event.getSenderId())
                    .messageText(event.getMessageText())
                    .sequenceNumber(event.getSequenceNumber())
                    .timestamp(ProtoUtils.toInstant(event.getTimestamp()))
                    .build();
            
            messageRepository.save(message);
            
            // 2. Notifica usuários online via gRPC stream
            event.getRecipientIdsList().forEach(recipientId -> {
                streamingService.notifyUserMessage(recipientId, grpcEvent);
            });
            
            // 3. Roteia para plataformas externas
            routeToPlatformTopics(event);
            
            // 4. Commit offset APÓS sucesso (at-least-once)
            ack.acknowledge();
            
            logger.info("Message processed: {} in conversation {}", 
                       messageId, conversationId);
            
        } catch (DuplicateKeyException e) {
            // Idempotência: mensagem já processada
            logger.warn("Duplicate message ignored: {}", event.getMessageId());
            ack.acknowledge();
        } catch (Exception e) {
            // Erro: NÃO commita offset → reprocessa
            logger.error("Failed to process message: {}", event.getMessageId(), e);
            throw e;
        }
    }
}
```

**Idempotência**:
- MongoDB unique index em `message_id`
- Duplicatas (retry Kafka) → `DuplicateKeyException` → ignora e commita
- Garante exactly-once semantics no banco

### MessageStateUpdateWorker

**Arquivo**: `src/main/java/com/chat/worker/MessageStateUpdateWorker.java`

```java
@Component
public class MessageStateUpdateWorker {
    
    @KafkaListener(
        topics = "state-update-events",
        groupId = "state-update-workers",
        containerFactory = "stateUpdateEventKafkaListenerContainerFactory"
    )
    public void handleStateUpdate(StateUpdateEvent event, Acknowledgment ack) {
        try {
            String messageId = event.getMessageId();
            
            // Atualiza Message.stateHistory array (embedded)
            Message message = messageRepository.findByMessageId(messageId)
                    .orElseThrow(() -> new IllegalArgumentException("Message not found"));
            
            MessageStateTransition transition = MessageStateTransition.builder()
                    .state(mapStatus(event.getNewStatus()))
                    .timestamp(ProtoUtils.toInstant(event.getTimestamp()))
                    .recipientId(event.getUserId())
                    .build();
            
            message.getStateHistory().add(transition);
            messageRepository.save(message);
            
            ack.acknowledge();
            
        } catch (Exception e) {
            logger.error("Failed to update state: {}", event.getMessageId(), e);
            throw e;
        }
    }
}
```

---

## Serialização Protobuf

### ProtobufSerializer

**Arquivo**: `src/main/java/com/chat/kafka/serialization/ProtobufSerializer.java`

```java
public class ProtobufSerializer<T extends com.google.protobuf.Message> 
        implements Serializer<T> {
    
    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) {
            return null;
        }
        // Protobuf → bytes
        return data.toByteArray();
    }
}
```

### ProtobufDeserializer

**Arquivo**: `src/main/java/com/chat/kafka/serialization/ProtobufDeserializer.java`

```java
public class ProtobufDeserializer<T extends com.google.protobuf.Message> 
        implements Deserializer<T> {
    
    private final com.google.protobuf.Parser<T> parser;
    
    public ProtobufDeserializer(com.google.protobuf.Parser<T> parser) {
        this.parser = parser;
    }
    
    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            // bytes → Protobuf
            return parser.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException("Failed to deserialize", e);
        }
    }
}
```

**Vantagens Protobuf vs JSON**:
- **Tamanho**: 100 bytes vs 250 bytes (60% menor)
- **Velocidade**: 1-2ms vs 3-5ms (2-3x mais rápido)
- **Type-safety**: Compile-time vs runtime errors
- **Schema evolution**: Field numbers garantem compatibilidade

---

## Partition Strategy

### Partition Key = conversation_id

**Motivo**: Garantir ordem de mensagens na mesma conversa

```java
// Publish to Kafka
messageKafkaTemplate.send(
    "message-events",         // Topic
    conversationId,           // KEY = partition key
    event                     // VALUE
);
```

**Como Funciona**:
1. Kafka calcula: `partition = hash(conversation_id) % num_partitions`
2. Todas mensagens da mesma conversa → mesma partição
3. Partição = fila ordenada (FIFO)
4. Consumer processa em ordem

**Exemplo**:
- Conversation A (`conv-123`) → Partition 0 (sempre)
- Conversation B (`conv-456`) → Partition 3 (sempre)
- Consumer 1 lê Partition 0 → processa A em ordem
- Consumer 2 lê Partition 3 → processa B em ordem

---

## Consumer Groups

### Escalabilidade Horizontal

```
Topic: message-events (10 partitions)

Consumer Group: message-delivery-workers
├── Worker 1 → Partitions 0, 1, 2, 3
├── Worker 2 → Partitions 4, 5, 6
└── Worker 3 → Partitions 7, 8, 9

Se Worker 2 falha:
├── Worker 1 → Partitions 0, 1, 2, 3, 4, 5
└── Worker 3 → Partitions 6, 7, 8, 9 (rebalance automático)
```

**Configuração**:
```java
@KafkaListener(
    topics = "message-events",
    groupId = "message-delivery-workers",  // Consumer group ID
    concurrency = "3"  // 3 threads paralelos
)
```

---

## Decisões Arquiteturais

### 1. Kafka vs RabbitMQ

| Aspecto | Kafka (Escolhido) | RabbitMQ |
|---------|-------------------|----------|
| **Throughput** | ✅ 1M+ msg/s | ❌ 10K msg/s |
| **Ordering** | ✅ Per-partition | ⚠️ Per-queue |
| **Persistence** | ✅ Log-based | ⚠️ Memory-first |
| **Replay** | ✅ Offset control | ❌ Não |

**Decisão**: Kafka para escalabilidade e replay capability

### 2. Protobuf vs JSON

| Aspecto | Protobuf (Escolhido) | JSON |
|---------|---------------------|------|
| **Tamanho** | ✅ 100 bytes | ❌ 250 bytes |
| **Speed** | ✅ 1-2ms | ❌ 3-5ms |
| **Type Safety** | ✅ Compile-time | ❌ Runtime |
| **Debug** | ❌ Binário | ✅ Texto |

**Decisão**: Protobuf para performance + consistência com gRPC

### 3. Manual Commit vs Auto-Commit

| Aspecto | Manual (Escolhido) | Auto |
|---------|-------------------|------|
| **At-least-once** | ✅ Sim | ❌ Não |
| **Controle** | ✅ Preciso | ❌ Automático |
| **Complexidade** | ⚠️ Maior | ✅ Simples |

**Decisão**: Manual commit para garantir persistência antes de ack

---

## Referências

- **Protobuf**: `src/main/proto/kafka_events.proto`
- **Config**: `src/main/java/com/chat/config/Kafka*.java`
- **Workers**: `src/main/java/com/chat/worker/`
- **Serializers**: `src/main/java/com/chat/kafka/serialization/`
- **Decisões**: `specs/001-ubiquitous-messaging-platform/research.md` (Decision 2)

---

**Próximo Documento**: [05-MONGODB-E-PERSISTENCIA.md](05-MONGODB-E-PERSISTENCIA.md)
