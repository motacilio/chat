# 🚀 Comandos grpcurl Validados

> Referência rápida de comandos testados e funcionais para Windows PowerShell

⚠️ **IMPORTANTE:** O banco MongoDB correto é `chat`, **NÃO** `chat_dev`!

## ⚙️ Setup Inicial

### 1. Criar Usuários de Teste

```powershell
# Definir IDs
$aliceId = "a1a1a1a1-1111-1111-1111-111111111111"
$bobId = "b2b2b2b2-2222-2222-2222-222222222222"
$carlosId = "c3c3c3c3-3333-3333-3333-333333333333"

# Inserir no MongoDB (BANCO: chat)
docker exec -i mongodb-dev mongosh chat --quiet --eval "db.users.insertMany([{userId: '$aliceId', name: 'Alice Silva', email: 'alice@test.com'}, {userId: '$bobId', name: 'Bob Santos', email: 'bob@test.com'}, {userId: '$carlosId', name: 'Carlos Pereira', email: 'carlos@test.com'}])"
```

**✅ Saída Esperada:**
```json
{
  "acknowledged": true,
  "insertedIds": {
    "0": ObjectId("6932ec927e0795ca279dc29d"),
    "1": ObjectId("6932ec927e0795ca279dc29e"),
    "2": ObjectId("6932ec927e0795ca279dc29f")
  }
}
```

---

## 📁 ConversationService

### Criar Conversa Privada (1:1)

```powershell
grpcurl -plaintext -import-path src/main/proto -proto conversation_service.proto -d '{\"type\": \"PRIVATE\", \"participant_ids\": [\"a1a1a1a1-1111-1111-1111-111111111111\", \"b2b2b2b2-2222-2222-2222-222222222222\"]}' localhost:9090 chat_api.v1.ConversationService/CreateConversation
```

**✅ Saída Esperada:**
```json
{
  "conversationId": "095cda8e-40a9-4414-9f73-cfe6b3b9c0e3",
  "type": "PRIVATE",
  "participantIds": [
    "a1a1a1a1-1111-1111-1111-111111111111",
    "b2b2b2b2-2222-2222-2222-222222222222"
  ],
  "createdAt": "2025-12-05T14:31:49.055686200Z"
}
```

**💡 Salvar conversationId para próximos testes:**
```powershell
$conversationId = "095cda8e-40a9-4414-9f73-cfe6b3b9c0e3"
```

---

### Criar Grupo

```powershell
grpcurl -plaintext -import-path src/main/proto -proto conversation_service.proto -d '{\"type\": \"GROUP\", \"participant_ids\": [\"a1a1a1a1-1111-1111-1111-111111111111\", \"b2b2b2b2-2222-2222-2222-222222222222\", \"c3c3c3c3-3333-3333-3333-333333333333\"], \"name\": \"Grupo de Testes\"}' localhost:9090 chat_api.v1.ConversationService/CreateConversation
```

**✅ Saída Esperada:**
```json
{
  "conversationId": "162c076e-ad87-4790-8f48-c0c5014b1239",
  "type": "GROUP",
  "participantIds": [
    "a1a1a1a1-1111-1111-1111-111111111111",
    "b2b2b2b2-2222-2222-2222-222222222222",
    "c3c3c3c3-3333-3333-3333-333333333333"
  ],
  "createdAt": "2025-12-05T14:34:04.687225Z"
}
```

---

### Listar Conversas do Usuário

```powershell
grpcurl -plaintext -import-path src/main/proto -proto conversation_service.proto -d '{\"user_id\": \"a1a1a1a1-1111-1111-1111-111111111111\", \"limit\": 10, \"offset\": 0}' localhost:9090 chat_api.v1.ConversationService/ListConversations
```

---

### Buscar Detalhes de uma Conversa

```powershell
grpcurl -plaintext -import-path src/main/proto -proto conversation_service.proto -d '{\"conversation_id\": \"095cda8e-40a9-4414-9f73-cfe6b3b9c0e3\"}' localhost:9090 chat_api.v1.ConversationService/GetConversation
```

---

### Buscar Histórico de Mensagens

```powershell
grpcurl -plaintext -import-path src/main/proto -proto conversation_service.proto -d '{\"conversation_id\": \"095cda8e-40a9-4414-9f73-cfe6b3b9c0e3\", \"limit\": 10, \"offset\": 0}' localhost:9090 chat_api.v1.ConversationService/GetConversationHistory
```

---

## 💬 ChatService

### Enviar Mensagem de Texto

```powershell
grpcurl -plaintext -import-path src/main/proto -proto chat_service.proto -d '{\"conversation_id\": \"095cda8e-40a9-4414-9f73-cfe6b3b9c0e3\", \"sender_id\": \"a1a1a1a1-1111-1111-1111-111111111111\", \"recipient_id\": \"b2b2b2b2-2222-2222-2222-222222222222\", \"message_text\": \"Hello from grpcurl!\"}' localhost:9090 chat_api.v1.ChatService/SendMessage
```

**⚠️ Nota:** Se receber erro "Authentication required", a autenticação JWT está ativa.

---

## 🔍 Comandos de Inspeção

### Listar Serviços Disponíveis

```powershell
# ConversationService
grpcurl -plaintext -import-path src/main/proto -proto conversation_service.proto localhost:9090 list

# ChatService
grpcurl -plaintext -import-path src/main/proto -proto chat_service.proto localhost:9090 list
```

---

### Descrever Serviço

```powershell
# ConversationService
grpcurl -plaintext -import-path src/main/proto -proto conversation_service.proto describe chat_api.v1.ConversationService

# ChatService
grpcurl -plaintext -import-path src/main/proto -proto chat_service.proto describe chat_api.v1.ChatService
```

---

### Descrever Mensagens (Request/Response)

```powershell
# CreateConversationRequest
grpcurl -plaintext -import-path src/main/proto -proto conversation_service.proto describe chat_api.v1.CreateConversationRequest

# SendMessageRequest
grpcurl -plaintext -import-path src/main/proto -proto chat_service.proto describe chat_api.v1.SendMessageRequest

# GetConversationHistoryRequest
grpcurl -plaintext -import-path src/main/proto -proto conversation_service.proto describe chat_api.v1.GetConversationHistoryRequest
```

---

## 🗄️ Comandos MongoDB (Validação)

### Verificar Usuários

```powershell
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.users.find({}).toArray()'
```

---

### Verificar Conversas

```powershell
# Todas as conversas
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.conversations.find({}).toArray()'

# Apenas PRIVATE
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.conversations.find({"type": "PRIVATE"}).toArray()'

# Apenas GROUP
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.conversations.find({"type": "GROUP"}).toArray()'
```

---

### Verificar Mensagens

```powershell
# Últimas 5 mensagens
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.messages.find({}).sort({"timestamp": -1}).limit(5).toArray()'

# Mensagens de uma conversa específica
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.messages.find({"conversationId": "095cda8e-40a9-4414-9f73-cfe6b3b9c0e3"}).toArray()'
```

---

### Verificar Collections Existentes

```powershell
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.getCollectionNames()'
```

---

## 🧹 Limpeza de Dados (Testes)

### Limpar Todas as Conversas

```powershell
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.conversations.deleteMany({})'
```

---

### Limpar Todas as Mensagens

```powershell
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.messages.deleteMany({})'
```

---

### Limpar Todos os Usuários

```powershell
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.users.deleteMany({})'
```

---

### Reset Completo (Todas as Collections)

```powershell
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.dropDatabase()'
```

**⚠️ Cuidado:** Isso apaga TODOS os dados do banco `chat`!

---

## 🐛 Troubleshooting

### Erro: "Failed to list services: server does not support the reflection API"

**Causa:** gRPC Reflection não está habilitada no servidor.

**Solução:** Sempre use `-import-path src/main/proto -proto <arquivo>.proto`

---

### Erro: "Authentication required"

**Causa:** JWT authentication está ativa.

**Solução:** Verificar que profile `dev` está ativo:
```powershell
Get-Content src/main/resources/application.yml | Select-String "active:"
```

---

### Erro: "Invalid UUID format"

**Causa:** UUIDs devem seguir formato: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`

**Solução:** Usar UUIDs do seed:
- Alice: `a1a1a1a1-1111-1111-1111-111111111111`
- Bob: `b2b2b2b2-2222-2222-2222-222222222222`
- Carlos: `c3c3c3c3-3333-3333-3333-333333333333`

---

### Erro: "conversation_service.proto does not reside in any import path"

**Causa:** Comando executado de diretório errado.

**Solução:** Executar a partir da raiz do projeto:
```powershell
cd C:\Users\marcos.pereira\Desktop\programacao\java\chat\chat
```

---

## 📋 Template para Novos Testes

```powershell
# 1. Criar variável com conversationId
$convId = "SEU-CONVERSATION-ID-AQUI"

# 2. Executar comando
grpcurl -plaintext `
  -import-path src/main/proto `
  -proto conversation_service.proto `
  -d '{\"conversation_id\": \"'+ $convId +'\", \"limit\": 10}' `
  localhost:9090 `
  chat_api.v1.ConversationService/GetConversationHistory

# Nota: Use backticks ` para quebrar linha no PowerShell
```

---

## ✅ Checklist de Validação Completa

```powershell
# 1. Verificar servidor rodando
Test-NetConnection -ComputerName localhost -Port 9090 -InformationLevel Quiet

# 2. Criar usuários (banco: chat)
# (executar script de seed acima)

# 3. Criar conversa privada
# (executar comando CreateConversation PRIVATE)

# 4. Criar grupo
# (executar comando CreateConversation GROUP)

# 5. Verificar no MongoDB (banco correto: chat)
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.conversations.countDocuments({})'
# Deve retornar: 2 (ou mais)

# 6. Ver conversas criadas
docker exec -i mongodb-dev mongosh chat --quiet --eval 'db.conversations.find({}, {conversationId: 1, type: 1, participantIds: 1}).toArray()'

# 7. ✅ Tudo funcionando!
Write-Host "[OK] Sistema validado!" -ForegroundColor Green
```

---

**Última Atualização:** 05/12/2025  
**Testado em:** Windows 11, PowerShell 5.1, grpcurl 1.8.9
