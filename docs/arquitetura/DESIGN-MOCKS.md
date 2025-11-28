# Design de Mocks para Testes - Multi-Platform Messaging

**Data**: 2024-11-24  
**Contexto**: Phase 8 - User Story 6 (Multi-Platform Message Routing - Priority P4)  
**Objetivo**: Criar mocks educacionais e funcionais para testar roteamento de mensagens para plataformas externas SEM implementar integrações reais ainda

---

## 1. Visão Geral

### 1.1 Propósito dos Mocks

Os mocks servem para:

1. **Validar a arquitetura do adapter pattern** antes de integrar APIs reais
2. **Testar o fluxo de roteamento** de mensagens para múltiplas plataformas
3. **Demonstrar conceitos educacionais** de sistemas distribuídos (adapter, registry, routing)
4. **Permitir testes end-to-end** sem dependências externas custosas

### 1.2 Estratégia de Implementação

```
┌─────────────────────────────────────────────────────────────┐
│                    MessageService                           │
│  (envia mensagem internamente via Kafka)                    │
└───────────────────┬─────────────────────────────────────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │ PlatformRoutingService│
         │  (identifica plataformas) │
         └──────────┬───────────┘
                    │
        ┌───────────┼───────────┐
        │           │           │
        ▼           ▼           ▼
  ┌─────────┐ ┌─────────┐ ┌─────────┐
  │Telegram │ │WhatsApp │ │Instagram│
  │ Adapter │ │  Mock   │ │  Mock   │
  └─────────┘ └─────────┘ └─────────┘
      │             │           │
      │ (real API)  │ (log)     │ (log)
      ▼             ▼           ▼
```

**Decisões de Design**:

- ✅ **Interface única** (`PlatformAdapter`) para todos os adapters
- ✅ **Mocks com logging detalhado** para visibilidade do comportamento
- ✅ **Simulação de latência** para testar timeouts
- ✅ **Simulação de falhas** para testar circuit breaker
- ✅ **Telegram como referência real** (será implementado depois)
- ✅ **WhatsApp e Instagram como mocks** (apenas console output)

---

## 2. Estrutura de Arquivos

```
src/main/java/com/chat/
├── adapter/
│   ├── PlatformAdapter.java              # Interface base (T072)
│   ├── TelegramBotAdapter.java           # Real (T073 - futuro)
│   ├── WhatsAppMockAdapter.java          # Mock (T074)
│   ├── InstagramMockAdapter.java         # Mock (T075)
│   └── dto/
│       ├── PlatformCredentials.java      # Credenciais de plataforma
│       ├── ConnectionResult.java         # Resultado de conexão
│       └── SendResult.java               # Resultado de envio
│
├── model/
│   ├── LinkedAccount.java                # Entity 7 (T070)
│   └── Platform.java                     # Enum (TELEGRAM, WHATSAPP, INSTAGRAM)
│
├── repository/
│   └── LinkedAccountRepository.java      # MongoDB repository (T071)
│
├── service/
│   ├── AdapterRegistry.java              # Registry pattern (T076)
│   └── PlatformRoutingService.java       # Routing logic (T077)
│
└── config/
    └── PlatformAdapterConfig.java        # Spring Bean configuration
```

---

## 3. Contratos e Interfaces

### 3.1 PlatformAdapter Interface

```java
package com.chat.adapter;

import com.chat.adapter.dto.ConnectionResult;
import com.chat.adapter.dto.PlatformCredentials;
import com.chat.adapter.dto.SendResult;
import com.chat.model.FileMetadata;

/**
 * RESPONSABILIDADE: Define o contrato para integração com plataformas externas.
 * 
 * NÃO RESPONSÁVEL POR: Armazenar credenciais (ver PlatformCredentials), 
 * gerenciar LinkedAccounts (ver PlatformRoutingService), retry logic (ver circuit breaker).
 * 
 * CONCEITO DE SISTEMAS DISTRIBUÍDOS:
 * Adapter Pattern - permite adicionar novas plataformas sem modificar código existente.
 * Cada implementação encapsula a lógica específica da plataforma (API calls, autenticação).
 * 
 * DESIGN EDUCACIONAL:
 * - Método connect() demonstra autenticação com serviços externos
 * - sendMessage() mostra comunicação assíncrona com APIs REST
 * - sendFile() ilustra transfer de arquivos via URLs pré-assinadas
 * - Cada método retorna Result objects (não exceptions) para error handling explícito
 */
public interface PlatformAdapter {
    
    /**
     * Estabelece conexão com a plataforma externa.
     * 
     * @param credentials Credenciais específicas da plataforma (token, secret, etc.)
     * @return ConnectionResult com status (SUCCESS/FAILURE) e mensagem de erro se houver
     * 
     * EXEMPLO DE USO:
     * - Telegram: registra webhook via setWebhook API
     * - WhatsApp: valida Business API token
     * - Instagram: autentica via Graph API
     */
    ConnectionResult connect(PlatformCredentials credentials);
    
    /**
     * Envia mensagem de texto para usuário externo.
     * 
     * @param externalUserId ID do usuário na plataforma externa (phone, username, chatId)
     * @param messageText Texto da mensagem (max 4096 chars conforme Telegram)
     * @return SendResult com messageId da plataforma e timestamp de envio
     * 
     * EDGE CASES:
     * - externalUserId inválido → FAILURE com erro "User not found"
     * - messageText muito longo → FAILURE com erro "Message too long"
     * - API rate limit → FAILURE com erro "Rate limit exceeded"
     */
    SendResult sendMessage(String externalUserId, String messageText);
    
    /**
     * Envia arquivo para usuário externo.
     * 
     * @param externalUserId ID do usuário na plataforma externa
     * @param file Metadados do arquivo (storage_url, mime_type, size)
     * @return SendResult com messageId da plataforma
     * 
     * NOTA: Implementação real deve fazer download do storage_url e enviar para API da plataforma.
     * Mocks apenas logam o envio.
     */
    SendResult sendFile(String externalUserId, FileMetadata file);
    
    /**
     * Retorna o nome da plataforma (TELEGRAM, WHATSAPP, INSTAGRAM).
     * Usado pelo AdapterRegistry para registro.
     */
    String getPlatformName();
}
```

### 3.2 DTOs de Resultado

```java
package com.chat.adapter.dto;

import java.time.Instant;

/**
 * Resultado de tentativa de conexão com plataforma externa.
 */
public class ConnectionResult {
    private final boolean success;
    private final String message;
    private final Instant timestamp;
    
    public static ConnectionResult success(String message) {
        return new ConnectionResult(true, message, Instant.now());
    }
    
    public static ConnectionResult failure(String errorMessage) {
        return new ConnectionResult(false, errorMessage, Instant.now());
    }
    
    // Constructor, getters
}

/**
 * Resultado de envio de mensagem.
 */
public class SendResult {
    private final boolean success;
    private final String platformMessageId;  // ID gerado pela plataforma
    private final String errorMessage;
    private final Instant sentAt;
    
    public static SendResult success(String platformMessageId) {
        return new SendResult(true, platformMessageId, null, Instant.now());
    }
    
    public static SendResult failure(String errorMessage) {
        return new SendResult(false, null, errorMessage, Instant.now());
    }
    
    // Constructor, getters
}

/**
 * Credenciais de acesso à plataforma.
 */
public class PlatformCredentials {
    private final String platform;   // TELEGRAM, WHATSAPP, INSTAGRAM
    private final String apiToken;
    private final String apiSecret;
    private final String webhookUrl; // Para receber mensagens da plataforma
    
    // Constructor, getters
}
```

---

## 4. Implementação dos Mocks

### 4.1 WhatsAppMockAdapter

**Comportamento Simulado**:

- ✅ Loga todas as operações no console (INFO level)
- ✅ Simula latência de 100-300ms (delay aleatório)
- ✅ Retorna sucesso em 95% dos casos (simula 5% de falhas)
- ✅ Gera IDs de mensagem no formato WhatsApp: `wamid.HBgNNTUxMTk4NzY1NDMyMRUCABIYFjNFQjBDMTAyRjRBNjQ5QzNCQjhGNzkA`

```java
package com.chat.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chat.adapter.dto.ConnectionResult;
import com.chat.adapter.dto.PlatformCredentials;
import com.chat.adapter.dto.SendResult;
import com.chat.model.FileMetadata;
import com.chat.model.Platform;

import java.util.Random;
import java.util.UUID;

/**
 * MOCK ADAPTER para WhatsApp Business API.
 * 
 * PROPÓSITO EDUCACIONAL:
 * - Demonstra o padrão Adapter sem custos de API real ($0.005/mensagem)
 * - Simula comportamento real (latência, falhas ocasionais)
 * - Permite testes de integração end-to-end
 * 
 * COMPORTAMENTO:
 * - connect(): Sempre sucesso (não valida token de verdade)
 * - sendMessage(): 95% sucesso, 5% falha aleatória
 * - sendFile(): 95% sucesso, 5% falha aleatória
 * - Latência simulada: 100-300ms
 * 
 * NOTAS DE IMPLEMENTAÇÃO REAL (FUTURO):
 * - API: https://developers.facebook.com/docs/whatsapp/cloud-api
 * - Autenticação: Bearer token (WhatsApp Business Account)
 * - Rate limits: 1000 msgs/24h (tier 1), 10000 msgs/24h (tier 2)
 * - Webhooks: Receber mensagens via Graph API webhooks
 */
@Component("whatsappAdapter")
public class WhatsAppMockAdapter implements PlatformAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(WhatsAppMockAdapter.class);
    private final Random random = new Random();
    
    @Override
    public ConnectionResult connect(PlatformCredentials credentials) {
        log.info("🟢 [WhatsApp MOCK] Connecting with token: {}...", 
                credentials.getApiToken().substring(0, Math.min(10, credentials.getApiToken().length())));
        
        simulateLatency();
        
        log.info("✅ [WhatsApp MOCK] Connected successfully (SIMULATED)");
        return ConnectionResult.success("WhatsApp mock connection established");
    }
    
    @Override
    public SendResult sendMessage(String externalUserId, String messageText) {
        log.info("📤 [WhatsApp MOCK] Sending message to phone: {}", externalUserId);
        log.debug("📄 [WhatsApp MOCK] Message preview: {}", 
                messageText.substring(0, Math.min(50, messageText.length())) + "...");
        
        simulateLatency();
        
        // Simula 5% de falhas para testes de retry
        if (random.nextDouble() < 0.05) {
            String error = "WhatsApp rate limit exceeded (SIMULATED)";
            log.error("❌ [WhatsApp MOCK] Send failed: {}", error);
            return SendResult.failure(error);
        }
        
        // Gera ID no formato WhatsApp
        String messageId = "wamid." + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        log.info("✅ [WhatsApp MOCK] Message sent successfully - ID: {}", messageId);
        
        return SendResult.success(messageId);
    }
    
    @Override
    public SendResult sendFile(String externalUserId, FileMetadata file) {
        log.info("📎 [WhatsApp MOCK] Sending file to phone: {}", externalUserId);
        log.debug("📁 [WhatsApp MOCK] File: {} ({} bytes, {})", 
                file.getFilename(), file.getSizeBytes(), file.getMimeType());
        
        simulateLatency();
        
        // Simula 5% de falhas
        if (random.nextDouble() < 0.05) {
            String error = "WhatsApp file upload failed (SIMULATED)";
            log.error("❌ [WhatsApp MOCK] File send failed: {}", error);
            return SendResult.failure(error);
        }
        
        String messageId = "wamid." + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        log.info("✅ [WhatsApp MOCK] File sent successfully - ID: {}", messageId);
        
        return SendResult.success(messageId);
    }
    
    @Override
    public String getPlatformName() {
        return Platform.WHATSAPP.name();
    }
    
    /**
     * Simula latência de rede (100-300ms).
     * WhatsApp Cloud API tem latência real de ~200-500ms.
     */
    private void simulateLatency() {
        try {
            int latency = 100 + random.nextInt(200); // 100-300ms
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 4.2 InstagramMockAdapter

**Comportamento Simulado**:

- ✅ Similar ao WhatsApp mas com formato de IDs do Instagram
- ✅ Simula latência de 150-400ms (Instagram API é mais lenta)
- ✅ Retorna sucesso em 90% dos casos (10% de falhas - Instagram é menos confiável)
- ✅ IDs no formato Instagram: `ig_mid.123456789012345`

```java
package com.chat.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chat.adapter.dto.ConnectionResult;
import com.chat.adapter.dto.PlatformCredentials;
import com.chat.adapter.dto.SendResult;
import com.chat.model.FileMetadata;
import com.chat.model.Platform;

import java.util.Random;

/**
 * MOCK ADAPTER para Instagram Messaging API.
 * 
 * PROPÓSITO EDUCACIONAL:
 * - Demonstra adapter para plataforma com API menos estável
 * - Simula taxa de falha maior (10% vs 5% do WhatsApp)
 * - Ilustra tratamento de erros em sistemas distribuídos
 * 
 * COMPORTAMENTO:
 * - connect(): Sempre sucesso
 * - sendMessage(): 90% sucesso, 10% falha aleatória
 * - sendFile(): 90% sucesso, 10% falha aleatória
 * - Latência simulada: 150-400ms (Instagram API é mais lenta)
 * 
 * NOTAS DE IMPLEMENTAÇÃO REAL (FUTURO):
 * - API: https://developers.facebook.com/docs/messenger-platform
 * - Autenticação: Page Access Token (Facebook Graph API)
 * - Limitações: Apenas mensagens 1:1 (sem grupos)
 * - Webhooks: Receber via Messenger Platform webhooks
 */
@Component("instagramAdapter")
public class InstagramMockAdapter implements PlatformAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(InstagramMockAdapter.class);
    private final Random random = new Random();
    
    @Override
    public ConnectionResult connect(PlatformCredentials credentials) {
        log.info("🟣 [Instagram MOCK] Connecting with page token: {}...", 
                credentials.getApiToken().substring(0, Math.min(10, credentials.getApiToken().length())));
        
        simulateLatency();
        
        log.info("✅ [Instagram MOCK] Connected successfully (SIMULATED)");
        return ConnectionResult.success("Instagram mock connection established");
    }
    
    @Override
    public SendResult sendMessage(String externalUserId, String messageText) {
        log.info("📤 [Instagram MOCK] Sending message to @{}", externalUserId);
        log.debug("📄 [Instagram MOCK] Message preview: {}", 
                messageText.substring(0, Math.min(50, messageText.length())) + "...");
        
        simulateLatency();
        
        // Simula 10% de falhas (Instagram é menos confiável)
        if (random.nextDouble() < 0.10) {
            String error = "Instagram API temporarily unavailable (SIMULATED)";
            log.error("❌ [Instagram MOCK] Send failed: {}", error);
            return SendResult.failure(error);
        }
        
        // Gera ID no formato Instagram
        String messageId = "ig_mid." + Math.abs(random.nextLong());
        log.info("✅ [Instagram MOCK] Message sent successfully - ID: {}", messageId);
        
        return SendResult.success(messageId);
    }
    
    @Override
    public SendResult sendFile(String externalUserId, FileMetadata file) {
        log.info("📎 [Instagram MOCK] Sending file to @{}", externalUserId);
        log.debug("📁 [Instagram MOCK] File: {} ({} bytes, {})", 
                file.getFilename(), file.getSizeBytes(), file.getMimeType());
        
        simulateLatency();
        
        // Simula 10% de falhas
        if (random.nextDouble() < 0.10) {
            String error = "Instagram file upload timeout (SIMULATED)";
            log.error("❌ [Instagram MOCK] File send failed: {}", error);
            return SendResult.failure(error);
        }
        
        String messageId = "ig_mid." + Math.abs(random.nextLong());
        log.info("✅ [Instagram MOCK] File sent successfully - ID: {}", messageId);
        
        return SendResult.success(messageId);
    }
    
    @Override
    public String getPlatformName() {
        return Platform.INSTAGRAM.name();
    }
    
    /**
     * Simula latência de rede (150-400ms).
     * Instagram Messaging API tem latência real de ~300-600ms.
     */
    private void simulateLatency() {
        try {
            int latency = 150 + random.nextInt(250); // 150-400ms
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

---

## 5. Adapter Registry

```java
package com.chat.service;

import com.chat.adapter.PlatformAdapter;
import com.chat.model.Platform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * RESPONSABILIDADE: Gerencia registro de adapters de plataforma.
 * 
 * CONCEITO DE SISTEMAS DISTRIBUÍDOS:
 * Registry Pattern - centraliza descoberta de serviços.
 * Permite adicionar/remover adapters em runtime sem modificar código cliente.
 * 
 * DESIGN EDUCACIONAL:
 * - Demonstra injeção de dependência com @Qualifier
 * - Ilustra padrão Factory para criação de adapters
 * - Usa Map para lookup O(1) de adapters por plataforma
 */
@Service
public class AdapterRegistry {
    
    private final Map<String, PlatformAdapter> adapters = new HashMap<>();
    
    @Autowired
    public AdapterRegistry(
            @Qualifier("telegramAdapter") PlatformAdapter telegramAdapter,
            @Qualifier("whatsappAdapter") PlatformAdapter whatsappAdapter,
            @Qualifier("instagramAdapter") PlatformAdapter instagramAdapter) {
        
        adapters.put(Platform.TELEGRAM.name(), telegramAdapter);
        adapters.put(Platform.WHATSAPP.name(), whatsappAdapter);
        adapters.put(Platform.INSTAGRAM.name(), instagramAdapter);
    }
    
    /**
     * Retorna adapter para a plataforma especificada.
     * 
     * @param platform TELEGRAM, WHATSAPP ou INSTAGRAM
     * @return PlatformAdapter correspondente
     * @throws IllegalArgumentException se plataforma não suportada
     */
    public PlatformAdapter getAdapter(Platform platform) {
        PlatformAdapter adapter = adapters.get(platform.name());
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
        return adapter;
    }
    
    /**
     * Verifica se plataforma é suportada.
     */
    public boolean isSupported(Platform platform) {
        return adapters.containsKey(platform.name());
    }
}
```

---

## 6. Testes Sugeridos

### 6.1 Teste de Envio Simples

```bash
# 1. Criar linked account para alice no WhatsApp
POST /api/linked-accounts
{
  "user_id": "a1a1a1a1-1111-1111-1111-111111111111",
  "platform": "WHATSAPP",
  "external_id": "+5511987654321"
}

# 2. Enviar mensagem para bob (que tem WhatsApp linkado)
POST /api/messages
{
  "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
  "message_text": "Hello from Chat API!",
  "channels": ["WHATSAPP"]
}

# 3. Verificar logs para ver mock output:
docker logs chat-api | grep "WhatsApp MOCK"
```

### 6.2 Teste de Múltiplas Plataformas

```bash
# Enviar mesma mensagem para WhatsApp E Instagram
POST /api/messages
{
  "conversation_id": "550e8400-e29b-41d4-a716-446655440000",
  "message_text": "Multi-platform message",
  "channels": ["WHATSAPP", "INSTAGRAM"]
}
```

### 6.3 Teste de Falhas

```bash
# Enviar 100 mensagens e verificar ~5% de falhas no WhatsApp
for i in {1..100}; do
  curl -X POST http://localhost:8081/api/messages \
    -H "Content-Type: application/json" \
    -d '{"conversation_id":"...","message_text":"Test '$i'","channels":["WHATSAPP"]}'
done

# Verificar taxa de sucesso nos logs
docker logs chat-api | grep "WhatsApp MOCK" | grep -c "✅"
docker logs chat-api | grep "WhatsApp MOCK" | grep -c "❌"
```

---

## 7. Próximos Passos

### 7.1 Implementação Imediata (Mocks)

- [ ] T070: Create LinkedAccount entity
- [ ] T071: Create LinkedAccountRepository  
- [ ] T072: Create PlatformAdapter interface
- [ ] T074: Implement WhatsAppMockAdapter
- [ ] T075: Implement InstagramMockAdapter
- [ ] T076: Create AdapterRegistry
- [ ] T077: Create PlatformRoutingService
- [ ] T078: Update MessageService to call PlatformRoutingService

### 7.2 Implementação Real (Futuro - Phase 8)

- [ ] T073: Implement TelegramBotAdapter
  - [ ] Add telegrambots library to pom.xml
  - [ ] Implement webhook registration
  - [ ] Handle incoming messages
  - [ ] Implement sendMessage via Bot API
  - [ ] Add signature validation

### 7.3 Testes de Integração

- [ ] Teste end-to-end: enviar mensagem → rotear para mock → verificar log
- [ ] Teste de falha: simular erro no adapter → verificar retry logic
- [ ] Teste de múltiplas plataformas: enviar para WhatsApp + Instagram simultaneamente
- [ ] Teste de performance: medir latência do routing (target <50ms)

---

## 8. Métricas de Sucesso

✅ **Mocks implementados**: 100% (WhatsApp, Instagram)  
✅ **Logs visíveis**: Todas as operações loggadas com emojis para facilitar debug  
✅ **Taxa de falha simulada**: 5% WhatsApp, 10% Instagram  
✅ **Latência simulada**: 100-300ms WhatsApp, 150-400ms Instagram  
✅ **Registry funcional**: Lookup de adapters por plataforma  
✅ **Documentação educacional**: Cada classe com comentários explicativos  
