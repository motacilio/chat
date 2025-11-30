# 05 - MongoDB e Persistência

**Versão**: 1.0  
**Status**: ✅ Implementado

---

## Schema Design

### Collections

| Collection | Documento | Índices | Propósito |
|------------|-----------|---------|-----------|
| `messages` | Message | message_id (unique), conversation_id+timestamp | Mensagens e histórico de status |
| `conversations` | Conversation | participants+lastMessageAt | Conversas 1:1 e grupos |
| `users` | User | username (unique) | Usuários do sistema |
| `linked_accounts` | LinkedAccount | userId+platform (unique) | Contas externas (WhatsApp, Instagram) |
| `recipient_contacts` | RecipientContact | userId | Contatos de destinatários |

### Message Entity

**Arquivo**: `src/main/java/com/chat/model/Message.java`

```java
@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "conversation_timestamp", 
                  def = "{'conversationId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "conversation_sequence", 
                  def = "{'conversationId': 1, 'sequenceNumber': 1}")
})
public class Message {
    @Id
    private String id;                    // MongoDB ObjectId
    
    @Indexed(unique = true)
    private String messageId;             // UUID (idempotência)
    
    @Indexed
    private String conversationId;
    
    @Indexed
    private String senderId;
    
    private String messageText;           // XOR com fileMetadata
    private FileMetadata fileMetadata;    // Embedded document
    
    private Instant timestamp;
    private Long sequenceNumber;          // Ordem na conversa
    
    // Embedded array - evita JOINs
    private List<MessageStateTransition> stateHistory;
}
```

**Embedded Document: MessageStateTransition**

```java
@Data
public class MessageStateTransition {
    private MessageStatus state;     // SENT, DELIVERED, READ
    private Instant timestamp;
    private String recipientId;      // Para grupos
}
```

**Por Que Embedded Array**:
- ✅ 1 query para buscar mensagem + histórico completo
- ✅ Operação atômica (append ao array)
- ❌ Não suporta milhares de transições (limite MongoDB 16MB/doc)
- **Trade-off**: Adequado para 3 estados (SENT→DELIVERED→READ)

### Conversation Entity

```java
@Document(collection = "conversations")
@CompoundIndex(name = "participants_lastMessage", 
              def = "{'participants': 1, 'lastMessageAt': -1}")
public class Conversation {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String conversationId;    // UUID
    
    private ConversationType type;    // PRIVATE, GROUP
    
    private List<String> participants;  // Array de user_ids
    
    private Instant createdAt;
    private Instant lastMessageAt;    // Denormalized para sort
    private String lastMessagePreview; // Primeiros 50 chars
}
```

**Denormalização: lastMessagePreview**
- Evita JOIN com `messages` collection
- Atualizado via MessageDeliveryWorker após persistir mensagem
- Trade-off: Duplicação vs performance

### Índices MongoDB

```javascript
// messages collection
db.messages.createIndex(
  { "message_id": 1 }, 
  { unique: true }  // Idempotência
);

db.messages.createIndex(
  { "conversation_id": 1, "timestamp": -1 }  // Query histórico
);

// conversations collection
db.conversations.createIndex(
  { "participants": 1, "last_message_at": -1 }  // Listar conversas
);

db.conversations.createIndex(
  { "conversation_id": 1 }, 
  { unique: true }
);
```

---

## Repositories

### MessageRepository

**Arquivo**: `src/main/java/com/chat/repository/MessageRepository.java`

```java
public interface MessageRepository extends MongoRepository<Message, String> {
    
    Optional<Message> findByMessageId(String messageId);
    
    @Query("{ 'conversationId': ?0 }")
    Page<Message> findByConversationIdOrderByTimestampDesc(
        String conversationId, Pageable pageable);
    
    @Query("{ 'conversationId': ?0, 'timestamp': { $gte: ?1 } }")
    List<Message> findByConversationIdAndTimestampAfter(
        String conversationId, Instant since);
}
```

### ConversationRepository

```java
public interface ConversationRepository extends MongoRepository<Conversation, String> {
    
    Optional<Conversation> findByConversationId(String conversationId);
    
    @Query("{ 'participants': { $all: ?0 }, 'type': 'PRIVATE' }")
    Optional<Conversation> findPrivateConversationBetween(List<String> participants);
    
    Page<Conversation> findByParticipantsContaining(String userId, Pageable pageable);
}
```

---

## MongoDB Configuration

**Arquivo**: `src/main/java/com/chat/config/MongoConfig.java`

```java
@Configuration
public class MongoConfig {
    
    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory) {
        MongoTemplate template = new MongoTemplate(factory);
        
        // WriteConcern.MAJORITY: Espera replicação em maioria dos nós
        template.setWriteConcern(WriteConcern.MAJORITY);
        
        // Lança exceção se write falhar
        template.setWriteResultChecking(WriteResultChecking.EXCEPTION);
        
        return template;
    }
}
```

**application.yml**:
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/chatdb
      # Produção: mongodb://mongo1,mongo2,mongo3/chatdb?replicaSet=rs0
```

---

## Decisões

### 1. Embedded vs Referenced Documents

| Embedded (Escolhido) | Referenced |
|----------------------|------------|
| ✅ 1 query | ❌ Requer $lookup (JOIN) |
| ✅ Atômico | ⚠️ Transações necessárias |
| ❌ Limite 16MB/doc | ✅ Sem limite |

**Decisão**: Embedded para `stateHistory` (max 3 transições)

### 2. WriteConcern.MAJORITY

- Espera replicação em maioria dos nós antes de ack
- Previne perda de dados em failover
- Trade-off: +10-20ms latência vs durabilidade

---

**Próximo**: [06-UPLOAD-ARQUIVOS-E-MINIO.md](06-UPLOAD-ARQUIVOS-E-MINIO.md)
