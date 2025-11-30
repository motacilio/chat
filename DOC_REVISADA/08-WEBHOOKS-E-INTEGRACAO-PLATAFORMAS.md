# 08 - Webhooks e Integração com Plataformas

**Versão**: 1.0  
**Status**: ✅ Implementado (mocks WhatsApp/Instagram)

---

## Visão Geral

### Problema

Como receber mensagens de plataformas externas (WhatsApp, Instagram)?

### Solução

**HTTP Webhooks** - Plataformas fazem POST para nosso servidor quando há mensagens novas.

---

## Arquitetura

```
WhatsApp/Instagram Cloud
        │
        │ POST /api/webhooks/{platform}
        ▼
WebhookController (Spring REST)
        │
        ▼
ConversationService (mapeia para interno)
        │
        ▼
Kafka (message-events)
        │
        ▼
MessageDeliveryWorker (persiste + notifica)
```

---

## WebhookController

**Arquivo**: `src/main/java/com/chat/controller/WebhookController.java`

```java
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
    
    @PostMapping("/whatsapp")
    public ResponseEntity<Void> handleWhatsAppWebhook(@RequestBody String payload) {
        logger.info("Received WhatsApp webhook: {}", payload);
        
        // Parse payload (formato específico WhatsApp API)
        WhatsAppMessage message = parseWhatsAppPayload(payload);
        
        // Mapeia para sistema interno
        String internalUserId = linkedAccountService.getUserIdByExternalId(
            message.getFrom(), Platform.WHATSAPP);
        
        String conversationId = conversationService.getOrCreateConversation(
            internalUserId, message.getTo());
        
        // Publica evento Kafka
        MessageEvent event = MessageEvent.newBuilder()
                .setMessageId(UUID.randomUUID().toString())
                .setConversationId(conversationId)
                .setSenderId(internalUserId)
                .setMessageText(message.getText())
                .build();
        
        messageKafkaTemplate.send("message-events", conversationId, event);
        
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/instagram")
    public ResponseEntity<Void> handleInstagramWebhook(@RequestBody String payload) {
        // Similar ao WhatsApp
        return ResponseEntity.ok().build();
    }
}
```

---

## Decisões Arquiteturais

### 1. Webhook vs Polling vs WebSocket (Plataformas)

| Webhook (Escolhido) | Polling | WebSocket Server |
|---------------------|---------|------------------|
| ✅ Push instantâneo | ❌ Delay (intervalo) | ❌ Não suportado por WhatsApp/Instagram |
| ✅ Eficiente | ❌ Overhead (queries vazias) | - |
| ⚠️ Requer IP público | ✅ Sem infraestrutura | - |
| ✅ Padrão indústria | ❌ Não escala | - |

**Decisão**: **Webhook para produção** (requer infraestrutura: IP público, HTTPS, load balancer)

**Fallback POC**: Polling apenas para desenvolvimento/testes

**Por Que WebSocket NÃO funciona**:
- WhatsApp/Instagram APIs não suportam WebSocket em modo servidor
- Nosso sistema seria WebSocket **client** conectando a eles
- Mas eles só oferecem webhook callbacks ou polling

### 2. Diferença: gRPC Streaming vs Webhook

| Uso | Protocolo | Direção |
|-----|-----------|---------|
| **Notificar nossos usuários móveis** | gRPC Server-Side Streaming | Nosso servidor → Dispositivos |
| **Receber de plataformas externas** | HTTP Webhook | WhatsApp/Instagram → Nosso servidor |

**NÃO são substituíveis** - resolvem problemas diferentes!

---

## Mocks (WhatsApp/Instagram)

**WhatsAppMessageWorker**:
```java
@Component
public class WhatsAppMessageWorker {
    
    @KafkaListener(topics = "whatsapp-messages", ...)
    public void handleWhatsAppMessage(PlatformMessageEvent event, ...) {
        // POC: Apenas loga (mock)
        logger.info("MOCK WhatsApp API: Sending message {} to {}", 
                   event.getMessageId(), event.getExternalRecipientId());
        
        // Produção: Chama WhatsApp Business API
        // whatsappClient.sendMessage(event.getExternalRecipientId(), event.getMessageText());
        
        ack.acknowledge();
    }
}
```

---

**Próximo**: [09-OBSERVABILIDADE-E-MONITORAMENTO.md](09-OBSERVABILIDADE-E-MONITORAMENTO.md)
