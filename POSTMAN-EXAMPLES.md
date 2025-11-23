# 🧪 Exemplos Postman - UUIDs Válidos

## ⚠️ Problema Comum: UUID Inválido

O sistema valida UUIDs no formato **RFC 4122**:
```
xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

### ❌ ERRADO (Postman vai rejeitar)
```json
{
  "conversation_id": "test-123",           // ❌ Não é UUID
  "sender_id": "alice",                     // ❌ Não é UUID
  "message_id": "msg-001",                  // ❌ Não é UUID
  "message_text": "Hello"
}
```

### ✅ CORRETO (UUID válido)
```json
{
  "conversation_id": "3184a104-6171-45d5-b541-7ee8f36d1062",  // ✅ UUID RFC 4122
  "sender_id": "a1a1a1a1-1111-1111-1111-111111111111",        // ✅ UUID RFC 4122
  "message_id": "00000001-0000-0000-0000-000000000001",       // ✅ UUID RFC 4122
  "message_text": "Hello World"
}
```

---

## 📋 Payloads Completos para Postman

### 1️⃣ CreateConversation

**Método**: `conversation.ConversationService/CreateConversation`  
**URL**: `localhost:9090` (desmarcar TLS)

```json
{
  "type": "PRIVATE",
  "participant_ids": [
    "a1a1a1a1-1111-1111-1111-111111111111",
    "b2b2b2b2-2222-2222-2222-222222222222"
  ]
}
```

**Response esperado**:
```json
{
  "conversationId": "3184a104-6171-45d5-b541-7ee8f36d1062",
  "type": "PRIVATE",
  "participants": [
    {"userId": "a1a1a1a1-1111-1111-1111-111111111111"},
    {"userId": "b2b2b2b2-2222-2222-2222-222222222222"}
  ],
  "createdAt": "2025-11-23T20:15:30.123Z"
}
```

**⚠️ IMPORTANTE**: Copie o `conversationId` retornado para usar nos próximos testes!

---

### 2️⃣ SendMessage

**Método**: `chat.ChatService/SendMessage`  
**URL**: `localhost:9090` (desmarcar TLS)

```json
{
  "conversation_id": "3184a104-6171-45d5-b541-7ee8f36d1062",
  "sender_id": "a1a1a1a1-1111-1111-1111-111111111111",
  "recipient_id": "b2b2b2b2-2222-2222-2222-222222222222",
  "message_id": "00000001-0000-0000-0000-000000000001",
  "message_text": "Olá! Esta é minha primeira mensagem."
}
```

**Response esperado**:
```json
{
  "messageId": "00000001-0000-0000-0000-000000000001",
  "timestamp": "2025-11-23T20:16:45.456Z",
  "sequenceNumber": "1"
}
```

**💡 Dica**: Para cada nova mensagem, incremente o UUID:
- Mensagem 1: `00000001-0000-0000-0000-000000000001`
- Mensagem 2: `00000002-0000-0000-0000-000000000002`
- Mensagem 3: `00000003-0000-0000-0000-000000000003`

---

### 3️⃣ GetMessageStatus

**Método**: `chat.ChatService/GetMessageStatus`  
**URL**: `localhost:9090` (desmarcar TLS)

```json
{
  "message_id": "00000001-0000-0000-0000-000000000001",
  "requester_id": "a1a1a1a1-1111-1111-1111-111111111111"
}
```

**Response esperado**:
```json
{
  "messageId": "00000001-0000-0000-0000-000000000001",
  "currentStatus": "SENT",
  "stateHistory": [
    {
      "status": "SENT",
      "timestamp": "2025-11-23T20:16:45.456Z"
    }
  ]
}
```

---

### 4️⃣ MarkMessageAsRead

**Método**: `chat.ChatService/MarkMessageAsRead`  
**URL**: `localhost:9090` (desmarcar TLS)

```json
{
  "message_id": "00000001-0000-0000-0000-000000000001",
  "reader_id": "b2b2b2b2-2222-2222-2222-222222222222"
}
```

**Response esperado**:
```json
{
  "messageId": "00000001-0000-0000-0000-000000000001",
  "newStatus": "READ",
  "timestamp": "2025-11-23T20:18:00.789Z"
}
```

---

### 5️⃣ GetConversationHistory

**Método**: `conversation.ConversationService/GetConversationHistory`  
**URL**: `localhost:9090` (desmarcar TLS)

```json
{
  "conversation_id": "3184a104-6171-45d5-b541-7ee8f36d1062",
  "requester_id": "a1a1a1a1-1111-1111-1111-111111111111",
  "limit": 10
}
```

**Response esperado**:
```json
{
  "messages": [
    {
      "messageId": "00000001-0000-0000-0000-000000000001",
      "senderId": "a1a1a1a1-1111-1111-1111-111111111111",
      "messageText": "Olá! Esta é minha primeira mensagem.",
      "timestamp": "2025-11-23T20:16:45.456Z",
      "sequenceNumber": "1",
      "stateHistory": [
        {
          "status": "SENT",
          "timestamp": "2025-11-23T20:16:45.456Z"
        },
        {
          "status": "READ",
          "timestamp": "2025-11-23T20:18:00.789Z",
          "recipientId": "b2b2b2b2-2222-2222-2222-222222222222"
        }
      ]
    }
  ],
  "pagination": {
    "totalCount": "1",
    "hasMore": false
  }
}
```

---

### 6️⃣ ListConversations

**Método**: `conversation.ConversationService/ListConversations`  
**URL**: `localhost:9090` (desmarcar TLS)

```json
{
  "user_id": "a1a1a1a1-1111-1111-1111-111111111111",
  "limit": 20
}
```

**Response esperado**:
```json
{
  "conversations": [
    {
      "conversationId": "3184a104-6171-45d5-b541-7ee8f36d1062",
      "type": "PRIVATE",
      "participants": [
        {"userId": "a1a1a1a1-1111-1111-1111-111111111111"},
        {"userId": "b2b2b2b2-2222-2222-2222-222222222222"}
      ],
      "lastMessagePreview": "Olá! Esta é minha primeira mensagem.",
      "lastMessageAt": "2025-11-23T20:16:45.456Z",
      "createdAt": "2025-11-23T20:15:30.123Z"
    }
  ],
  "pagination": {
    "totalCount": "1",
    "hasMore": false
  }
}
```

---

## 🎯 Fluxo Completo de Teste

### Passo 1: Criar Conversa
```json
// CreateConversation
{
  "type": "PRIVATE",
  "participant_ids": [
    "a1a1a1a1-1111-1111-1111-111111111111",
    "b2b2b2b2-2222-2222-2222-222222222222"
  ]
}
```
➡️ **Salve o `conversationId` retornado!**

---

### Passo 2: Enviar Primeira Mensagem
```json
// SendMessage
{
  "conversation_id": "COLE_O_CONVERSATION_ID_AQUI",
  "sender_id": "a1a1a1a1-1111-1111-1111-111111111111",
  "recipient_id": "b2b2b2b2-2222-2222-2222-222222222222",
  "message_id": "00000001-0000-0000-0000-000000000001",
  "message_text": "Primeira mensagem de teste!"
}
```

---

### Passo 3: Consultar Status
```json
// GetMessageStatus
{
  "message_id": "00000001-0000-0000-0000-000000000001",
  "requester_id": "a1a1a1a1-1111-1111-1111-111111111111"
}
```
✅ Deve retornar `currentStatus: "SENT"`

---

### Passo 4: Marcar Como Lida
```json
// MarkMessageAsRead
{
  "message_id": "00000001-0000-0000-0000-000000000001",
  "reader_id": "b2b2b2b2-2222-2222-2222-222222222222"
}
```
✅ Deve retornar `newStatus: "READ"`

---

### Passo 5: Verificar Estado Atualizado
```json
// GetMessageStatus novamente
{
  "message_id": "00000001-0000-0000-0000-000000000001",
  "requester_id": "a1a1a1a1-1111-1111-1111-111111111111"
}
```
✅ Agora `stateHistory` deve ter 2 entradas: SENT → READ

---

### Passo 6: Ver Histórico Completo
```json
// GetConversationHistory
{
  "conversation_id": "COLE_O_CONVERSATION_ID_AQUI",
  "requester_id": "a1a1a1a1-1111-1111-1111-111111111111",
  "limit": 10
}
```
✅ Deve listar todas as mensagens da conversa

---

## 🛠️ Gerando Novos UUIDs

### Opção 1: Online (Rápido)
https://www.uuidgenerator.net/version4

### Opção 2: PowerShell
```powershell
[guid]::NewGuid().ToString()
```

### Opção 3: Linux/Mac
```bash
uuidgen
```

### Opção 4: Python
```python
import uuid
print(uuid.uuid4())
```

---

## 🔧 Troubleshooting

### Erro: "Invalid UUID format"
**Causa**: UUID não está no formato RFC 4122  
**Solução**: Use UUIDs completos com hífens:
- ✅ `a1a1a1a1-1111-1111-1111-111111111111`
- ❌ `alice` ou `test-123`

### Erro: "Conversation not found"
**Causa**: `conversation_id` não existe ou está incorreto  
**Solução**: 
1. Execute `CreateConversation` primeiro
2. Copie o `conversationId` retornado
3. Cole no campo `conversation_id` dos próximos requests

### Erro: "Sender is not a participant"
**Causa**: `sender_id` não está na lista de participantes  
**Solução**: Use apenas os UUIDs especificados em `participant_ids` ao criar a conversa

### Erro: "Message already exists"
**Causa**: `message_id` duplicado (idempotência)  
**Solução**: Use um novo UUID para cada mensagem
- Mensagem 1: `00000001-0000-0000-0000-000000000001`
- Mensagem 2: `00000002-0000-0000-0000-000000000002`

---

## 📝 Notas Importantes

1. **UUIDs são case-insensitive**: `A1A1A1A1...` = `a1a1a1a1...`
2. **Formato obrigatório**: 8-4-4-4-12 caracteres hexadecimais com hífens
3. **Idempotência**: Mesmo `message_id` retorna sucesso sem duplicar
4. **Autorização**: Apenas participantes podem enviar/ler mensagens
5. **Paginação**: Default `limit=20`, máximo `limit=100`

---

**Criado em**: 23 de Novembro de 2025  
**Status**: ✅ Aplicação rodando (PID 34300, portas 9090 e 8081)
