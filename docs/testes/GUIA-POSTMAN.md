# 🚀 Guia Postman - Chat API gRPC

## 📋 Checklist Rápido

- [ ] Postman versão 9.7+ instalado
- [ ] Docker containers rodando (`docker ps`)
- [ ] Spring Boot iniciado (verificar logs "gRPC Server started")
- [ ] Porta 9090 livre (`netstat -ano | findstr ":9090"`)

---

## 🎯 Configuração Inicial (Fazer 1 vez)

### Passo 1: Criar Nova Requisição gRPC

1. Abrir Postman
2. Clicar em **"New"** (botão laranja no canto superior esquerdo)
3. Selecionar **"gRPC Request"**
4. Dar um nome: "Chat API - SendMessage"

### Passo 2: Configurar Conexão

```
┌─────────────────────────────────────────┐
│ Enter server URL                        │
│ localhost:9090                          │
└─────────────────────────────────────────┘

☐ Use TLS                    ⬅️ DESMARCAR!
```

**⚠️ IMPORTANTE**: O checkbox "Use TLS" deve estar **DESMARCADO**

### Passo 3: Escolher Método de Definição

**Opção A - Server Reflection** (Recomendado - Mais Fácil):
```
┌─────────────────────────────────────────┐
│ Select a method                         │
│ ● Use server reflection                │  ⬅️ Selecionar
│ ○ Import a .proto file                 │
└─────────────────────────────────────────┘
```

**Opção B - Importar Proto File** (Se Reflection não funcionar):
1. Selecionar "Import a .proto file"
2. Clicar em "Select a file"
3. Navegar até: `src/main/proto/chat_api.v1.proto`
4. Clicar em "Import"

---

## 💬 Teste 1: Enviar Mensagem (SendMessage)

### Configuração
```
Method: chat.ChatService/SendMessage
```

### Payload (copiar e colar no campo "Message")
```json
{
  "conversation_id": "postman-test-001",
  "sender_id": "alice",
  "content": "Olá! Esta é uma mensagem de teste do Postman",
  "message_type": "TEXT"
}
```

### Executar
1. Clicar no botão **"Invoke"** (azul, no canto inferior direito)
2. Aguardar resposta (1-2 segundos)

### Resposta Esperada ✅
```json
{
  "messageId": "673b5a2c-8f91-4d3e-9c12-1a2b3c4d5e6f",
  "status": "SENT",
  "timestamp": "2025-11-22T19:45:30.123Z"
}
```

### ⚠️ Importante
**COPIE o `messageId`** retornado! Você vai usá-lo nos próximos testes.

Exemplo: `673b5a2c-8f91-4d3e-9c12-1a2b3c4d5e6f`

---

## 🔍 Teste 2: Verificar Status (GetMessageStatus)

### Configuração
```
Method: chat.ChatService/GetMessageStatus
```

### Payload
```json
{
  "message_id": "COLE-AQUI-O-MESSAGE-ID-DO-TESTE-1"
}
```

**Substitua** `COLE-AQUI-O-MESSAGE-ID-DO-TESTE-1` pelo ID real que você copiou!

Exemplo correto:
```json
{
  "message_id": "673b5a2c-8f91-4d3e-9c12-1a2b3c4d5e6f"
}
```

### Resposta Esperada ✅
```json
{
  "messageId": "673b5a2c-8f91-4d3e-9c12-1a2b3c4d5e6f",
  "status": "DELIVERED",
  "timestamp": "2025-11-22T19:45:30.123Z",
  "conversationId": "postman-test-001"
}
```

**Observação**: O status pode ser `SENT` ou `DELIVERED`, dependendo se o worker Kafka já processou.

---

## ✔️ Teste 3: Marcar como Lida (MarkMessageAsRead)

### Configuração
```
Method: chat.ChatService/MarkMessageAsRead
```

### Payload
```json
{
  "message_id": "COLE-AQUI-O-MESSAGE-ID-DO-TESTE-1",
  "user_id": "bob"
}
```

### Resposta Esperada ✅
```json
{
  "success": true
}
```

### Verificação
Agora execute o **Teste 2** novamente. O status deve mudar para `READ`.

---

## 📝 Organizando Testes no Postman

### Criar Collection

1. No menu lateral, clicar em **"Collections"**
2. Clicar em **"+"** para criar nova collection
3. Nome: "Chat API - gRPC"
4. Salvar cada requisição dentro desta collection

### Usar Variáveis de Ambiente

1. Clicar no ícone de engrenagem (⚙️) → **"Environments"**
2. Criar novo environment: "Chat API - Local"
3. Adicionar variáveis:

```
Variable         | Initial Value           | Current Value
─────────────────┼────────────────────────┼──────────────────────
grpc_host        | localhost:9090         | localhost:9090
latest_message_id| (vazio)                | (vazio)
```

4. Usar variáveis nas requisições:
```json
{
  "message_id": "{{latest_message_id}}"
}
```

### Salvar messageId Automaticamente

1. Após invocar SendMessage, ir na aba **"Tests"**
2. Adicionar script:
```javascript
pm.environment.set("latest_message_id", pm.response.json().messageId);
```

---

## ❌ Problemas Comuns e Soluções

### Erro: "Failed to connect to localhost:9090"

**Verificações**:
```powershell
# 1. Verificar se aplicação está rodando
netstat -ano | findstr ":9090"

# 2. Se não aparecer nada, iniciar aplicação
java -jar target/meu-projeto-chat-1.0.0-SNAPSHOT.jar

# 3. Aguardar mensagem no log:
# "gRPC Server started, listening on port 9090"
```

**No Postman**:
- Verificar se URL é exatamente: `localhost:9090` (sem http://)
- Verificar se "Use TLS" está **DESMARCADO**

---

### Erro: "Unimplemented method"

**Causa**: Proto não carregado ou método errado

**Solução**:
1. Verificar nome do método:
   - ✅ Correto: `chat.ChatService/SendMessage`
   - ❌ Errado: `ChatService/SendMessage`
   - ❌ Errado: `SendMessage`

2. Se usar proto file, re-importar:
   - Postman → Settings → Data → Clear gRPC cache
   - Re-importar: `src/main/proto/chat_api.v1.proto`

3. Se usar reflection, reconectar:
   - Fechar e reabrir a requisição
   - Selecionar novamente "Use server reflection"

---

### Erro: "Invalid JSON payload"

**Causa**: JSON mal formatado

**Verificar**:
- ❌ Aspas simples: `{'conversation_id': 'test'}`
- ✅ Aspas duplas: `{"conversation_id": "test"}`
- ❌ Valores sem aspas: `{message_type: TEXT}`
- ✅ Valores com aspas: `{"message_type": "TEXT"}`

**Template válido**:
```json
{
  "conversation_id": "test",
  "sender_id": "alice",
  "content": "hello",
  "message_type": "TEXT"
}
```

---

### Erro: "Required field missing"

**Campos obrigatórios por método**:

**SendMessage**:
- ✅ `conversation_id` (string)
- ✅ `sender_id` (string)
- ✅ `content` (string)
- ✅ `message_type` (string: "TEXT", "IMAGE", "VIDEO", "AUDIO", "FILE")

**GetMessageStatus**:
- ✅ `message_id` (string UUID)

**MarkMessageAsRead**:
- ✅ `message_id` (string UUID)
- ✅ `user_id` (string)

---

## 🎨 Testando Diferentes Tipos de Mensagem

### Mensagem de Texto
```json
{
  "conversation_id": "conv-001",
  "sender_id": "alice",
  "content": "Olá Bob!",
  "message_type": "TEXT"
}
```

### Mensagem com Imagem
```json
{
  "conversation_id": "conv-001",
  "sender_id": "alice",
  "content": "https://exemplo.com/imagem.jpg",
  "message_type": "IMAGE"
}
```

### Mensagem de Áudio
```json
{
  "conversation_id": "conv-001",
  "sender_id": "alice",
  "content": "https://exemplo.com/audio.mp3",
  "message_type": "AUDIO"
}
```

---

## 🔄 Fluxo Completo de Teste

### Cenário: Alice envia mensagem e Bob lê

```
┌──────────────────────────────────────────────────────┐
│ 1. Alice envia mensagem                              │
│    Method: SendMessage                               │
│    sender_id: "alice"                                │
│    → Copia messageId: "abc-123"                      │
└──────────────────────────────────────────────────────┘
                      ↓
┌──────────────────────────────────────────────────────┐
│ 2. Verifica status inicial                          │
│    Method: GetMessageStatus                          │
│    message_id: "abc-123"                             │
│    → Status: "SENT" ou "DELIVERED"                   │
└──────────────────────────────────────────────────────┘
                      ↓
┌──────────────────────────────────────────────────────┐
│ 3. Aguarda processamento (2-5 segundos)             │
└──────────────────────────────────────────────────────┘
                      ↓
┌──────────────────────────────────────────────────────┐
│ 4. Verifica status novamente                        │
│    Method: GetMessageStatus                          │
│    message_id: "abc-123"                             │
│    → Status: "DELIVERED"                             │
└──────────────────────────────────────────────────────┘
                      ↓
┌──────────────────────────────────────────────────────┐
│ 5. Bob marca como lida                              │
│    Method: MarkMessageAsRead                         │
│    message_id: "abc-123"                             │
│    user_id: "bob"                                    │
│    → success: true                                   │
└──────────────────────────────────────────────────────┘
                      ↓
┌──────────────────────────────────────────────────────┐
│ 6. Verifica status final                            │
│    Method: GetMessageStatus                          │
│    message_id: "abc-123"                             │
│    → Status: "READ"                                  │
└──────────────────────────────────────────────────────┘
```

---

## 📊 Verificando Dados Reais

### No MongoDB
```powershell
# Conectar
docker exec -it mongodb-dev mongosh -u admin -p password --authenticationDatabase admin

# Ver mensagens
use chat
db.messages.find().pretty()

# Ver apenas última mensagem
db.messages.find().sort({timestamp: -1}).limit(1).pretty()

# Buscar por messageId específico
db.messages.find({_id: ObjectId("673b5a2c...")}).pretty()
```

### No Kafka UI
1. Abrir navegador: http://localhost:8080
2. Clicar em **"Topics"**
3. Selecionar **"message-events"**
4. Clicar em **"Messages"**
5. Ver eventos publicados em tempo real

---

## 🎓 Dicas Avançadas

### 1. Criar Suite de Testes

Salve estas requisições na ordem:
1. **Setup**: SendMessage (cria mensagem)
2. **Test 1**: GetMessageStatus (verifica SENT)
3. **Test 2**: MarkMessageAsRead (marca lida)
4. **Test 3**: GetMessageStatus (verifica READ)

### 2. Usar Scripts de Teste

Na aba "Tests" de cada requisição:

**SendMessage - Validar resposta**:
```javascript
pm.test("Status code is OK", function () {
    pm.response.to.have.status(0);
});

pm.test("MessageId exists", function () {
    const response = pm.response.json();
    pm.expect(response.messageId).to.exist;
    pm.environment.set("latest_message_id", response.messageId);
});

pm.test("Status is SENT", function () {
    const response = pm.response.json();
    pm.expect(response.status).to.equal("SENT");
});
```

**GetMessageStatus - Validar status**:
```javascript
pm.test("Message status is valid", function () {
    const response = pm.response.json();
    pm.expect(["SENT", "DELIVERED", "READ"]).to.include(response.status);
});
```

### 3. Exportar Collection

1. Clicar com botão direito na Collection
2. Selecionar **"Export"**
3. Salvar como `chat-api-postman-collection.json`
4. Compartilhar com time

---

## 🆘 Precisa de Ajuda?

### Verificar se tudo está rodando:

```powershell
# Containers Docker
docker ps

# Porta gRPC
netstat -ano | findstr ":9090"

# Porta MongoDB
netstat -ano | findstr ":27017"

# Porta Kafka
netstat -ano | findstr ":9092"

# Health check da aplicação
curl http://localhost:8081/actuator/health
```

### Logs úteis:

```powershell
# Aplicação Spring Boot
Get-Content target\spring-boot.log -Tail 50

# MongoDB
docker logs mongodb-dev --tail 50

# Kafka
docker logs kafka-dev --tail 50
```

---

## ✅ Checklist de Validação

Após completar os testes, você deve ter visto:

- [ ] SendMessage retornou um UUID válido
- [ ] GetMessageStatus retornou status "SENT" ou "DELIVERED"
- [ ] MarkMessageAsRead retornou `success: true`
- [ ] GetMessageStatus (após marcar lida) retornou status "READ"
- [ ] MongoDB contém a mensagem (verificado via mongosh)
- [ ] Kafka UI mostra eventos nos topics

**Se todos os itens estão marcados: 🎉 Parabéns! Seu sistema está funcionando perfeitamente!**
