# 02 - API gRPC e Contratos Protobuf

**Versão**: 1.0  
**Última Atualização**: 29/11/2025  
**Status**: ✅ Implementado

---

## 📋 Índice

1. [Visão Geral da API](#visão-geral-da-api)
2. [Contratos Protobuf](#contratos-protobuf)
3. [Implementações gRPC](#implementações-grpc)
4. [Padrões de Streaming](#padrões-de-streaming)
5. [Tratamento de Erros](#tratamento-de-erros)
6. [Exemplos de Uso](#exemplos-de-uso)

---

## Visão Geral da API

### Serviços Disponíveis

O projeto expõe **2 serviços gRPC** na porta **9090**:

| Serviço | Protobuf File | Responsabilidade |
|---------|---------------|------------------|
| **ChatService** | `chat_service.proto` | Envio de mensagens, streaming tempo real, rastreamento de status |
| **ConversationService** | `conversation_service.proto` | Criação de conversas, listagem, histórico paginado |

### Porta e Protocolo

**Configuração**: `application.yml`
```yaml
grpc:
  server:
    port: 9090
    enable-reflection: true  # Permite BloomRPC/gRPCurl descobrir API em runtime
```

**Protocolo**: HTTP/2 com TLS opcional (dev mode = plaintext)

---

## Contratos Protobuf

### 1. ChatService (chat_service.proto)

**Localização**: `src/main/proto/chat_service.proto`

#### 1.1 Definição do Serviço

```protobuf
service ChatService {
  // Envia mensagem de texto para conversa (síncrono-async híbrido)
  rpc SendMessage(SendMessageRequest) returns (SendMessageResponse);
  
  // Streaming server-side de mensagens em tempo real
  rpc StreamMessages(SubscribeRequest) returns (stream MessageEvent);
  
  // Consulta histórico de status de mensagem (SENT → DELIVERED → READ)
  rpc GetMessageStatus(GetMessageStatusRequest) returns (GetMessageStatusResponse);
  
  // Marca mensagem como lida (usuário abriu mensagem)
  rpc MarkMessageAsRead(MarkMessageAsReadRequest) returns (MarkMessageAsReadResponse);
}
```

#### 1.2 SendMessage - Envio de Mensagens

**Request**:
```protobuf
message SendMessageRequest {
  string message_id = 1;         // UUID gerado pelo cliente (idempotência)
  string conversation_id = 2;    // UUID da conversa destino
  string sender_id = 3;          // UUID do remetente (extraído do JWT)
  string recipient_id = 4;       // UUID do destinatário
  string message_text = 5;       // Conteúdo (max 100 KB)
  map<string, string> metadata = 6;  // Futuro: reply_to, mentions
}
```

**Response**:
```protobuf
message SendMessageResponse {
  string message_id = 1;                    // Echo do message_id
  MessageStatus status = 2;                 // Sempre SENT (202 Accepted)
  google.protobuf.Timestamp timestamp = 3;  // Timestamp do servidor
  int64 sequence_number = 4;                // Número de sequência na conversa
}
```

**Implementação**: `src/main/java/com/chat/grpc/ChatServiceImpl.java`

```java
@Override
public void sendMessage(SendMessageRequest request, 
                       StreamObserver<SendMessageResponse> responseObserver) {
    try {
        String messageId = request.getMessageId();
        String conversationId = request.getConversationId();
        String messageText = request.getMessageText();
        
        // Valida request (UUID, tamanho, autorização)
        messageService.validateMessage(messageId, conversationId, senderId, 
                                       recipientId, messageText);
        
        // Gera sequence number atômico
        Long sequenceNumber = messageService.generateSequenceNumber(conversationId);
        
        // Publica evento Kafka (async persistence)
        MessageEvent event = MessageEvent.newBuilder()
                .setMessageId(messageId)
                .setConversationId(conversationId)
                .setMessageText(messageText)
                .setSequenceNumber(sequenceNumber)
                .build();
        
        // Key = conversation_id para garantir ordem por partição
        messageKafkaTemplate.send(MESSAGE_EVENTS_TOPIC, conversationId, event);
        
        // Retorna 202 Accepted imediatamente
        SendMessageResponse response = SendMessageResponse.newBuilder()
                .setMessageId(messageId)
                .setTimestamp(Timestamps.fromMillis(now.toEpochMilli()))
                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
        
    } catch (IllegalArgumentException e) {
        responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
    }
}
```

**Por Que Assim**:
- **Kafka async**: Retorna 202 Accepted sem esperar persistência → p95 <100ms
- **Sequence number**: Gerado atomicamente via MongoDB `findAndModify` → garante ordem
- **Partition key = conversation_id**: Todas mensagens da mesma conversa vão para mesma partição → ordem preservada

#### 1.3 StreamMessages - Tempo Real

**Request**:
```protobuf
message SubscribeRequest {
  string user_id = 1;                  // Usuário assinando stream
  repeated string conversation_ids = 2; // Filtro opcional (vazio = todas)
}
```

**Response** (Server-Side Streaming):
```protobuf
message MessageEvent {
  oneof event {
    NewMessageEvent new_message = 1;       // Nova mensagem recebida
    StatusUpdateEvent status_update = 2;   // Status mudou (DELIVERED/READ)
  }
}

message NewMessageEvent {
  string message_id = 1;
  string conversation_id = 2;
  UserInfo sender = 3;
  string message_text = 4;
  google.protobuf.Timestamp timestamp = 5;
  int64 sequence_number = 6;
}
```

**Implementação**: `ChatServiceImpl.java`

```java
@Override
public void streamMessages(
        SubscribeRequest request,
        StreamObserver<com.chat.grpc.v1.MessageEvent> responseObserver) {
    
    String userId = request.getUserId();
    
    // Registra stream no StreamingService (in-memory)
    streamingService.subscribeToMessages(userId, responseObserver);
    
    logger.info("User {} opened message stream (active streams: {})",
                userId, streamingService.getActiveMessageStreamCount());
    
    // Stream fica aberto até cliente chamar onCompleted ou conexão quebrar
    // MessageDeliveryWorker chama responseObserver.onNext() para cada mensagem
}
```

**StreamingService**: `src/main/java/com/chat/service/StreamingService.java`

```java
@Service
public class StreamingService {
    // Thread-safe map de streams ativos
    private final ConcurrentHashMap<String, StreamObserver<MessageEvent>> activeMessageStreams 
        = new ConcurrentHashMap<>();
    
    public void subscribeToMessages(String userId, StreamObserver<MessageEvent> stream) {
        activeMessageStreams.put(userId, stream);
    }
    
    // Chamado por MessageDeliveryWorker
    public void notifyUserMessage(String userId, MessageEvent grpcEvent) {
        StreamObserver<MessageEvent> stream = activeMessageStreams.get(userId);
        if (stream != null) {
            try {
                stream.onNext(grpcEvent);  // Push mensagem via HTTP/2 stream
            } catch (Exception e) {
                // Stream quebrado - remove do map
                activeMessageStreams.remove(userId);
            }
        }
    }
}
```

**Por Que Assim**:
- **Server-Side Streaming**: Cliente abre 1 conexão persistente, servidor faz push quando há dados → evita polling
- **ConcurrentHashMap**: Thread-safe para múltiplos workers Kafka acessando simultaneamente
- **HTTP/2**: Multiplexing permite múltiplos streams em 1 conexão TCP → eficiente

---

### 2. ConversationService (conversation_service.proto)

**Localização**: `src/main/proto/conversation_service.proto`

#### 2.1 Definição do Serviço

```protobuf
service ConversationService {
  // Cria nova conversa (1:1 ou grupo)
  rpc CreateConversation(CreateConversationRequest) returns (CreateConversationResponse);
  
  // Lista conversas do usuário com preview da última mensagem
  rpc ListConversations(ListConversationsRequest) returns (ListConversationsResponse);
  
  // Detalhes de conversa específica
  rpc GetConversation(GetConversationRequest) returns (GetConversationResponse);
  
  // Histórico de mensagens paginado
  rpc GetConversationHistory(GetConversationHistoryRequest) 
      returns (GetConversationHistoryResponse);
  
  // Adicionar membro em grupo (P3 - futuro)
  rpc AddMember(AddMemberRequest) returns (AddMemberResponse);
  
  // Remover membro de grupo (P3 - futuro)
  rpc RemoveMember(RemoveMemberRequest) returns (RemoveMemberResponse);
}
```

#### 2.2 CreateConversation

**Request**:
```protobuf
message CreateConversationRequest {
  ConversationType type = 1;         // PRIVATE ou GROUP
  repeated string participant_ids = 2;  // 2 para PRIVATE, N para GROUP
  string name = 3;                   // Nome do grupo (obrigatório para GROUP)
  map<string, string> metadata = 4;  // Futuro: avatar_url, description
}
```

**Implementação**: `ConversationServiceImpl.java`

```java
@Override
public void createConversation(
        CreateConversationRequest request,
        StreamObserver<CreateConversationResponse> responseObserver) {
    
    List<String> participantIds = request.getParticipantIdsList();
    
    // Valida exatamente 2 participantes para PRIVATE
    if (participantIds.size() != 2) {
        responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("PRIVATE conversations require exactly 2 participants")
                .asRuntimeException());
        return;
    }
    
    // Cria conversa (persiste no MongoDB)
    Conversation conversation = conversationService.createConversation(
        participantIds.get(0), participantIds.get(1));
    
    // Mapeia entity para protobuf
    CreateConversationResponse response = CreateConversationResponse.newBuilder()
            .setConversationId(conversation.getConversationId())
            .setType(com.chat.grpc.v1.ConversationType.PRIVATE)
            .addAllParticipantIds(conversation.getParticipants())
            .setCreatedAt(toProtobufTimestamp(conversation.getCreatedAt()))
            .build();
    
    responseObserver.onNext(response);
    responseObserver.onCompleted();
}
```

#### 2.3 ListConversations - Paginação

**Request**:
```protobuf
message ListConversationsRequest {
  string user_id = 1;
  int32 limit = 2;              // Items por página (default 20, max 100)
  int32 offset = 3;             // Posição inicial (0-indexed)
  ConversationType type_filter = 4;  // Filtro opcional (PRIVATE/GROUP)
}
```

**Response**:
```protobuf
message ListConversationsResponse {
  repeated ConversationSummary conversations = 1;
  PaginationInfo pagination = 2;  // Total count, has_more flag
}

message ConversationSummary {
  string conversation_id = 1;
  ConversationType type = 2;
  repeated UserInfo participants = 3;
  string last_message_preview = 6;  // Primeiros 50 chars
  google.protobuf.Timestamp last_message_at = 5;  // Sort key
  int32 unread_count = 7;
}
```

**Implementação**:

```java
@Override
public void listConversations(
        ListConversationsRequest request,
        StreamObserver<ListConversationsResponse> responseObserver) {
    
    String userId = request.getUserId();
    int limit = request.getLimit() > 0 ? request.getLimit() : 20;
    int offset = request.getOffset();
    
    // Query MongoDB com paginação (sorted by lastMessageAt desc)
    Page<Conversation> conversationsPage = 
        conversationService.listConversations(userId, limit, offset);
    
    // Mapeia para protobuf
    List<ConversationSummary> summaries = conversationsPage.getContent().stream()
            .map(this::toConversationSummary)
            .collect(Collectors.toList());
    
    // Pagination info
    PaginationInfo pagination = PaginationInfo.newBuilder()
            .setTotalCount((int) conversationsPage.getTotalElements())
            .setHasMore(conversationsPage.hasNext())
            .build();
    
    ListConversationsResponse response = ListConversationsResponse.newBuilder()
            .addAllConversations(summaries)
            .setPagination(pagination)
            .build();
    
    responseObserver.onNext(response);
    responseObserver.onCompleted();
}
```

**Query MongoDB**:
```java
// ConversationRepository
Page<Conversation> findByParticipantsContaining(String userId, Pageable pageable);

// Service layer
Pageable pageable = PageRequest.of(
    offset / limit,
    limit,
    Sort.by("lastMessageAt").descending()
);
```

**Índice MongoDB**:
```javascript
db.conversations.createIndex(
  { "participants": 1, "last_message_at": -1 }
);
```

**Por Que Assim**:
- **Offset-based pagination**: Simples, adequado para conversas (baixo volume, poucos updates)
- **Denormalized preview**: Campo `lastMessagePreview` evita JOIN com collection `messages`
- **Compound index**: (participants, lastMessageAt) suporta query eficiente

#### 2.4 GetConversationHistory - Histórico Paginado

**Request**:
```protobuf
message GetConversationHistoryRequest {
  string conversation_id = 1;
  int32 limit = 2;              // Mensagens por página (default 50, max 100)
  int32 offset = 3;
  google.protobuf.Timestamp since = 4;   // Filtro opcional (depois de)
  google.protobuf.Timestamp until = 5;   // Filtro opcional (antes de)
}
```

**Response**:
```protobuf
message GetConversationHistoryResponse {
  string conversation_id = 1;
  repeated Message messages = 2;  // Ordem cronológica (mais antigas primeiro)
  PaginationInfo pagination = 3;
}

message Message {
  string message_id = 1;
  UserInfo sender = 3;
  string message_text = 4;
  google.protobuf.Timestamp timestamp = 6;
  int64 sequence_number = 7;
  repeated MessageStateTransition state_history = 8;  // Array de transições
  MessageStatus current_status = 9;
}
```

**Implementação**:

```java
@Override
public void getConversationHistory(
        GetConversationHistoryRequest request,
        StreamObserver<GetConversationHistoryResponse> responseObserver) {
    
    String conversationId = request.getConversationId();
    int limit = request.getLimit() > 0 ? request.getLimit() : 50;
    int offset = request.getOffset();
    
    // Query messages ordenadas por timestamp DESC
    Pageable pageable = PageRequest.of(offset / limit, limit);
    Page<Message> messagesPage = 
        messageRepository.findByConversationIdOrderByTimestampDesc(
            conversationId, pageable);
    
    // Mapeia para protobuf
    List<com.chat.grpc.v1.Message> messageProtos = messagesPage.getContent()
            .stream()
            .map(this::toMessageProto)
            .collect(Collectors.toList());
    
    GetConversationHistoryResponse response = 
        GetConversationHistoryResponse.newBuilder()
            .setConversationId(conversationId)
            .addAllMessages(messageProtos)
            .setPagination(buildPaginationInfo(messagesPage))
            .build();
    
    responseObserver.onNext(response);
    responseObserver.onCompleted();
}
```

**Por Que Assim**:
- **DESC order**: UI de chat mostra mensagens recentes primeiro, usuário scrolls para ver antigas
- **Embedded state_history**: Cada mensagem carrega histórico completo SENT→DELIVERED→READ
- **Cursor pagination (futuro)**: Filtro `since` permite query incremental (ex: polling de novas mensagens)

---

### 3. Common Types (common_types.proto)

**Localização**: `src/main/proto/common_types.proto`

#### 3.1 Enums

```protobuf
enum MessageStatus {
  MESSAGE_STATUS_UNSPECIFIED = 0;  // Valor inválido (Protobuf best practice)
  SENT = 1;                         // Aceita pelo servidor, persistida
  DELIVERED = 2;                    // Chegou no device do destinatário
  READ = 3;                         // Aberta pelo destinatário
}

enum ConversationType {
  CONVERSATION_TYPE_UNSPECIFIED = 0;
  PRIVATE = 1;                      // 1:1 (2 participantes)
  GROUP = 2;                        // N participantes
}
```

**Por Que Field 0 = UNSPECIFIED**:
- Protobuf default value para enum é 0
- Se cliente não seta valor, fica UNSPECIFIED → fácil detectar erro
- Evita ambiguidade (0 = SENT seria confuso se esquecesse de setar)

#### 3.2 Messages Compartilhadas

```protobuf
message UserInfo {
  string user_id = 1;       // UUID
  string username = 2;      // Display name
}

message MessageStateTransition {
  MessageStatus state = 1;
  google.protobuf.Timestamp timestamp = 2;
  string recipient_id = 3;  // Para mensagens em grupo (quem leu?)
}

message PaginationInfo {
  int32 limit = 1;
  int32 offset = 2;
  int64 total_count = 3;
  bool has_more = 4;  // UI pode mostrar "Load More" button
}
```

---

## Padrões de Streaming

### 1. Server-Side Streaming (StreamMessages)

**Diagrama**:
```
Cliente                                    Servidor
   │                                          │
   │──── StreamMessages(SubscribeRequest) ──→│
   │                                          │ Registra stream
   │                                          │
   │                                          │ Nova mensagem chega
   │←── MessageEvent (push) ─────────────────│
   │                                          │
   │←── MessageEvent (push) ─────────────────│
   │                                          │
   │         ... stream aberto ...            │
   │                                          │
   │──── onCompleted ──────────────────────→ │ Cliente fecha
```

**Características**:
- **1 request, N responses**: Cliente inicia, servidor faz push contínuo
- **Long-lived connection**: Mantém conexão HTTP/2 aberta (horas/dias)
- **Baixa latência**: Push instantâneo (<100ms) vs polling (intervalo de segundos)

**Uso**: Notificações em tempo real, chat, dashboards

### 2. Unary RPC (Todos os outros métodos)

**Diagrama**:
```
Cliente                    Servidor
   │                          │
   │──── Request ────→        │
   │                          │ Processa
   │        ←──── Response ───│
   │                          │
```

**Características**:
- **1 request, 1 response**: Tradicional request/response
- **Stateless**: Cada chamada independente

**Uso**: CRUD operations, queries

---

## Tratamento de Erros

### Códigos gRPC Status

**Implementação**: `GlobalExceptionHandler.java`

```java
@Component
public class GlobalExceptionHandler {
    
    // Argumentos inválidos (UUID malformado, fields faltando)
    public StatusRuntimeException handleIllegalArgument(IllegalArgumentException e) {
        return Status.INVALID_ARGUMENT
                .withDescription(e.getMessage())
                .withCause(e)
                .asRuntimeException();
    }
    
    // Autorização (usuário não é participante)
    public StatusRuntimeException permissionDenied(String message) {
        return Status.PERMISSION_DENIED
                .withDescription(message)
                .asRuntimeException();
    }
    
    // Recurso não encontrado
    public StatusRuntimeException notFound(String resource, String id) {
        return Status.NOT_FOUND
                .withDescription(resource + " not found: " + id)
                .asRuntimeException();
    }
    
    // Erro interno genérico
    public StatusRuntimeException handleGenericException(RuntimeException e) {
        logger.error("Internal error", e);
        return Status.INTERNAL
                .withDescription("Internal server error")
                .asRuntimeException();
    }
}
```

### Mapeamento de Erros

| Exception Java | gRPC Status | Uso |
|----------------|-------------|-----|
| `IllegalArgumentException` | `INVALID_ARGUMENT` | UUID inválido, fields vazios |
| `SecurityException` | `PERMISSION_DENIED` | Não autorizado (não é participante) |
| `EntityNotFoundException` | `NOT_FOUND` | Conversa/mensagem não existe |
| `DuplicateKeyException` | `ALREADY_EXISTS` | message_id duplicado |
| `RuntimeException` | `INTERNAL` | Erro inesperado |

**Exemplo de Uso**:

```java
try {
    // Business logic
    messageService.validateMessage(...);
} catch (IllegalArgumentException e) {
    responseObserver.onError(exceptionHandler.handleIllegalArgument(e));
    return;
}
```

---

## Exemplos de Uso

### 1. Enviar Mensagem (Java Client)

```java
// Setup
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 9090)
    .usePlaintext()
    .build();

ChatServiceGrpc.ChatServiceBlockingStub stub = 
    ChatServiceGrpc.newBlockingStub(channel);

// Request
SendMessageRequest request = SendMessageRequest.newBuilder()
    .setMessageId(UUID.randomUUID().toString())
    .setConversationId("conv-123")
    .setSenderId("user-456")
    .setRecipientId("user-789")
    .setMessageText("Hello, World!")
    .build();

// Call
SendMessageResponse response = stub.sendMessage(request);
System.out.println("Message sent: " + response.getMessageId());
```

### 2. Streaming Tempo Real (Java Client)

```java
ChatServiceGrpc.ChatServiceStub asyncStub = 
    ChatServiceGrpc.newStub(channel);

SubscribeRequest request = SubscribeRequest.newBuilder()
    .setUserId("user-789")
    .build();

// Server-side streaming
asyncStub.streamMessages(request, new StreamObserver<MessageEvent>() {
    @Override
    public void onNext(MessageEvent event) {
        if (event.hasNewMessage()) {
            NewMessageEvent msg = event.getNewMessage();
            System.out.println("New message: " + msg.getMessageText());
        }
    }
    
    @Override
    public void onError(Throwable t) {
        System.err.println("Stream error: " + t.getMessage());
    }
    
    @Override
    public void onCompleted() {
        System.out.println("Stream closed by server");
    }
});

// Stream fica aberto - bloqueie thread ou use async processing
Thread.sleep(60000);
```

### 3. Listar Conversas (gRPCurl CLI)

```bash
# Listar conversas do usuário
grpcurl -plaintext \
  -d '{
    "user_id": "user-123",
    "limit": 20,
    "offset": 0
  }' \
  localhost:9090 \
  chat_api.v1.ConversationService/ListConversations
```

**Response**:
```json
{
  "conversations": [
    {
      "conversation_id": "conv-abc",
      "type": "PRIVATE",
      "participants": [
        {"user_id": "user-123", "username": "alice"},
        {"user_id": "user-456", "username": "bob"}
      ],
      "last_message_preview": "Hello, how are you?",
      "last_message_at": "2025-11-29T10:30:00Z",
      "unread_count": 3
    }
  ],
  "pagination": {
    "total_count": 15,
    "limit": 20,
    "offset": 0,
    "has_more": false
  }
}
```

---

## Vantagens da Abordagem gRPC + Protobuf

### 1. Performance

| Métrica | REST/JSON | gRPC/Protobuf |
|---------|-----------|---------------|
| **Payload Size** | 250 bytes | 100 bytes (60% menor) |
| **Serialization** | 5-10ms | 1-2ms (3-5x mais rápido) |
| **Latency p95** | 150-200ms | <100ms |

### 2. Type Safety

```protobuf
// Protobuf schema garante tipos em compile-time
message SendMessageRequest {
  string message_id = 1;  // String obrigatória
  int64 sequence_number = 2;  // Int64, não aceita string
}
```

**vs JSON** (runtime errors):
```json
{
  "message_id": 123,  // Ops, era string
  "sequence_number": "456"  // Ops, era int
}
```

### 3. Streaming Nativo

gRPC tem streaming nativo via HTTP/2:
- **Server-Side**: 1 request → N responses (chat, notificações)
- **Client-Side**: N requests → 1 response (upload chunked)
- **Bidirecional**: N requests ↔ N responses (chat voice/video)

REST requer workarounds (SSE, WebSocket = protocolos separados)

### 4. Code Generation

```bash
# Protobuf compiler gera código para múltiplas linguagens
protoc --java_out=src/main/java src/main/proto/*.proto
protoc --python_out=sdk/python src/main/proto/*.proto
protoc --go_out=sdk/go src/main/proto/*.proto
```

Resultado: SDKs nativos para Java, Python, Go, etc.

---

## Referências

- **Contratos Protobuf**: `src/main/proto/`
  - `chat_service.proto` - Operações de mensagens
  - `conversation_service.proto` - Gerenciamento de conversas
  - `common_types.proto` - Tipos compartilhados
- **Implementações gRPC**: `src/main/java/com/chat/grpc/`
  - `ChatServiceImpl.java` - Chat service implementation
  - `ConversationServiceImpl.java` - Conversation service implementation
  - `GlobalExceptionHandler.java` - Error handling
- **Streaming**: `src/main/java/com/chat/service/StreamingService.java`
- **Decisões**: `specs/001-ubiquitous-messaging-platform/research.md` (Decision 1)

---

**Próximo Documento**: [03-AUTENTICACAO-E-SEGURANCA.md](03-AUTENTICACAO-E-SEGURANCA.md)
