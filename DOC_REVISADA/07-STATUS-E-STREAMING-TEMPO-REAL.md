# 07 - Status e Streaming Tempo Real

**Versão**: 1.0  
**Status**: ✅ Implementado

---

## Ciclo de Vida de Status

```
SENT ────────→ DELIVERED ────────→ READ
(servidor)     (device recebe)    (usuário abre)
```

### Transições

| Status | Acionado Por | Quando |
|--------|--------------|--------|
| **SENT** | ChatServiceImpl | Mensagem aceita pelo servidor, publicada no Kafka |
| **DELIVERED** | Cliente gRPC | Device recebe via StreamMessages (online) OU polling |
| **READ** | Cliente gRPC | Usuário abre mensagem (chama MarkMessageAsRead) |

---

## Implementação

### 1. SENT (Automático)

```java
// ChatServiceImpl.sendMessage()
SendMessageResponse response = SendMessageResponse.newBuilder()
        .setMessageId(messageId)
        .setStatus(MessageStatus.SENT)  // Sempre SENT ao aceitar
        .build();
```

### 2. DELIVERED (Cliente notifica)

```java
// Cliente chama após receber via StreamMessages
stub.markMessageAsDelivered(MarkMessageAsDeliveredRequest.newBuilder()
        .setMessageId(messageId)
        .setUserId(userId)
        .build());
```

**Backend**:
```java
// Publica StateUpdateEvent no Kafka
StateUpdateEvent event = StateUpdateEvent.newBuilder()
        .setMessageId(messageId)
        .setNewStatus(StateUpdateEvent.MessageStatus.DELIVERED)
        .setUserId(userId)
        .build();

stateKafkaTemplate.send("state-update-events", messageId, event);
```

### 3. READ (MarkMessageAsRead)

```java
@Override
public void markMessageAsRead(MarkMessageAsReadRequest request, ...) {
    // Publica StateUpdateEvent com READ
    StateUpdateEvent event = StateUpdateEvent.newBuilder()
            .setMessageId(request.getMessageId())
            .setNewStatus(StateUpdateEvent.MessageStatus.READ)
            .setUserId(request.getUserId())
            .build();
    
    stateKafkaTemplate.send("state-update-events", messageId, event);
}
```

---

## Streaming Tempo Real

### Server-Side Streaming

**Cliente abre stream**:
```java
SubscribeRequest request = SubscribeRequest.newBuilder()
        .setUserId("user-123")
        .build();

asyncStub.streamMessages(request, new StreamObserver<MessageEvent>() {
    @Override
    public void onNext(MessageEvent event) {
        // Nova mensagem recebida em tempo real!
        System.out.println("New message: " + event.getNewMessage().getMessageText());
    }
});
```

**Servidor faz push**:
```java
// MessageDeliveryWorker após persistir mensagem
event.getRecipientIdsList().forEach(recipientId -> {
    StreamObserver<MessageEvent> stream = streamingService.getStream(recipientId);
    if (stream != null) {
        stream.onNext(grpcMessageEvent);  // Push instantâneo (<100ms)
    }
});
```

### StreamingService (In-Memory)

```java
@Service
public class StreamingService {
    private final ConcurrentHashMap<String, StreamObserver<MessageEvent>> activeStreams 
        = new ConcurrentHashMap<>();
    
    public void subscribeToMessages(String userId, StreamObserver<MessageEvent> stream) {
        activeStreams.put(userId, stream);
    }
    
    public void notifyUserMessage(String userId, MessageEvent event) {
        StreamObserver<MessageEvent> stream = activeStreams.get(userId);
        if (stream != null) {
            try {
                stream.onNext(event);
            } catch (Exception e) {
                // Stream quebrado - remove
                activeStreams.remove(userId);
            }
        }
    }
}
```

---

## Decisões

### 1. gRPC Streaming vs WebSocket

| gRPC Streaming (Escolhido) | WebSocket |
|----------------------------|-----------|
| ✅ HTTP/2 nativo | ❌ HTTP/1.1 upgrade |
| ✅ Protobuf type-safe | ⚠️ JSON manual |
| ✅ Multiplexing | ⚠️ 1 stream/conexão |

**Decisão**: gRPC para consistência com API

### 2. In-Memory Streams vs Redis Pub/Sub

- **POC**: In-memory (ConcurrentHashMap)
- **Produção**: Redis Pub/Sub para múltiplas instâncias

---

**Próximo**: [08-WEBHOOKS-E-INTEGRACAO-PLATAFORMAS.md](08-WEBHOOKS-E-INTEGRACAO-PLATAFORMAS.md)
