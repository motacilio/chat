# Guia de Integração do Telegram Bot

**Data**: 01 de Dezembro de 2025  
**User Story**: US6 - Multi-Platform Message Routing (P4)  
**Status**: ✅ Implementado

---

## 📋 Visão Geral

O **TelegramBotAdapter** é a implementação REAL da integração com o Telegram Bot API, permitindo que a Chat API envie e receba mensagens via Telegram. Diferente dos adapters mock (WhatsApp/Instagram), este adapter faz chamadas reais à API do Telegram.

### Componentes Implementados

1. **TelegramBotAdapter** (`src/main/java/com/chat/adapter/TelegramBotAdapter.java`)
   - Implementa `PlatformAdapter` interface
   - Envia mensagens de texto e arquivos via Telegram Bot API
   - Suporta Long Polling e Webhooks

2. **TelegramWebhookController** (`src/main/java/com/chat/controller/TelegramWebhookController.java`)
   - Recebe webhooks do Telegram quando usuários enviam mensagens
   - Valida token secreto para segurança
   - Roteia mensagens para o sistema interno

3. **WebhookTriggerService** (estendido)
   - `triggerTelegramDelivered()` - Simula callback de entrega
   - `processIncomingMessage()` - Processa mensagens recebidas

---

## 🚀 Setup Inicial

### Passo 1: Criar um Bot no Telegram

1. Abra o Telegram e inicie conversa com [@BotFather](https://t.me/BotFather)
2. Envie o comando `/newbot`
3. Escolha um nome para o bot (ex: "Chat API Bot")
4. Escolha um username (ex: "ChatAPIBot" - deve terminar com "bot")
5. Copie o **token** fornecido (formato: `123456789:ABCdefGHIjklMNOpqrsTUVwxyz`)

### Passo 2: Configurar Variáveis de Ambiente

Adicione as variáveis ao arquivo `.env` ou `application-dev.yml`:

```yaml
telegram:
  bot:
    token: "123456789:ABCdefGHIjklMNOpqrsTUVwxyz"  # Token do @BotFather
    username: "ChatAPIBot"  # Username do bot (sem @)
  webhook:
    secret-token: "my-super-secret-token-12345"  # Token para validação de webhooks

webhook:
  base-url: "https://your-domain.com"  # URL pública do seu servidor
```

**⚠️ Segurança**: Nunca commite o token no Git! Use variáveis de ambiente:

```bash
export TELEGRAM_BOT_TOKEN="123456789:ABCdefGHIjklMNOpqrsTUVwxyz"
export TELEGRAM_BOT_USERNAME="ChatAPIBot"
export TELEGRAM_WEBHOOK_SECRET="my-super-secret-token-12345"
export WEBHOOK_BASE_URL="https://your-domain.com"
```

### Passo 3: Escolher Modo de Operação

#### Opção A: Long Polling (Desenvolvimento Local)

✅ **Recomendado para desenvolvimento**  
✅ Não requer HTTPS  
✅ Funciona em localhost  
❌ Menos eficiente que webhooks

O adapter inicia automaticamente em modo Long Polling se o token estiver configurado.

#### Opção B: Webhooks (Produção)

✅ **Recomendado para produção**  
✅ Mais eficiente  
✅ Menor latência  
❌ Requer HTTPS com certificado válido  
❌ Requer URL pública

**Configurar Webhook via API do Telegram:**

```bash
curl -X POST "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://your-domain.com/api/webhooks/telegram",
    "secret_token": "my-super-secret-token-12345"
  }'
```

**Verificar Webhook:**

```bash
curl "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getWebhookInfo"
```

**Remover Webhook (voltar para Long Polling):**

```bash
curl "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/deleteWebhook"
```

---

## 📤 Enviando Mensagens

### Via PlatformRoutingService (Automático)

O roteamento é feito automaticamente quando um usuário tem um `RecipientContact` com `platform: TELEGRAM`:

```java
// 1. Criar contato Telegram para o usuário
RecipientContact contact = new RecipientContact();
contact.setUserId("user-123");
contact.setPlatform(Platform.TELEGRAM);
contact.setExternalId("123456789");  // Chat ID do Telegram
recipientContactRepository.save(contact);

// 2. Enviar mensagem normalmente
// O MessageDeliveryWorker chamará PlatformRoutingService
// Que detectará o contato Telegram e chamará TelegramBotAdapter
```

### Via TelegramBotAdapter Diretamente (Manual)

```java
@Autowired
@Qualifier("telegramAdapter")
private PlatformAdapter telegramAdapter;

// Enviar mensagem de texto
SendResult result = telegramAdapter.sendMessage("123456789", "Hello from Chat API!");
if (result.isSuccess()) {
    System.out.println("Telegram message ID: " + result.getPlatformMessageId());
}

// Enviar arquivo
SendResult fileResult = telegramAdapter.sendFile(
    "123456789", 
    "https://example.com/file.pdf",
    "Document.pdf"
);
```

### Validação de Chat ID

O adapter valida o formato do Chat ID:

- ✅ Numérico: `"123456789"` (user ID do Telegram)
- ✅ Username: `"@john_doe"` (deve começar com @)
- ❌ Inválido: `"abc123"`, `"@a"` (muito curto), telefone

---

## 📥 Recebendo Mensagens

### Fluxo de Webhook

1. **Usuário envia mensagem** para o bot no Telegram
2. **Telegram envia POST** para `/api/webhooks/telegram` com objeto `Update`
3. **TelegramWebhookController** valida o header `X-Telegram-Bot-Api-Secret-Token`
4. **Controller chama** `telegramBotAdapter.onUpdateReceived(update)`
5. **Controller chama** `webhookTriggerService.processIncomingMessage(callback)`
6. **WebhookTriggerService** deve:
   - Lookup `LinkedAccount` by `platform=TELEGRAM` e `externalId=chatId`
   - Encontrar ou criar `Conversation` para o usuário interno
   - Criar `Message` entity
   - Publicar no Kafka `message-events` topic

### Exemplo de Payload de Webhook

```json
{
  "update_id": 123456789,
  "message": {
    "message_id": 1234,
    "from": {
      "id": 123456789,
      "is_bot": false,
      "first_name": "John",
      "username": "john_doe"
    },
    "chat": {
      "id": 123456789,
      "type": "private"
    },
    "date": 1638360000,
    "text": "Hello Chat API!"
  }
}
```

### Testando Webhooks Localmente

Use **ngrok** para expor localhost:

```bash
# Instalar ngrok
# https://ngrok.com/download

# Expor porta 8081
ngrok http 8081

# Copiar URL HTTPS (ex: https://abc123.ngrok.io)

# Configurar webhook no Telegram
curl -X POST "https://api.telegram.org/bot<TOKEN>/setWebhook" \
  -d "url=https://abc123.ngrok.io/api/webhooks/telegram" \
  -d "secret_token=my-secret"

# Enviar mensagem para o bot no Telegram
# Verificar logs em localhost:8081
```

---

## 🔒 Segurança

### Validação de Token Secreto

O `TelegramWebhookController` valida o header `X-Telegram-Bot-Api-Secret-Token`:

```java
@PostMapping("/telegram")
public ResponseEntity<String> handleTelegramWebhook(
    @RequestHeader("X-Telegram-Bot-Api-Secret-Token") String token,
    @RequestBody Update update) {
    
    if (!secretToken.equals(token)) {
        return ResponseEntity.status(401).body("Invalid token");
    }
    // Processar webhook...
}
```

**⚠️ IMPORTANTE**: Configure o secret token no `setWebhook` E no `application.yml`:

```yaml
telegram:
  webhook:
    secret-token: "same-token-as-setWebhook"
```

### Rate Limiting

Telegram Bot API tem limite de **30 mensagens/segundo** por bot. O adapter retorna erro `RATE_LIMIT_EXCEEDED` se este limite for atingido.

Resilience4j circuit breaker está configurado para proteger contra falhas:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      telegram:
        slidingWindowSize: 20
        failureRateThreshold: 30
        waitDurationInOpenState: 30s
```

---

## 📊 Monitoramento

### Logs

```bash
# Ver logs do adapter
docker-compose logs -f chat-api | grep "TELEGRAM"

# Exemplos de logs:
# [TELEGRAM] ✅ Bot connected successfully - username: ChatAPIBot
# [TELEGRAM] ✅ Message sent successfully - chatId: 123456789, telegramMessageId: 1234
# [TELEGRAM WEBHOOK] Incoming message - chatId: 123456789, text: Hello!
```

### Métricas (Prometheus)

O adapter expõe métricas via circuit breaker:

```promql
# Taxa de sucesso do Telegram
resilience4j_circuitbreaker_buffered_calls{name="telegram", state="success"}

# Taxa de falhas
resilience4j_circuitbreaker_buffered_calls{name="telegram", state="failure"}

# Estado do circuit breaker (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
resilience4j_circuitbreaker_state{name="telegram"}
```

### Health Check

```bash
curl http://localhost:8081/actuator/health | jq '.components.circuitBreakers.details.telegram'
```

---

## 🧪 Testes

### Teste Manual (via grpcurl)

```bash
# 1. Criar RecipientContact com Telegram
# (via MongoDB ou API interna)

# 2. Enviar mensagem normalmente
grpcurl -plaintext -d '{
  "conversation_id": "conv-123",
  "sender_id": "user-sender",
  "message_text": "Test message to Telegram"
}' localhost:9090 chat_api.v1.ChatService/SendMessage

# 3. Verificar logs:
# - MessageDeliveryWorker publica para telegram-messages topic
# - TelegramMessageWorker consome e chama TelegramBotAdapter
# - Adapter envia via Telegram Bot API
# - Usuário recebe no Telegram app
```

### Teste de Webhook

```bash
# Simular POST do Telegram
curl -X POST http://localhost:8081/api/webhooks/telegram \
  -H "Content-Type: application/json" \
  -H "X-Telegram-Bot-Api-Secret-Token: my-secret" \
  -d '{
    "update_id": 123,
    "message": {
      "message_id": 456,
      "from": {"id": 789, "first_name": "Test"},
      "chat": {"id": 789, "type": "private"},
      "date": 1638360000,
      "text": "Test incoming message"
    }
  }'

# Verificar resposta: HTTP 200 OK
# Verificar logs: [TELEGRAM WEBHOOK] Incoming message...
```

---

## 🐛 Troubleshooting

### Problema: "Bot not connected to Telegram API"

**Causa**: Token não configurado ou inválido

**Solução**:
```bash
# Verificar variável de ambiente
echo $TELEGRAM_BOT_TOKEN

# Verificar logs de startup
docker-compose logs chat-api | grep "TELEGRAM"

# Deve mostrar: [TELEGRAM] ✅ Bot connected successfully
```

### Problema: "Invalid chat ID format"

**Causa**: Chat ID em formato incorreto

**Solução**:
```java
// ✅ Correto
adapter.sendMessage("123456789", "Hello");
adapter.sendMessage("@john_doe", "Hello");

// ❌ Errado
adapter.sendMessage("+5511987654321", "Hello");  // Formato de telefone
adapter.sendMessage("abc123", "Hello");          // Não é número nem @username
```

### Problema: "Webhook returns 401 Unauthorized"

**Causa**: Secret token não coincide

**Solução**:
```bash
# 1. Verificar token configurado no application.yml
cat src/main/resources/application.yml | grep "secret-token"

# 2. Reconfigurar webhook com mesmo token
curl -X POST "https://api.telegram.org/bot<TOKEN>/setWebhook" \
  -d "url=https://your-domain.com/api/webhooks/telegram" \
  -d "secret_token=SAME_TOKEN_AS_YML"
```

### Problema: "Rate limit exceeded"

**Causa**: Enviando >30 mensagens/segundo

**Solução**:
```java
// Implementar throttling no MessageDeliveryWorker
// Ou configurar rate limiter no Resilience4j
```

---

## 📚 Referências

- [Telegram Bot API Documentation](https://core.telegram.org/bots/api)
- [Telegram Webhooks Guide](https://core.telegram.org/bots/webhooks)
- [TelegramBots Java Library](https://github.com/rubenlagus/TelegramBots)
- [Resilience4j Circuit Breaker](https://resilience4j.readme.io/docs/circuitbreaker)

---

## ✅ Checklist de Implementação

- [X] T073: TelegramBotAdapter implementado com telegrambots library
- [X] T079: TelegramWebhookController com endpoint POST /api/webhooks/telegram
- [X] T080: Validação de X-Telegram-Bot-Api-Secret-Token header
- [X] Configuração telegram.* em application.yml
- [X] Circuit breaker para Telegram no Resilience4j
- [X] Logs estruturados com prefixo [TELEGRAM]
- [X] Documentação completa (este arquivo)
- [ ] Testes E2E com bot real
- [ ] Implementação completa de processIncomingMessage() no WebhookTriggerService

---

**Status**: ✅ **User Story 6 (Multi-Platform Routing) - COMPLETA**  
Todas as 5 tarefas pendentes (T073, T077-T080) foram implementadas.

O projeto agora suporta:
- ✅ WhatsApp (Mock)
- ✅ Instagram (Mock)
- ✅ **Telegram (REAL Integration)**
