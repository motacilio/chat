# Guia de Teste - Chat API

## ⚡ Quick Start (5 minutos)

### 📘 Setup Completo (Copie e Cole)

Execute este script PowerShell para configurar tudo automaticamente:

```powershell
# 1. Iniciar infraestrutura Docker
Write-Host "=== Iniciando Docker Compose ===" -ForegroundColor Cyan
docker-compose -f docker-compose.dev.yml up -d

# 2. Aguardar containers ficarem saudáveis
Write-Host "=== Aguardando containers iniciarem ===" -ForegroundColor Yellow
Start-Sleep -Seconds 15

# 3. Criar conversação de teste no MongoDB
Write-Host "=== Criando conversação de teste ===" -ForegroundColor Green
docker exec mongodb-dev mongosh --eval 'db.getSiblingDB("chat").conversations.insertOne({conversationId:"673b4a12-e29b-41d4-a716-44665544010f",type:"PRIVATE",participants:["673b4a12-e29b-41d4-a716-44665544020f","673b4a12-e29b-41d4-a716-44665544021f"],createdAt:new Date(),lastMessageTimestamp:new Date()})'

# 4. Iniciar aplicação Spring Boot
Write-Host "=== Iniciando Spring Boot (aguarde 20 segundos) ===" -ForegroundColor Magenta
Start-Job -ScriptBlock {
    Set-Location "C:\Users\marcos.pereira\Desktop\programacao\java\chat\chat"
    java -jar target/meu-projeto-chat-1.0.0-SNAPSHOT.jar
}

Start-Sleep -Seconds 20

Write-Host "`n=== ✅ PRONTO PARA TESTAR! ===" -ForegroundColor Green
Write-Host "Postman: localhost:9090" -ForegroundColor Cyan
Write-Host "Kafka UI: http://localhost:8080" -ForegroundColor Cyan
Write-Host "Health: http://localhost:8081/actuator/health" -ForegroundColor Cyan
```

**Agora abra o Postman e teste!**

---

### 📘 Guia Completo do Postman

**👉 Veja o guia detalhado em: [`POSTMAN-GUIDE.md`](./POSTMAN-GUIDE.md)**

Inclui:
- ✅ Configuração passo a passo com screenshots
- ✅ Exemplos de payload para todos os métodos
- ✅ Troubleshooting de erros comuns
- ✅ Dicas de automação e variáveis
- ✅ Fluxo completo de teste

### Para testar com Postman:

1. **Iniciar infraestrutura e aplicação**:
```powershell
# Terminal 1: Docker
docker-compose -f docker-compose.dev.yml up -d

# Terminal 2: Spring Boot (aguardar ~10 segundos após Docker)
java -jar target/meu-projeto-chat-1.0.0-SNAPSHOT.jar
```

2. **Abrir Postman**:
   - New → gRPC Request
   - URL: `localhost:9090`
   - ⚠️ **DESMARCAR** "Use TLS"
   - Método: Selecionar "Use Server Reflection"

3. **Primeiro teste**:
   - Método: `chat.ChatService/SendMessage`
   - ⚠️ **IMPORTANTE**: Use o payload COMPLETO abaixo (inclui `recipient_id`)
   - Message:
   ```json
   {
     "message_id": "550e8400-e29b-41d4-a716-446655440000",
     "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
     "sender_id": "673b4a12-e29b-41d4-a716-44665544020f",
     "recipient_id": "673b4a12-e29b-41d4-a716-44665544021f",
     "message_text": "Hello Postman"
   }
   ```
   - Clique em "Invoke"
   - ✅ Deve retornar o mesmo `messageId` com status `SENT`
   
   **🚨 ERRO COMUM**: Se receber `Invalid UUID format for recipient_id: Hello Postman`, você esqueceu de incluir o campo `recipient_id`. Veja seção Troubleshooting.

---

## ✅ Status da Aplicação

A aplicação Spring Boot está **100% funcional** com:

- **gRPC Server**: Porta 9090
- **Health Check**: http://localhost:8081/actuator/health
- **Kafka**: Topics auto-criados (message-events, state-update-events)
- **MongoDB**: Conectado em mongodb://localhost:27017/chat
- **Kafka UI**: http://localhost:8080

## 🚀 Como Rodar

### 1. Iniciar Infraestrutura (Docker)

```powershell
docker-compose -f docker-compose.dev.yml up -d
```

**Serviços iniciados:**
- MongoDB (porta 27017)
- Kafka (porta 9092)
- Zookeeper (porta 2181)
- Kafka UI (porta 8080)

### 2. Iniciar Spring Boot

```powershell
java -jar target/meu-projeto-chat-1.0.0-SNAPSHOT.jar
```

**Aguardar mensagens de sucesso:**
```
✓ Started ChatApiApplication in X seconds
✓ gRPC Server started, listening on port 9090
✓ partitions assigned: [message-events-0]
✓ partitions assigned: [state-update-events-0]
```

## 🧪 Testando Endpoints gRPC

### ⚠️ IMPORTANTE: Formato UUID Válido

Todos os IDs (`message_id`, `conversation_id`, `sender_id`, `recipient_id`) **DEVEM** seguir o formato RFC 4122:

**Formato Correto**: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
- 8 dígitos hex - 4 dígitos hex - 4 dígitos hex - 4 dígitos hex - **12 dígitos hex**

**Exemplos Válidos**:
- ✅ `673b4a12-e29b-41d4-a716-44665544010f` (12 dígitos no final)
- ✅ `550e8400-e29b-41d4-a716-446655440000` (12 dígitos no final)
- ✅ `f47ac10b-58cc-4372-a567-0e02b2c3d479` (12 dígitos no final)

**Exemplos Inválidos**:
- ❌ `673b4a12-e29b-41d4-a716-446655440100` (13 dígitos no final)
- ❌ `550e8400-e29b-41d4-a716-4466554401` (10 dígitos no final)
- ❌ `not-a-uuid-format`

**Gerar UUID válido** (PowerShell):
```powershell
[guid]::NewGuid().ToString()
# Exemplo de saída: f47ac10b-58cc-4372-a567-0e02b2c3d479
```

---

### Opção 1: Usando Postman (Recomendado para Iniciantes)

#### Configuração Inicial

1. **Abra o Postman** (versão 9.7 ou superior)
2. Clique em **"New"** → **"gRPC Request"**
3. Configure a URL do servidor:
   ```
   localhost:9090
   ```
4. **Importante**: Desmarque "Use TLS" (nossa conexão é plaintext)

#### Importar Proto Files

**Método 1: Via Server Reflection** (Mais Fácil)
1. No Postman, selecione **"Server Reflection"**
2. Clique em **"Use Server Reflection"**
3. Os métodos aparecerão automaticamente

**Método 2: Importar Proto Manualmente**
1. Clique em **"Import .proto file"**
2. Navegue até: `src/main/proto/chat_api.v1.proto`
3. Adicione também as dependências:
   - `specs/001-ubiquitous-messaging-platform/contracts/chat_service.proto`
   - `specs/001-ubiquitous-messaging-platform/contracts/common_types.proto`
   - `specs/001-ubiquitous-messaging-platform/contracts/conversation_service.proto`

#### Teste 0: Criar Conversação (IMPORTANTE - FAZER PRIMEIRO!)

**⚠️ ATENÇÃO**: Antes de enviar mensagens, você **DEVE** criar uma conversação no MongoDB!

**Opção 1 - Via MongoDB Shell (Mais Rápido)**:
```powershell
docker exec -it mongodb-dev mongosh

# No shell do MongoDB:
use chat

db.conversations.insertOne({
  "conversationId": "673b4a12-e29b-41d4-a716-44665544010f",
  "type": "PRIVATE",
  "participants": [
    "673b4a12-e29b-41d4-a716-44665544020f",
    "673b4a12-e29b-41d4-a716-44665544021f"
  ],
  "createdAt": new Date(),
  "lastMessageTimestamp": new Date()
})
```

**Opção 2 - Via PowerShell (Comando Único)**:
```powershell
docker exec mongodb-dev mongosh --eval 'db.getSiblingDB("chat").conversations.insertOne({conversationId:"673b4a12-e29b-41d4-a716-44665544010f",type:"PRIVATE",participants:["673b4a12-e29b-41d4-a716-44665544020f","673b4a12-e29b-41d4-a716-44665544021f"],createdAt:new Date(),lastMessageTimestamp:new Date()})'
```

**✅ Confirmação**: Você deve ver `acknowledged: true` na resposta.

---

#### Teste 1: SendMessage

**⚠️ REQUISITO**: Execute o **Teste 0** (criar conversação) antes deste teste!

1. Selecione o método: **`chat.ChatService/SendMessage`**
2. No campo **Message**, cole:
```json
{
  "message_id": "673b4a12-e29b-41d4-a716-44665544000f",
  "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
  "sender_id": "673b4a12-e29b-41d4-a716-44665544020f",
  "recipient_id": "673b4a12-e29b-41d4-a716-44665544021f",
  "message_text": "Olá via Postman!"
}
```

**Importante**: O `sender_id` ("673b4a12-e29b-41d4-a716-44665544020f") **DEVE** estar na lista de `participants` da conversação!

3. Clique em **"Invoke"**
4. **Resposta esperada**:
```json
{
  "messageId": "673b4a12-e29b-41d4-a716-44665544000f",
  "status": "SENT",
  "timestamp": "2025-11-22T19:30:00Z",
  "sequenceNumber": "1"
}
```
5. **Copie o `messageId`** para os próximos testes

#### Teste 2: GetMessageStatus

1. Selecione o método: **`chat.ChatService/GetMessageStatus`**
2. No campo **Message**, cole (substituindo o ID):
```json
{
  "message_id": "673b4a12-COLE-O-ID-AQUI"
}
```
3. Clique em **"Invoke"**
4. **Resposta esperada**:
```json
{
  "messageId": "673b4a12-...",
  "status": "DELIVERED",
  "timestamp": "2025-11-22T19:30:00Z",
  "conversationId": "conv-postman-123"
}
```

#### Teste 3: MarkMessageAsRead

1. Selecione o método: **`chat.ChatService/MarkMessageAsRead`**
2. No campo **Message**, cole:
```json
{
  "message_id": "673b4a12-COLE-O-ID-AQUI",
  "user_id": "user-bob"
}
```
3. Clique em **"Invoke"**
4. **Resposta esperada**:
```json
{
  "success": true
}
```

#### Dicas do Postman

- **Salvar Requests**: Clique em "Save" para criar uma Collection
- **Variáveis**: Use `{{messageId}}` para reutilizar valores
- **Environment**: Configure variáveis de ambiente para `baseUrl`, etc.
- **History**: Veja todas as chamadas anteriores na aba "History"

---

### Opção 2: Usando grpcurl (CLI)

#### Instalação

```powershell
# Windows (Chocolatey)
choco install grpcurl

# Windows (Scoop)
scoop install grpcurl
```

### 1. SendMessage - Enviar Mensagem

#### Via Postman
Já descrito acima na seção "Opção 1"

#### Via grpcurl (CLI)

```powershell
grpcurl -plaintext -d '{
  "message_id": "550e8400-e29b-41d4-a716-446655440000",
  "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
  "sender_id": "673b4a12-e29b-41d4-a716-44665544020f",
  "recipient_id": "673b4a12-e29b-41d4-a716-44665544021f",
  "message_text": "Olá! Como vai?"
}' localhost:9090 chat.ChatService/SendMessage
```

**Resposta esperada:**
```json
{
  "messageId": "<uuid-gerado>",
  "status": "SENT",
  "timestamp": "2024-11-22T18:30:00Z"
}
```

**O que acontece:**
1. Mensagem persistida no MongoDB (collection `messages`)
2. Evento publicado no Kafka topic `message-events`
3. Worker consome evento e atualiza status para `DELIVERED`

### 2. GetMessageStatus - Consultar Status

#### Via Postman
Já descrito acima na seção "Opção 1"

#### Via grpcurl (CLI)

```powershell
grpcurl -plaintext -d '{
  "message_id": "<uuid-do-sendmessage>"
}' localhost:9090 chat.ChatService/GetMessageStatus
```

**Resposta esperada:**
```json
{
  "messageId": "<uuid>",
  "status": "DELIVERED",
  "timestamp": "2024-11-22T18:30:00Z",
  "conversationId": "conv-123"
}
```

### 3. MarkMessageAsRead - Marcar como Lida

#### Via Postman
Já descrito acima na seção "Opção 1"

#### Via grpcurl (CLI)

```powershell
grpcurl -plaintext -d '{
  "message_id": "<uuid-do-sendmessage>",
  "user_id": "user-bob"
}' localhost:9090 chat.ChatService/MarkMessageAsRead
```

**Resposta esperada:**
```json
{
  "success": true
}
```

**O que acontece:**
1. Evento de leitura publicado no Kafka topic `state-update-events`
2. Worker consome e atualiza status para `READ`
3. Collection `messageReadStatus` atualizada no MongoDB

---

### Opção 3: Alternativas ao Postman

#### BloomRPC (Interface Gráfica Simples)
1. Download: https://github.com/bloomrpc/bloomrpc/releases
2. Abrir BloomRPC
3. Importar proto: `src/main/proto/chat_api.v1.proto`
4. Server URL: `localhost:9090`
5. Desmarcar TLS
6. Testar métodos com JSON

#### Insomnia (Similar ao Postman)
1. Download: https://insomnia.rest/download
2. New Request → gRPC
3. URL: `localhost:9090`
4. Importar proto files
5. Testar endpoints

#### grpcui (Interface Web via CLI)
```powershell
# Instalar
go install github.com/fullstorydev/grpcui/cmd/grpcui@latest

# Executar (abre navegador automaticamente)
grpcui -plaintext localhost:9090
```

---

### 📸 Tutorial Visual Postman (Passo a Passo)

#### 1️⃣ Criar Nova Requisição gRPC
![image](https://github.com/user-attachments/assets/...)
- Abra Postman
- Clique em "New" → "gRPC Request"

#### 2️⃣ Configurar Servidor
```
Server URL: localhost:9090
☐ Use TLS (desmarcar)
```

#### 3️⃣ Importar Proto
- Clique em "Import a .proto file"
- Selecione: `src/main/proto/chat_api.v1.proto`

#### 4️⃣ Selecionar Método
- Dropdown: `chat.ChatService/SendMessage`

#### 5️⃣ Preencher Mensagem
```json
{
  "message_id": "550e8400-e29b-41d4-a716-446655440002",
  "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
  "sender_id": "673b4a12-e29b-41d4-a716-44665544020f",
  "recipient_id": "673b4a12-e29b-41d4-a716-44665544021f",
  "message_text": "Hello from Postman"
}
```

#### 6️⃣ Invocar e Ver Resposta
- Clique em "Invoke"
- Veja resposta no painel inferior

---

## 📊 Verificar Dados no MongoDB

```powershell
# Conectar ao MongoDB
docker exec -it mongodb-dev mongosh -u admin -p password --authenticationDatabase admin

# No shell do MongoDB
use chat

# Ver mensagens enviadas
db.messages.find().pretty()

# Ver status de leitura
db.messageReadStatus.find().pretty()

# Ver conversas
db.conversations.find().pretty()
```

## 📈 Monitorar Kafka

### Via Kafka UI
Abra http://localhost:8080 no navegador:
- **Topics**: Ver message-events e state-update-events
- **Messages**: Inspecionar eventos publicados
- **Consumer Groups**: Ver lag dos workers

### Via CLI

```powershell
# Listar topics
docker exec kafka-dev kafka-topics --list --bootstrap-server localhost:9092

# Ver mensagens do topic
docker exec kafka-dev kafka-console-consumer --bootstrap-server localhost:9092 --topic message-events --from-beginning
```

## 🛠️ Troubleshooting

### ❌ Erro: "Invalid UUID format for recipient_id: Olá! Teste..."

**Sintoma**:
```
Invalid UUID format for recipient_id: Olá! Teste com recipient_id
```

**Causa**: Você está usando um payload **DESATUALIZADO** que não inclui o campo `recipient_id`, ou os campos estão na ordem errada.

**❌ PAYLOAD ERRADO** (causa o erro):
```json
{
  "message_id": "673b4a12-e29b-41d4-a716-44665544000f",
  "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
  "sender_id": "673b4a12-e29b-41d4-a716-44665544020f",
  "message_text": "Olá! Teste com recipient_id"
}
```

**Por que falha?**
O protobuf espera os campos nesta ordem:
1. `message_id` ✅
2. `conversation_id` ✅
3. `sender_id` ✅
4. `recipient_id` ❌ **FALTANDO!**
5. `message_text` 

Quando você não envia `recipient_id`, o protobuf lê `message_text` no lugar do `recipient_id`, causando erro de validação UUID.

**✅ PAYLOAD CORRETO** (funciona):
```json
{
  "message_id": "673b4a12-e29b-41d4-a716-44665544000f",
  "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
  "sender_id": "673b4a12-e29b-41d4-a716-44665544020f",
  "recipient_id": "673b4a12-e29b-41d4-a716-44665544021f",
  "message_text": "Olá! Teste com recipient_id"
}
```

**Solução Rápida**: Copie e cole este payload válido no Postman/grpcurl:
```json
{
  "message_id": "673b4a12-e29b-41d4-a716-44665544000f",
  "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
  "sender_id": "673b4a12-e29b-41d4-a716-44665544020f",
  "recipient_id": "673b4a12-e29b-41d4-a716-44665544021f",
  "message_text": "Olá! Agora funciona!"
}
```

**Nota**: Se você está usando Server Reflection no Postman, pode ser necessário **reconectar** para obter a definição atualizada do protobuf. Clique em "Disconnect" e depois em "Connect" novamente.

---

### ❌ Erro: "Conversation not found" ao enviar mensagem

**Sintoma**:
```
INVALID_ARGUMENT: Conversation not found: 673b4a12-e29b-41d4-a716-44665544010f
```

**Causa**: Você está tentando enviar uma mensagem para uma conversação que não existe no MongoDB.

**Solução**:
1. **Criar a conversação primeiro** (ver "Teste 0" acima):
   ```powershell
   docker exec mongodb-dev mongosh --eval 'db.getSiblingDB("chat").conversations.insertOne({conversationId:"673b4a12-e29b-41d4-a716-44665544010f",type:"PRIVATE",participants:["673b4a12-e29b-41d4-a716-44665544020f","673b4a12-e29b-41d4-a716-44665544021f"],createdAt:new Date(),lastMessageTimestamp:new Date()})'
   ```

2. **Verificar se a conversação foi criada**:
   ```powershell
   docker exec mongodb-dev mongosh --eval 'db.getSiblingDB("chat").conversations.find({conversationId:"673b4a12-e29b-41d4-a716-44665544010f"}).pretty()'
   ```

3. **Agora sim, enviar a mensagem** via Postman usando o mesmo `conversation_id`

**Entendendo o fluxo**:
- ✅ **CORRETO**: Criar conversação → Enviar mensagem
- ❌ **ERRADO**: Enviar mensagem sem conversação existente

---

### ❌ Erro: "User X is not a participant in conversation Y"

**Sintoma**:
```
PERMISSION_DENIED: User 673b4a12-e29b-41d4-a716-44665544020f is not a participant in conversation 673b4a12-e29b-41d4-a716-44665544010f
```

**Causa**: O `sender_id` na mensagem não está na lista de `participants` da conversação.

**Solução**:
1. **Verificar participantes da conversação**:
   ```powershell
   docker exec mongodb-dev mongosh --eval 'db.getSiblingDB("chat").conversations.findOne({conversationId:"673b4a12-e29b-41d4-a716-44665544010f"}).participants'
   ```

2. **Usar um sender_id que esteja na lista**:
   - Se a conversação tem participantes `["user-alice", "user-bob"]`
   - O `sender_id` deve ser `"user-alice"` OU `"user-bob"`

3. **Ou recriar a conversação com os participantes corretos**:
   ```powershell
   # Deletar conversação antiga
   docker exec mongodb-dev mongosh --eval 'db.getSiblingDB("chat").conversations.deleteOne({conversationId:"673b4a12-e29b-41d4-a716-44665544010f"})'
   
   # Criar nova com participantes corretos
   docker exec mongodb-dev mongosh --eval 'db.getSiblingDB("chat").conversations.insertOne({conversationId:"673b4a12-e29b-41d4-a716-44665544010f",type:"PRIVATE",participants:["673b4a12-e29b-41d4-a716-44665544020f","673b4a12-e29b-41d4-a716-44665544021f"],createdAt:new Date(),lastMessageTimestamp:new Date()})'
   ```

---

### ❌ Postman: "Failed to connect" ou "Connection refused"

**Causas:**
1. Aplicação Spring Boot não está rodando
2. TLS habilitado incorretamente
3. Porta errada

**Soluções:**
```powershell
# 1. Verificar se app está rodando
netstat -ano | findstr ":9090"

# 2. Verificar logs da aplicação
Get-Content target\spring-boot.log -Tail 20 | Select-String "gRPC"

# 3. Testar conexão básica
Test-NetConnection localhost -Port 9090

# 4. No Postman: DESMARCAR "Use TLS"
```

### ❌ Postman: "Method not found" ou "Service not available"

**Causas:**
1. Proto file não importado corretamente
2. Service name errado

**Soluções:**
1. **Re-importar proto file**:
   - Postman → Settings → Data → Clear gRPC cache
   - Importar novamente: `src/main/proto/chat_api.v1.proto`

2. **Verificar service via reflection**:
   ```powershell
   grpcurl -plaintext localhost:9090 list
   # Deve mostrar: chat.ChatService
   
   grpcurl -plaintext localhost:9090 list chat.ChatService
   # Deve mostrar métodos disponíveis
   ```

3. **Service correto**: `chat.ChatService` (não `ChatService`)

### ❌ Postman: "Invalid JSON" ou "Parse error"

**Problema**: JSON mal formatado

**Solução**: Use este template válido:
```json
{
  "message_id": "550e8400-e29b-41d4-a716-446655440003",
  "conversation_id": "550e8400-e29b-41d4-a716-446655440103",
  "sender_id": "550e8400-e29b-41d4-a716-446655440203",
  "message_text": "Hello"
}
```

**Atenção**:
- `message_id`, `conversation_id`, `sender_id` devem ser UUIDs válidos (formato: 8-4-4-4-12 caracteres hex)
- Use `message_text` (não `content`)
- Todos os campos são strings (use aspas)
- Não use aspas simples, apenas duplas

### ❌ Postman: "Proto import failed"

**Problema**: Dependências do proto não encontradas

**Solução 1 - Copiar arquivos**:
```powershell
# Copiar protos para um único diretório
New-Item -ItemType Directory -Force -Path ".\proto-all"
Copy-Item "src\main\proto\*.proto" ".\proto-all\"
Copy-Item "specs\001-ubiquitous-messaging-platform\contracts\*.proto" ".\proto-all\"

# Importar do diretório proto-all/ no Postman
```

**Solução 2 - Server Reflection** (Mais fácil):
- No Postman, selecione "Use Server Reflection"
- Não precisa importar nenhum arquivo!

### Aplicação não inicia

```powershell
# Verificar logs
Get-Content target\spring-boot.log -Tail 50

# Verificar se portas estão livres
netstat -ano | findstr ":9090"  # gRPC
netstat -ano | findstr ":8081"  # Actuator
```

### Kafka consumers não conectam

```powershell
# Verificar status dos containers
docker ps

# Ver logs do Kafka
docker logs kafka-dev --tail 100

# Recriar topics
docker exec kafka-dev kafka-topics --delete --topic message-events --bootstrap-server localhost:9092
docker exec kafka-dev kafka-topics --delete --topic state-update-events --bootstrap-server localhost:9092
```

### MongoDB não conecta

```powershell
# Testar conexão
docker exec mongodb-dev mongosh -u admin -p password --authenticationDatabase admin --eval "db.runCommand({ ping: 1 })"

# Ver logs
docker logs mongodb-dev --tail 100
```

## 📝 Fluxo Completo de Teste

### Cenário: Alice envia mensagem para Bob

```powershell
# 1. Enviar mensagem
$response = grpcurl -plaintext -d '{
  "message_id": "550e8400-e29b-41d4-a716-446655440004",
  "conversation_id": "673b4a12-e29b-41d4-a716-44665544010f",
  "sender_id": "673b4a12-e29b-41d4-a716-44665544020f",
  "recipient_id": "673b4a12-e29b-41d4-a716-44665544021f",
  "message_text": "Oi Bob, tudo bem?"
}' localhost:9090 chat.ChatService/SendMessage

# Pegar o messageId da resposta
$messageId = "<copiar-uuid-da-resposta>"

# 2. Verificar status (deve estar DELIVERED após alguns segundos)
grpcurl -plaintext -d "{\"message_id\": \"$messageId\"}" localhost:9090 chat.ChatService/GetMessageStatus

# 3. Bob marca como lida
grpcurl -plaintext -d "{\"message_id\": \"$messageId\", \"user_id\": \"bob\"}" localhost:9090 chat.ChatService/MarkMessageAsRead

# 4. Verificar status novamente (deve estar READ)
grpcurl -plaintext -d "{\"message_id\": \"$messageId\"}" localhost:9090 chat.ChatService/GetMessageStatus

# 5. Verificar no MongoDB
docker exec -it mongodb-dev mongosh -u admin -p password --authenticationDatabase admin
use chat
db.messages.find({_id: ObjectId("$messageId")}).pretty()
db.messageReadStatus.find({messageId: "$messageId"}).pretty()
```

## 🎯 Validações Realizadas

✅ **Setup Completo:**
- Docker Compose configurado (MongoDB, Kafka, Zookeeper)
- Spring Boot compilado e empacotado
- Ignore files criados (.gitignore, .dockerignore)

✅ **Integração Kafka:**
- Topics auto-criados
- Producers configurados com tipos específicos
- Consumers conectados e processando partições
- Consumer groups funcionais

✅ **Persistência MongoDB:**
- Conexão estabelecida
- Collections criadas automaticamente
- Índices configurados

✅ **gRPC Server:**
- Servidor iniciado na porta 9090
- ChatService registrado
- Endpoints disponíveis

## 📚 Documentação Adicional

- **Proto Files**: `specs/001-ubiquitous-messaging-platform/contracts/*.proto`
- **Data Model**: `specs/001-ubiquitous-messaging-platform/data-model.md`
- **Architecture**: `specs/001-ubiquitous-messaging-platform/plan.md`
- **Quickstart**: `specs/001-ubiquitous-messaging-platform/quickstart.md`

## 🔧 Comandos Úteis

```powershell
# Parar tudo
docker-compose -f docker-compose.dev.yml down

# Limpar volumes (reset completo)
docker-compose -f docker-compose.dev.yml down -v

# Rebuild da aplicação
mvn clean package -DskipTests

# Ver logs da aplicação em tempo real
Get-Content target\spring-boot.log -Wait
```

## 📞 Próximos Passos

1. **Testes Automatizados**: Executar testes de integração com `mvn test`
2. **Performance**: Testar throughput com múltiplas mensagens simultâneas
3. **Resiliência**: Simular falhas (desligar Kafka/MongoDB e verificar recovery)
4. **Métricas**: Acessar http://localhost:8081/actuator/metrics para observabilidade
5. **Produção**: Configurar docker-compose.yml com replica sets e autenticação completa
