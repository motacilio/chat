# Platform Adapters - Guia Educacional

## 📚 O que são Adapters?

**Adapter Pattern** é um padrão de design que permite que interfaces incompatíveis trabalhem juntas. No nosso caso, usamos para **integrar plataformas externas** (WhatsApp, Instagram, Telegram) sem acoplar a lógica de negócio aos detalhes de cada API.

## 🎯 Por que Adapters?

### Problema Sem Adapters:
```java
// ❌ RUIM: Lógica de roteamento acoplada a APIs específicas
public void routeMessage(String platform, String recipient, String text) {
    if (platform.equals("whatsapp")) {
        // Código específico do WhatsApp Business API
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://graph.facebook.com/v18.0/.../messages"))
            .header("Authorization", "Bearer " + whatsappToken)
            .POST(...)
            .build();
        // ... 50 linhas de código HTTP
    } else if (platform.equals("instagram")) {
        // Código específico do Instagram Graph API
        // ... mais 50 linhas diferentes
    } else if (platform.equals("telegram")) {
        // Código específico do Telegram Bot API
        // ... mais 50 linhas diferentes
    }
}
```

**Problemas**:
- 🔴 150+ linhas de código numa única função
- 🔴 Difícil de testar (precisa mockar APIs externas)
- 🔴 Difícil de adicionar nova plataforma (mexe em código existente)
- 🔴 Quebra princípio Open/Closed (aberto para extensão, fechado para modificação)

### Solução Com Adapters:
```java
// ✅ BOM: Lógica de roteamento desacoplada de APIs específicas
public void routeMessage(Platform platform, String recipient, String text) {
    PlatformAdapter adapter = adapterRegistry.getAdapter(platform);
    adapter.connect(credentials);
    SendResult result = adapter.sendMessage(recipient, text);
    // 3 linhas de código! Detalhes encapsulados no adapter
}
```

**Benefícios**:
- ✅ Código limpo e focado (3 linhas vs 150+)
- ✅ Fácil de testar (mock do adapter)
- ✅ Fácil de adicionar plataforma (cria novo adapter, zero mudanças no routing)
- ✅ Respeita princípio Open/Closed

## 🏗️ Arquitetura dos Adapters

```
┌─────────────────────────────────────────────────────────────────┐
│                    PlatformAdapter (interface)                  │
│  - connect(credentials): ConnectionResult                       │
│  - sendMessage(externalId, text): SendResult                    │
│  - sendFile(externalId, url, filename): SendResult              │
│  - getPlatform(): Platform                                      │
└────────────┬────────────────────┬──────────────────┬────────────┘
             │                    │                  │
             │                    │                  │
             ▼                    ▼                  ▼
    ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
    │WhatsAppMockAdapter│  │InstagramMockAdapter│  │TelegramBotStub │
    │                 │  │                 │  │                 │
    │ - 95% success   │  │ - 90% success   │  │ - not_implemented│
    │ - 100-300ms     │  │ - 150-400ms     │  │ - placeholder   │
    │ - E.164 valid.  │  │ - @username val.│  │                 │
    │ - wamid.UUID    │  │ - mid.UUID      │  │                 │
    └─────────────────┘  └─────────────────┘  └─────────────────┘
             │                    │                  │
             └────────────────────┴──────────────────┘
                                  │
                                  │ registrados em
                                  ▼
                         ┌─────────────────┐
                         │ AdapterRegistry │
                         │  (EnumMap)      │
                         │                 │
                         │ Platform.WHATSAPP → WhatsAppMockAdapter  │
                         │ Platform.INSTAGRAM → InstagramMockAdapter │
                         │ Platform.TELEGRAM → TelegramBotStub      │
                         └─────────────────┘
```

## 🔧 Como Spring Conecta Tudo?

### 1. Adapters são Beans do Spring

Cada adapter tem `@Component("nomeDoBean")`:

```java
@Component("whatsappAdapter")  // ← Nome do bean = "whatsappAdapter"
public class WhatsAppMockAdapter implements PlatformAdapter {
    // ...
}

@Component("instagramAdapter")  // ← Nome do bean = "instagramAdapter"
public class InstagramMockAdapter implements PlatformAdapter {
    // ...
}

@Component("telegramAdapter")  // ← Nome do bean = "telegramAdapter"
public class TelegramBotAdapterStub implements PlatformAdapter {
    // ...
}
```

**O que Spring faz?**
- No startup, Spring **escaneia** todo o código procurando `@Component`
- Encontra os 3 adapters e **cria instâncias** (singletons por padrão)
- Armazena no **ApplicationContext** (container de beans do Spring)

### 2. AdapterRegistry Usa @Qualifier para Injeção

```java
@Service
public class AdapterRegistry {
    
    public AdapterRegistry(
            @Qualifier("whatsappAdapter") PlatformAdapter whatsappAdapter,    // ← Match pelo nome!
            @Qualifier("instagramAdapter") PlatformAdapter instagramAdapter,  // ← Match pelo nome!
            @Qualifier("telegramAdapter") PlatformAdapter telegramAdapter) {  // ← Match pelo nome!
        
        this.adapterMap = new EnumMap<>(Platform.class);
        adapterMap.put(Platform.WHATSAPP, whatsappAdapter);
        adapterMap.put(Platform.INSTAGRAM, instagramAdapter);
        adapterMap.put(Platform.TELEGRAM, telegramAdapter);
    }
}
```

**O que @Qualifier faz?**
- Sem `@Qualifier`: Spring vê 3 beans do tipo `PlatformAdapter` e **não sabe qual injetar** → erro!
- Com `@Qualifier("whatsappAdapter")`: Spring procura bean com nome **exatamente** "whatsappAdapter"
- Match encontrado → injeta `WhatsAppMockAdapter` no parâmetro

### 3. Lookup via Registry

```java
// Em PlatformRoutingService (futuro - T077)
public class PlatformRoutingService {
    private final AdapterRegistry adapterRegistry;  // ← Spring injeta automaticamente
    
    public void route(Platform platform, String externalId, String text) {
        PlatformAdapter adapter = adapterRegistry.getAdapter(platform);
        //                                         ↑
        //                                  Lookup O(1) no EnumMap!
        
        SendResult result = adapter.sendMessage(externalId, text);
    }
}
```

## 📖 Glossário de Conceitos

### Adapter Pattern
Padrão de design que **converte interface de uma classe** em outra interface esperada pelo cliente.
- **Analogia**: Adaptador de tomada (110V → 220V)
- **No nosso caso**: Converte diferentes APIs (WhatsApp, Instagram) em interface única (`PlatformAdapter`)

### Registry Pattern
Padrão de design que **centraliza lookup** de objetos por chave.
- **Analogia**: Catálogo telefônico (nome → telefone)
- **No nosso caso**: Platform enum → PlatformAdapter (`EnumMap`)

### Dependency Injection (DI)
Spring **fornece dependências** para uma classe em vez de a classe criá-las.
- **Sem DI**: `PlatformAdapter adapter = new WhatsAppMockAdapter()` (acoplamento forte)
- **Com DI**: Spring cria e injeta automaticamente via construtor

### @Qualifier
Anotação do Spring para **desambiguar** quando há múltiplos beans do mesmo tipo.
- **Problema**: 3 beans implementam `PlatformAdapter`
- **Solução**: `@Qualifier("whatsappAdapter")` especifica qual queremos

### EnumMap
Implementação de `Map` **otimizada** para chaves do tipo enum.
- **Vantagem**: Usa array internamente (lookup O(1) muito rápido)
- **Tipo-safe**: Compilador garante que chaves são valores válidos do enum

## 🧪 Testando Adapters

### Teste de Mock (WhatsApp)

```java
@Test
public void shouldSimulate95PercentSuccessRate() {
    WhatsAppMockAdapter adapter = new WhatsAppMockAdapter();
    adapter.connect(PlatformCredentials.builder().build());
    
    int successCount = 0;
    int totalAttempts = 1000;
    
    for (int i = 0; i < totalAttempts; i++) {
        SendResult result = adapter.sendMessage("+5511987654321", "Test message");
        if (result.isSuccess()) {
            successCount++;
        }
    }
    
    double successRate = (double) successCount / totalAttempts;
    
    // Deve estar próximo de 95% (tolerance: ±3%)
    assertEquals(0.95, successRate, 0.03);
}
```

### Teste de Registry

```java
@Test
public void shouldRetrieveCorrectAdapterForPlatform() {
    PlatformAdapter mockWhatsApp = mock(PlatformAdapter.class);
    when(mockWhatsApp.getPlatform()).thenReturn(Platform.WHATSAPP);
    
    AdapterRegistry registry = new AdapterRegistry(mockWhatsApp, mockInstagram, mockTelegram);
    
    PlatformAdapter retrieved = registry.getAdapter(Platform.WHATSAPP);
    
    assertEquals(Platform.WHATSAPP, retrieved.getPlatform());
}
```

## 🎓 Conceitos de Sistemas Distribuídos

### 1. Heterogeneous Reliability (SLAs Diferentes)
- **WhatsApp**: 95% success (alta confiabilidade)
- **Instagram**: 90% success (média confiabilidade)
- **Lição**: Sistemas distribuídos dependem de serviços com **SLAs variados**
- **Estratégia**: Retry logic, circuit breakers, fallbacks

### 2. Latency Variability (Latência Variável)
- **WhatsApp**: 100-300ms
- **Instagram**: 150-400ms (mais lento)
- **Lição**: Rede introduz **latência não-determinística**
- **Estratégia**: Timeouts, async processing, user feedback

### 3. Partial Failures (Falhas Parciais)
- Um adapter pode falhar enquanto outros funcionam
- **Exemplo**: Instagram timeout, mas WhatsApp OK
- **Lição**: Fail independently, track per-platform results
- **Estratégia**: Return `{whatsapp: success, instagram: failed}` (per FR-036)

### 4. External ID Validation
- Cada plataforma tem formato próprio:
  - WhatsApp: E.164 phone (`+5511987654321`)
  - Instagram: @username (`@john_doe`)
  - Telegram: numeric user_id (`123456789`)
- **Lição**: Validação **no adapter** (responsabilidade delegada)
- **Benefício**: Routing service não precisa conhecer formatos específicos

## 📝 Próximos Passos

- [ ] **LinkedAccount entity** (T070) - Mapeia users para contas externas
- [ ] **LinkedAccountRepository** (T071) - Queries MongoDB
- [ ] **PlatformRoutingService** (T077) - Lógica de roteamento + circuit breaker
- [ ] **Testes de integração** - Validar adapters com Testcontainers

## 🔗 Referências

- [Adapter Pattern - Refactoring Guru](https://refactoring.guru/design-patterns/adapter)
- [Spring Dependency Injection - Baeldung](https://www.baeldung.com/spring-dependency-injection)
- [Spring @Qualifier - Baeldung](https://www.baeldung.com/spring-qualifier-annotation)
- [EnumMap JavaDoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumMap.html)
