# Mocks Multi-Platform - Implementação Educacional

## ✅ Progresso da Implementação

### Fase 1: Fundamentos (COMPLETO)

- [X] **Platform enum** - Define as 3 plataformas suportadas
  - `TELEGRAM` - Para implementação real futura
  - `WHATSAPP` - Mock com 95% taxa de sucesso
  - `INSTAGRAM` - Mock com 90% taxa de sucesso
  - Validação de formato de external_id por plataforma
  
- [X] **Adapter DTOs** - Objects para comunicação com adapters
  - `ConnectionResult` - Resultado de conexão (success/failure)
  - `SendResult` - Resultado de envio de mensagem
  - `PlatformCredentials` - Credenciais de autenticação

### Fase 2: Interfaces e Contratos (COMPLETO ✅)

- [X] **PlatformAdapter interface** - Contrato base para todos os adapters
  - Métodos: `connect()`, `sendMessage()`, `sendFile()`, `getPlatform()`
  - Documentação completa do adapter pattern e hexagonal architecture
  
- [X] **WhatsAppMockAdapter** - Mock educacional do WhatsApp (FR-039 compliant)
  - ✅ 95% taxa de sucesso
  - ✅ Latência aleatória 100-300ms
  - ✅ Validação E.164 phone format (+5511987654321)
  - ✅ Erros: connection_timeout (2%), rate_limit_exceeded (2%), invalid_recipient (1%)
  - ✅ Geração de mock message ID (formato: wamid.UUID)
  
- [X] **InstagramMockAdapter** - Mock educacional do Instagram (FR-039 compliant)
  - ✅ 90% taxa de sucesso (menos confiável que WhatsApp)
  - ✅ Latência aleatória 150-400ms (maior que WhatsApp)
  - ✅ Validação @username pattern (@john_doe)
  - ✅ Erros: connection_timeout (4%), rate_limit_exceeded (4%), invalid_recipient (2%)
  - ✅ Geração de mock message ID (formato: mid.UUID)
  
- [X] **TelegramBotAdapterStub** - Stub temporário (implementação real = T073 deferred)
  - ✅ Retorna "not_implemented" para todas as operações
  - ✅ Permite Spring iniciar sem erro de autowiring
  - ⏸️ Implementação real será feita em T073 (Telegram Bot API real)

### Fase 3: Registry e Lookup (COMPLETO ✅)

- [X] **AdapterRegistry** - Service Registry pattern para lookup de adapters (T076)
  - ✅ Dependency Injection com @Qualifier para distinguir adapters
  - ✅ EnumMap otimizado para lookup O(1) por Platform enum
  - ✅ Métodos: `getAdapter()`, `hasAdapter()`, `getAdapterCount()`
  - ✅ Documentação educacional completa sobre:
    - Como Spring autowiring funciona com múltiplos beans da mesma interface
    - Por que EnumMap é melhor que HashMap para enum keys
    - Registry pattern vs if/else chains
    - Constructor injection (best practice) vs field/setter injection

### Fase 3: Registry e Lookup (COMPLETO ✅)

- [X] **AdapterRegistry** - Service Registry pattern para lookup de adapters (T076)
  - ✅ Dependency Injection com @Qualifier para distinguir adapters
  - ✅ EnumMap otimizado para lookup O(1) por Platform enum
  - ✅ Métodos: `getAdapter()`, `hasAdapter()`, `getAdapterCount()`
  - ✅ Documentação educacional completa sobre:
    - Como Spring autowiring funciona com múltiplos beans da mesma interface
    - Por que EnumMap é melhor que HashMap para enum keys
    - Registry pattern vs if/else chains
    - Constructor injection (best practice) vs field/setter injection

### Fase 4: Domínio e Persistência (COMPLETO ✅)

- [X] **LinkedAccount entity** - Mapeia users para contas externas (T070)
  - ✅ Campos: user_id, platform (enum), external_id, linked_at
  - ✅ Índices compostos únicos:
    - `(user_id, platform)`: Um usuário só pode ter uma conta por plataforma
    - `(platform, external_id)`: Uma conta externa não pode ser reivindicada por múltiplos usuários
  - ✅ Factory method `create()` com timestamp automático
  - ✅ Documentação educacional completa sobre:
    - Adapter Pattern para integração multi-plataforma
    - Formatos de external_id por plataforma (E.164 phone, @username, numeric ID)
    - Fluxo de roteamento outbound (user → external platforms)
    - Fluxo de webhook inbound (external platform → internal user)
    - Segurança: Índices únicos previnem account hijacking

- [X] **LinkedAccountRepository** - Queries MongoDB (T071)
  - ✅ `findByUserId()`: Busca todas as plataformas vinculadas a um usuário (roteamento outbound)
  - ✅ `findByUserIdAndPlatform()`: Verifica se usuário já vinculou plataforma específica
  - ✅ `findByPlatformAndExternalId()`: Reverse lookup para webhooks inbound
  - ✅ `existsByUserIdAndPlatform()`: Validação antes de vincular
  - ✅ `existsByPlatformAndExternalId()`: Previne duplicate account claims
  - ✅ `deleteByUserId()`: GDPR compliance (remoção de dados)
  - ✅ Documentação educacional completa sobre:
    - Spring Data MongoDB query derivation
    - Padrões de uso (outbound routing, inbound webhooks, account management)
    - Performance: Todas as queries usam índices compostos (O(1) lookup)

- [X] **model/README.md** - Documentação educacional do package
  - ✅ Visão geral das entidades (User, Conversation, Message, LinkedAccount)
  - ✅ Explicação de anotações (@Document, @Id, @Indexed, @CompoundIndex)
  - ✅ Estratégia de índices MongoDB (único, composto, array)
  - ✅ Padrões de relacionamento (referência vs embedding)
  - ✅ LinkedAccount multi-platform integration pattern
  - ✅ Fluxos de roteamento outbound e inbound com exemplos de código
  - ✅ Segurança: Como índices únicos previnem account hijacking
  - ✅ Glossário: DDD, NoSQL, Index, Embedding, Adapter Pattern, UUID, Lombok

### Fase 5: Serviços de Roteamento (PENDENTE)

- [ ] **AdapterRegistry** - Registry pattern para lookup de adapters
- [ ] **PlatformRoutingService** - Lógica de roteamento de mensagens

---

## 📚 Arquitetura dos Mocks

```
┌──────────────────────────────────────────────────────────┐
│               MessageService (Existente)                 │
│          (envia mensagens via Kafka internamente)        │
└────────────────────┬─────────────────────────────────────┘
                     │
                     │ chama quando canais externos são especificados
                     ▼
          ┌───────────────────────┐
          │ PlatformRoutingService│
          │  - Busca LinkedAccounts      │
          │  - Seleciona adapters via Registry │
          └──────────┬────────────┘
                     │
         ┌───────────┼───────────┐
         │           │           │
         ▼           ▼           ▼
    ┌────────┐  ┌────────┐  ┌────────┐
    │Telegram│  │WhatsApp│  │Instagram│
    │Adapter │  │  Mock  │  │  Mock   │
    │ (Stub) │  │        │  │         │
    └────────┘  └────────┘  └────────┘
         │           │           │
         │           │ (log + simulate) │
         │           ▼           ▼
         │      Console Output
         │      [📤 Sending to +5511...]
         │      [✅ Success - wamid.ABC...]
         ▼
    (Futura integração real)
```

---

## 🎯 Decisões de Design Documentadas

### 1. Por que Mocks ao invés de APIs reais?

**Decisão**: Implementar mocks para WhatsApp e Instagram, deixando apenas Telegram como real (futuro).

**Rationale**:
- ✅ **Custo**: WhatsApp Business API cobra $0.005/mensagem + taxa mensal
- ✅ **Complexidade**: Aprovação de conta leva semanas (análise do Facebook)
- ✅ **Educação**: Mocks demonstram adapter pattern sem dependências externas
- ✅ **Testes**: Permite testes end-to-end sem custos ou rate limits
- ✅ **Telegram gratuito**: Bot API é free e sem aprovação prévia

**Alternativas Consideradas**:
- ❌ Twilio WhatsApp API: Ainda cobra por mensagem ($0.005)
- ❌ APIs não oficiais: Violam ToS, APIs instáveis
- ✅ **Escolhido**: Mocks educacionais com logging detalhado

### 2. Por que Result Objects ao invés de Exceptions?

**Decisão**: Usar `ConnectionResult` e `SendResult` com campos `success/failure`.

**Rationale**:
- ✅ **Explícito**: Força tratamento de erros no código cliente
- ✅ **Performance**: Não há overhead de stack trace
- ✅ **Logging**: Facilita log estruturado de falhas
- ✅ **Educacional**: Demonstra error handling em sistemas distribuídos

**Exemplo**:
```java
SendResult result = adapter.sendMessage("+5511987654321", "Hello");
if (!result.isSuccess()) {
    log.error("Failed to send: {}", result.getErrorMessage());
    // Retry logic ou circuit breaker
}
```

### 3. Simulação de Latência e Falhas

**Decisão**: Mocks simulam latência real e falham aleatoriamente.

**Rationale**:
- ✅ **Realismo**: WhatsApp API tem latência de ~200-500ms
- ✅ **Testes de resiliência**: Força implementação de retry logic
- ✅ **Observabilidade**: Permite testar métricas de latência
- ✅ **Circuit breaker**: Valida comportamento com falhas intermitentes

**Parâmetros**:
- WhatsApp Mock: 100-300ms latência, 5% taxa de falha
- Instagram Mock: 150-400ms latência, 10% taxa de falha (menos confiável)

### 4. Registry Pattern para Adapters

**Decisão**: AdapterRegistry gerencia lookup de adapters por plataforma.

**Rationale**:
- ✅ **Extensibilidade**: Adicionar novo adapter sem modificar código existente
- ✅ **Testabilidade**: Permite mock de adapters em testes unitários
- ✅ **Spring DI**: Usa @Qualifier para injeção type-safe
- ✅ **Performance**: Lookup O(1) via HashMap

**Exemplo**:
```java
@Service
public class AdapterRegistry {
    private final Map<String, PlatformAdapter> adapters = new HashMap<>();
    
    @Autowired
    public AdapterRegistry(
            @Qualifier("telegramAdapter") PlatformAdapter telegram,
            @Qualifier("whatsappAdapter") PlatformAdapter whatsapp) {
        adapters.put("TELEGRAM", telegram);
        adapters.put("WHATSAPP", whatsapp);
    }
    
    public PlatformAdapter getAdapter(Platform platform) {
        return adapters.get(platform.name());
    }
}
```

---

## 🔍 Detalhes de Implementação

### Platform Enum

**Arquivo**: `src/main/java/com/chat/model/Platform.java`

**Funcionalidades**:
1. **Validação de external_id**:
   - Telegram: numérico (`\d+`)
   - WhatsApp: E.164 format (`\+\d{10,15}`)
   - Instagram: username com @ (`@\w+`)

2. **Exemplos de ID**:
   - `Platform.TELEGRAM.getExampleExternalId()` → `"123456789"`
   - `Platform.WHATSAPP.getExampleExternalId()` → `"+5511987654321"`
   - `Platform.INSTAGRAM.getExampleExternalId()` → `"@john_doe"`

3. **Validação**:
   ```java
   boolean valid = Platform.WHATSAPP.isValidExternalId("+5511987654321"); // true
   boolean invalid = Platform.WHATSAPP.isValidExternalId("987654321");    // false (sem +)
   ```

### ConnectionResult

**Arquivo**: `src/main/java/com/chat/adapter/dto/ConnectionResult.java`

**Factory Methods**:
```java
// Sucesso
ConnectionResult result = ConnectionResult.success("Webhook registered at https://...");

// Falha
ConnectionResult result = ConnectionResult.failure("Invalid API token");
```

**Campos**:
- `success`: boolean (true/false)
- `message`: descrição human-readable
- `timestamp`: Instant para tracking

### SendResult

**Arquivo**: `src/main/java/com/chat/adapter/dto/SendResult.java`

**Factory Methods**:
```java
// Sucesso - armazena ID da mensagem na plataforma
SendResult result = SendResult.success("wamid.ABC123...");

// Falha - armazena mensagem de erro
SendResult result = SendResult.failure("Rate limit exceeded");
```

**Uso**:
```java
SendResult result = adapter.sendMessage("+5511987654321", "Hello");
if (result.isSuccess()) {
    String platformId = result.getPlatformMessageId(); // "wamid.ABC..."
    // Armazenar platformId para correlação com webhooks
} else {
    log.error("Send failed: {}", result.getErrorMessage());
}
```

### PlatformCredentials

**Arquivo**: `src/main/java/com/chat/adapter/dto/PlatformCredentials.java`

**Builder Pattern**:
```java
PlatformCredentials creds = new PlatformCredentials.Builder("TELEGRAM", "bot_token_123")
    .withApiSecret("secret_abc")
    .withWebhookUrl("https://api.example.com/webhooks/telegram")
    .build();
```

**Segurança**:
- ⚠️ **POC**: Tokens em application.properties (apenas para desenvolvimento)
- ✅ **Produção**: Usar environment variables + secret manager
- 🔒 **Logging**: toString() mascara tokens (mostra apenas primeiros 10 chars)

---

## 📋 Próximos Passos

### 1. Criar PlatformAdapter Interface
```java
public interface PlatformAdapter {
    ConnectionResult connect(PlatformCredentials credentials);
    SendResult sendMessage(String externalUserId, String messageText);
    SendResult sendFile(String externalUserId, FileMetadata file);
    String getPlatformName();
}
```

### 2. Implementar WhatsAppMockAdapter
- Log todas as operações com emojis para visibilidade
- Simular latência de 100-300ms
- Falhar em 5% das tentativas (aleatório)
- Gerar IDs no formato WhatsApp: `wamid.HBgN...`

### 3. Implementar InstagramMockAdapter  
- Similar ao WhatsApp mas com 10% taxa de falha
- Latência de 150-400ms (Instagram API é mais lenta)
- IDs no formato Instagram: `ig_mid.123456789...`

### 4. Implementar TelegramBotAdapter (Stub)
- Apenas estrutura básica (implementação real futura)
- Retornar UNIMPLEMENTED para todas as operações

### 5. Criar LinkedAccount Entity
- Mapear user_id → external_id por plataforma
- Índices MongoDB: (user_id, platform) unique, (platform, external_id) unique

### 6. Criar AdapterRegistry
- Injeção de adapters via Spring @Qualifier
- Lookup O(1) por plataforma
- Suporte a adicionar/remover adapters em runtime

### 7. Criar PlatformRoutingService
- Buscar LinkedAccounts do recipient
- Selecionar adapters via Registry
- Chamar sendMessage em paralelo (multi-platform)
- Coletar resultados e logar falhas

---

## 🧪 Plano de Testes

### Teste 1: Validação de Platform Enum
```java
@Test
void testTelegramIdValidation() {
    assertTrue(Platform.TELEGRAM.isValidExternalId("123456789"));
    assertFalse(Platform.TELEGRAM.isValidExternalId("abc123")); // não numérico
}

@Test
void testWhatsAppIdValidation() {
    assertTrue(Platform.WHATSAPP.isValidExternalId("+5511987654321"));
    assertFalse(Platform.WHATSAPP.isValidExternalId("5511987654321")); // sem +
}
```

### Teste 2: ConnectionResult
```java
@Test
void testSuccessResult() {
    ConnectionResult result = ConnectionResult.success("Connected");
    assertTrue(result.isSuccess());
    assertEquals("Connected", result.getMessage());
    assertNotNull(result.getTimestamp());
}
```

### Teste 3: WhatsAppMockAdapter
```bash
# Enviar 100 mensagens e verificar ~95% sucesso
for i in {1..100}; do
  curl -X POST http://localhost:8081/api/messages \
    -d '{"message":"Test '$i'","channels":["WHATSAPP"]}'
done

# Verificar logs
docker logs chat-api | grep "WhatsApp MOCK" | grep -c "✅"  # ~95
docker logs chat-api | grep "WhatsApp MOCK" | grep -c "❌"  # ~5
```

---

## 📖 Referências

- **Adapter Pattern**: Gang of Four Design Patterns
- **Result Object Pattern**: Error Handling in Distributed Systems (Martin Fowler)
- **Telegram Bot API**: https://core.telegram.org/bots/api
- **WhatsApp Business API**: https://developers.facebook.com/docs/whatsapp/cloud-api
- **Instagram Messaging**: https://developers.facebook.com/docs/messenger-platform

---

**Status**: ✅ Fase 1 completa (Platform + DTOs)  
**Próximo**: Implementar PlatformAdapter interface e mocks  
**Data**: 2024-11-24
