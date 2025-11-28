# 📋 Checklist de Entrega - Sprint 1

## Entregas Esperadas

### ✅ 1. API funcional (envio e listagem de mensagens)

**Status**: 80% completo

#### Implementado:
- ✅ POST SendMessage (envio de mensagem texto via gRPC)
- ✅ GET GetMessageStatus (consultar status de mensagem)
- ✅ POST MarkMessageAsRead (marcar como lida)

#### Faltando:
- ❌ **GET GetConversationHistory (listar mensagens de uma conversação)**
  - **Arquivo**: `src/main/java/com/chat/grpc/ConversationServiceImpl.java` (não existe)
  - **Proto**: `src/main/proto/conversation_service.proto` (já definido)
  - **Método**: `GetConversationHistory(GetConversationHistoryRequest) returns (GetConversationHistoryResponse)`
  
**Ação necessária**: 
1. Criar `ConversationServiceImpl` 
2. Implementar `getConversationHistory()` que:
   - Recebe `conversation_id` e `pagination` (limit, offset)
   - Query MongoDB: `messageRepository.findByConversationId()` com `Sort.by("timestamp").descending()`
   - Retorna lista de mensagens com status

---

### ✅ 2. Kafka + consumidor em execução

**Status**: ✅ 100% completo

- ✅ Topics auto-criados (`message-events`, `state-update-events`)
- ✅ Produtor no `ChatServiceImpl`
- ✅ **MessageDeliveryWorker** rodando (consome message-events)
- ✅ **MessageStateUpdateWorker** rodando (consome state-update-events)
- ✅ Consumer groups configurados
- ✅ Manual offset commit
- ✅ Idempotência implementada

**Validação**:
```powershell
# Ver topics
docker exec kafka-dev kafka-topics --list --bootstrap-server localhost:9092

# Ver consumer groups
docker exec kafka-dev kafka-consumer-groups --list --bootstrap-server localhost:9092

# Ver mensagens no topic
docker exec kafka-dev kafka-console-consumer --bootstrap-server localhost:9092 --topic message-events --from-beginning --max-messages 5
```

---

### ✅ 3. Banco de dados armazenando mensagens

**Status**: ✅ 100% completo

- ✅ MongoDB conectado (mongodb://localhost:27017/chat)
- ✅ Collections criadas automaticamente:
  - `conversations` (conversas com participantes)
  - `messages` (mensagens com state_history)
  - `message_sequences` (sequence counters)
- ✅ Índices configurados (conversation_id, message_id, timestamp)
- ✅ Write concern "majority" (durabilidade)

**Validação**:
```powershell
docker exec mongodb-dev mongosh --eval '
use chat
db.conversations.find().pretty()
db.messages.find().pretty()
'
```

---

### ❌ 4. Implementar autenticação simples (JWT com chave estática)

**Status**: ❌ 0% completo

#### Faltando:
1. **JwtTokenProvider** (gerar e validar tokens)
2. **AuthenticationInterceptor** (interceptar requests gRPC)
3. **SecurityConfig** atualizado
4. **User login endpoint** (opcional para MVP - pode usar token hardcoded)

**Implementação mínima**:

```java
// JwtTokenProvider.java
@Component
public class JwtTokenProvider {
    private static final String SECRET_KEY = "my-secret-key-123"; // Hardcoded para MVP
    private static final long EXPIRATION_MS = 3600000; // 1 hora
    
    public String generateToken(String userId) {
        return Jwts.builder()
            .setSubject(userId)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
            .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
            .compact();
    }
    
    public String validateToken(String token) {
        Claims claims = Jwts.parser()
            .setSigningKey(SECRET_KEY)
            .parseClaimsJws(token)
            .getBody();
        return claims.getSubject(); // userId
    }
}
```

```java
// AuthenticationInterceptor.java
@Component
public class AuthenticationInterceptor implements ServerInterceptor {
    private final JwtTokenProvider jwtProvider;
    
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        String token = headers.get(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER));
        
        if (token == null || !token.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing token"), headers);
            return new ServerCall.Listener<>() {};
        }
        
        try {
            String userId = jwtProvider.validateToken(token.substring(7));
            Context ctx = Context.current().withValue(USER_ID_CONTEXT_KEY, userId);
            return Contexts.interceptCall(ctx, call, headers, next);
        } catch (Exception e) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), headers);
            return new ServerCall.Listener<>() {};
        }
    }
}
```

**Teste**:
```powershell
# 1. Gerar token (hardcoded para teste)
$token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# 2. Enviar com header Authorization
grpcurl -plaintext -H "Authorization: Bearer $token" -d '{...}' localhost:9090 chat.ChatService/SendMessage
```

---

### ⚠️ 5. Teste de comunicação interna

**Status**: ⚠️ Parcial (funcional mas não documentado como "teste oficial")

#### Implementado:
- ✅ Infraestrutura funcional (Docker Compose)
- ✅ API aceita mensagens
- ✅ Persistência confirmada

#### Faltando:
- ❌ **Script de teste automatizado** mostrando:
  1. Alice envia mensagem para Bob
  2. Verificar persistência no MongoDB
  3. Bob consulta histórico e recebe a mensagem
  4. Bob marca como lida
  5. Alice consulta status e vê READ

**Criar arquivo**: `scripts/test-communication.ps1`

```powershell
# Test Script: Alice → Bob communication
Write-Host "=== Teste de Comunicação Interna ===" -ForegroundColor Cyan

# 1. Alice envia mensagem para Bob
Write-Host "`n1. Alice envia mensagem para Bob" -ForegroundColor Yellow
$response = grpcurl -plaintext -d '{
  "message_id": "550e8400-e29b-41d4-a716-446655440010",
  "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
  "sender_id": "673b4a12-e29b-41d4-a716-44665544020f",
  "recipient_id": "673b4a12-e29b-41d4-a716-44665544021f",
  "message_text": "Oi Bob, tudo bem?"
}' localhost:9090 chat.ChatService/SendMessage

Write-Host $response

# 2. Aguardar processamento
Start-Sleep -Seconds 2

# 3. Verificar no MongoDB
Write-Host "`n2. Verificando no MongoDB..." -ForegroundColor Yellow
docker exec mongodb-dev mongosh --eval '
db.getSiblingDB("chat").messages.findOne({messageId: "550e8400-e29b-41d4-a716-446655440010"})
'

# 4. Bob consulta histórico
Write-Host "`n3. Bob consulta histórico da conversa" -ForegroundColor Yellow
grpcurl -plaintext -d '{
  "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
  "limit": 10
}' localhost:9090 chat.ConversationService/GetConversationHistory

# 5. Bob marca como lida
Write-Host "`n4. Bob marca mensagem como lida" -ForegroundColor Yellow
grpcurl -plaintext -d '{
  "message_id": "550e8400-e29b-41d4-a716-446655440010",
  "user_id": "673b4a12-e29b-41d4-a716-44665544021f"
}' localhost:9090 chat.ChatService/MarkMessageAsRead

# 6. Alice verifica status
Write-Host "`n5. Alice verifica status (deve ser READ)" -ForegroundColor Yellow
grpcurl -plaintext -d '{
  "message_id": "550e8400-e29b-41d4-a716-446655440010"
}' localhost:9090 chat.ChatService/GetMessageStatus

Write-Host "`n=== ✅ Teste Completo ===" -ForegroundColor Green
```

---

### ⚠️ 6. Documentação dos endpoints e arquitetura revisada

**Status**: ⚠️ 80% completo

#### Implementado:
- ✅ README.md com arquitetura
- ✅ TESTING.md detalhado
- ✅ POSTMAN-GUIDE.md
- ✅ Spec completa em specs/

#### Faltando:
- ❌ **Atualizar README.md** seção "Endpoints Disponíveis" com exemplos atualizados:
  - Incluir campos corretos (message_id, conversation_id, sender_id, **recipient_id**, message_text)
  - Adicionar endpoint GetConversationHistory
  - Adicionar exemplo de autenticação JWT

**Exemplo a adicionar no README**:

```markdown
## 📊 Endpoints gRPC Disponíveis

### 1. SendMessage - Enviar Mensagem

**Request**:
```json
{
  "message_id": "550e8400-e29b-41d4-a716-446655440000",
  "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
  "sender_id": "673b4a12-e29b-41d4-a716-44665544020f",
  "recipient_id": "673b4a12-e29b-41d4-a716-44665544021f",
  "message_text": "Hello World"
}
```

**Response**:
```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-11-23T10:30:00Z"
}
```

### 2. GetConversationHistory - Listar Mensagens

**Request**:
```json
{
  "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
  "limit": 50,
  "offset": 0
}
```

**Response**:
```json
{
  "messages": [
    {
      "messageId": "...",
      "senderId": "...",
      "messageText": "...",
      "timestamp": "...",
      "currentStatus": "READ"
    }
  ]
}
```

### 3. Autenticação

Todos os endpoints requerem header `Authorization`:

```
Authorization: Bearer <jwt_token>
```

Gerar token de teste:
```powershell
# Token hardcoded para MVP (expira em 1 hora)
$token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```
```

---

### ❌ 7. Log de execução demonstrando troca de mensagens

**Status**: ❌ Não criado formalmente

**Ação necessária**: Rodar script de teste e capturar saída completa

**Criar arquivo**: `logs/demo-communication-log.txt`

```
=== DEMO: Alice → Bob Communication ===
Data: 2025-11-23 10:30:00

1. Alice sends message to Bob
   Request:
   {
     "message_id": "550e8400-e29b-41d4-a716-446655440010",
     "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
     "sender_id": "alice-uuid",
     "recipient_id": "bob-uuid",
     "message_text": "Oi Bob, tudo bem?"
   }
   
   Response:
   {
     "messageId": "550e8400-e29b-41d4-a716-446655440010",
     "timestamp": "2025-11-23T10:30:01Z"
   }
   
   Kafka Log:
   INFO: Message published to Kafka - message_id: 550e8400..., conversation_id: 673b4a12...
   
   Worker Log:
   INFO: Message persisted to MongoDB - message_id: 550e8400..., sequence: 1
   
2. Verify MongoDB persistence
   db.messages.findOne({messageId: "550e8400..."})
   {
     "_id": ObjectId("..."),
     "messageId": "550e8400-e29b-41d4-a716-446655440010",
     "conversationId": "673b4a12-e29b-41d4-a716-44665544010f",
     "senderId": "alice-uuid",
     "messageText": "Oi Bob, tudo bem?",
     "timestamp": ISODate("2025-11-23T10:30:01Z"),
     "sequenceNumber": 1,
     "stateHistory": [
       {
         "state": "SENT",
         "timestamp": ISODate("2025-11-23T10:30:01Z"),
         "recipientId": null
       }
     ]
   }
   
3. Bob queries conversation history
   Request:
   {
     "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
     "limit": 10
   }
   
   Response:
   {
     "messages": [
       {
         "messageId": "550e8400-e29b-41d4-a716-446655440010",
         "senderId": "alice-uuid",
         "messageText": "Oi Bob, tudo bem?",
         "timestamp": "2025-11-23T10:30:01Z",
         "currentStatus": "SENT"
       }
     ]
   }
   
4. Bob marks as read
   Request:
   {
     "message_id": "550e8400-e29b-41d4-a716-446655440010",
     "user_id": "bob-uuid"
   }
   
   Response:
   {
     "messageId": "550e8400-e29b-41d4-a716-446655440010",
     "timestamp": "2025-11-23T10:30:05Z"
   }
   
   Kafka Log:
   INFO: State update event published - message_id: 550e8400..., new_status: READ
   
   Worker Log:
   INFO: Message state updated - message_id: 550e8400..., old_status: SENT, new_status: READ
   
5. Alice checks status
   Request:
   {
     "message_id": "550e8400-e29b-41d4-a716-446655440010"
   }
   
   Response:
   {
     "messageId": "550e8400-e29b-41d4-a716-446655440010",
     "currentStatus": "READ"
   }
   
=== ✅ SUCCESS: Full message lifecycle demonstrated ===
```

---

## 🎯 Resumo de Prioridades

### 🔴 CRÍTICO (bloqueia entrega):
1. ❌ **Implementar GetConversationHistory** (listar mensagens)
2. ❌ **Implementar autenticação JWT básica**
3. ❌ **Criar script de teste Alice → Bob**
4. ❌ **Gerar log de demonstração completo**

### 🟡 IMPORTANTE (melhora entrega):
5. ⚠️ **Atualizar README.md** com exemplos corretos
6. ⚠️ **Documentar endpoints com payloads atualizados**

### 🟢 OPCIONAL (nice-to-have):
7. ✅ Tudo já implementado nesta categoria

---

## 📅 Plano de Execução Sugerido

### Dia 1 (4 horas):
- ✅ Implementar `ConversationServiceImpl.getConversationHistory()` (2h)
- ✅ Implementar autenticação JWT básica (2h)

### Dia 2 (3 horas):
- ✅ Criar script de teste `test-communication.ps1` (1h)
- ✅ Executar teste e capturar log completo (1h)
- ✅ Atualizar README.md com exemplos atualizados (1h)

### Dia 3 (1 hora):
- ✅ Revisão final e validação de todos os itens
- ✅ Preparar apresentação da entrega

---

## ✅ Critérios de Aceitação

Para considerar a entrega **COMPLETA**, todos os itens devem estar ✅:

- [ ] API funcional com SendMessage, GetMessageStatus, MarkMessageAsRead, **GetConversationHistory**
- [ ] Kafka + workers rodando (message-events + state-update-events)
- [ ] MongoDB armazenando mensagens e conversas
- [ ] Autenticação JWT com chave estática implementada
- [ ] Script de teste Alice → Bob executado com sucesso
- [ ] Log de execução capturado mostrando ciclo completo
- [ ] README.md atualizado com endpoints corretos
- [ ] Docker Compose funcional com instruções claras

**Meta**: Todas as atividades concluídas em **3 dias úteis**
