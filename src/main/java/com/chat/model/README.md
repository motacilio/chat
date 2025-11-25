# Domain Model Package

Este package contém as **entidades de domínio** do sistema de mensagens, mapeadas para coleções MongoDB.

## 📋 Índice

- [Visão Geral](#visão-geral)
- [Entidades Implementadas](#entidades-implementadas)
- [Padrão Entity em Spring Data MongoDB](#padrão-entity-em-spring-data-mongodb)
- [Índices MongoDB](#índices-mongodb)
- [Relacionamentos](#relacionamentos)
- [LinkedAccount: Multi-Platform Integration Pattern](#linkedaccount-multi-platform-integration-pattern)
- [Guia de Uso](#guia-de-uso)
- [Glossário](#glossário)

---

## Visão Geral

As entidades neste package seguem o padrão **Domain-Driven Design (DDD)**, onde cada entidade representa um conceito de negócio:

- **User**: Usuário da plataforma
- **Conversation**: Contexto de conversa (1:1 ou grupo)
- **Message**: Mensagem individual dentro de uma conversa
- **LinkedAccount**: Mapeamento entre usuário interno e contas externas (WhatsApp, Instagram, Telegram)

Todas as entidades são **persistidas em MongoDB** usando Spring Data MongoDB.

---

## Entidades Implementadas

### User (Entity 1)

**Propósito**: Usuário registrado na plataforma com identificador único.

**Campos**:
- `userId` (String/UUID): Identificador único do usuário
- `username` (String): Nome de exibição (único)
- `email` (String): Email de contato (único)
- `createdAt` (Instant): Timestamp de criação

**Coleção MongoDB**: `users`

**Índices**:
- `user_id`: Único - Lookup principal
- `username`: Único - Busca de usuários, menções
- `email`: Único - Login, recuperação de conta

---

### Conversation (Entity 2)

**Propósito**: Contexto de mensagens (conversa privada ou grupo).

**Campos**:
- `conversationId` (String/UUID): Identificador único da conversa
- `type` (ConversationType): `PRIVATE` ou `GROUP`
- `participants` (List<String>): Lista de `user_id` participantes
- `createdAt` (Instant): Timestamp de criação
- `lastMessageAt` (Instant): Timestamp da última mensagem (para ordenação)
- `lastMessagePreview` (String): Preview da última mensagem

**Coleção MongoDB**: `conversations`

**Índices**:
- `conversation_id`: Único - Lookup principal
- `participants`: Array index - Query "conversas do usuário X"

---

### Message (Entity 3)

**Propósito**: Mensagem individual dentro de uma conversa.

**Campos**:
- `messageId` (String/UUID): Identificador único da mensagem
- `conversationId` (String): Referência à conversa
- `senderId` (String): Referência ao usuário remetente
- `messageText` (String): Conteúdo da mensagem
- `timestamp` (Instant): Timestamp de envio
- `sequenceNumber` (Long): Número sequencial na conversa
- `stateHistory` (List<MessageStateTransition>): Histórico de transições de estado

**Coleção MongoDB**: `messages`

**Índices**:
- `message_id`: Único - Lookup principal
- `conversation_id, sequence_number`: Composto - Query histórico ordenado
- `conversation_id, timestamp`: Composto - Query timeline

---

### LinkedAccount (Entity 7)

**Propósito**: Mapeia usuário interno para contas em plataformas externas.

**Campos**:
- `userId` (String/UUID): Identificador do usuário interno
- `platform` (Platform): Plataforma externa (`WHATSAPP`, `INSTAGRAM`, `TELEGRAM`)
- `externalId` (String): Identificador específico da plataforma
- `linkedAt` (Instant): Timestamp de vinculação

**Coleção MongoDB**: `linked_accounts`

**Índices**:
- `user_id, platform`: Composto único - "Um usuário só pode ter uma conta Instagram"
- `platform, external_id`: Composto único - "Evita dois usuários reivindicarem mesma conta"

**Formatos de `externalId` por plataforma**:
- WhatsApp: Telefone E.164 (`+5511987654321`)
- Instagram: Username com @ (`@john_doe`)
- Telegram: User ID numérico (`123456789`)

---

## Padrão Entity em Spring Data MongoDB

### Anotações Principais

#### @Document
Marca a classe como entidade MongoDB mapeada para uma coleção.

```java
@Document(collection = "users")
public class User { ... }
```

Equivalente SQL: `CREATE TABLE users (...)`

---

#### @Id
Marca o campo como identificador único do documento (MongoDB `_id`).

```java
@Id
private String id; // MongoDB ObjectId
```

**Por que separar `id` e `userId`?**

- `id`: Identificador técnico do MongoDB (ObjectId gerado automaticamente)
- `userId`: Identificador de negócio (UUID, usado em toda a aplicação)

Separação permite MongoDB gerenciar IDs internos enquanto usamos UUIDs como chave de negócio.

---

#### @Indexed
Cria índice no campo para otimizar queries.

```java
@Indexed(unique = true)
private String userId;
```

Equivalente MongoDB:
```javascript
db.users.createIndex({ user_id: 1 }, { unique: true })
```

**Tipos de índice**:
- `unique = true`: Garante valores únicos (ex: email, username)
- `unique = false`: Permite duplicatas, apenas otimiza queries

---

#### @CompoundIndex
Cria índice composto em múltiplos campos.

```java
@CompoundIndexes({
    @CompoundIndex(
        name = "idx_user_platform",
        def = "{'user_id': 1, 'platform': 1}",
        unique = true
    )
})
```

**Quando usar índice composto?**

Quando queries filtram por múltiplos campos:
```java
repository.findByUserIdAndPlatform(userId, Platform.WHATSAPP);
```

MongoDB pode usar índice `(user_id, platform)` para lookup O(1).

---

### Lombok Annotations

As entidades usam **Lombok** para reduzir boilerplate:

```java
@Data                  // Gera getters, setters, equals, hashCode, toString
@NoArgsConstructor     // Construtor vazio (requerido por Spring Data)
@AllArgsConstructor    // Construtor com todos os campos
@Builder               // Padrão Builder para criação fluente
public class User { ... }
```

**Exemplo de uso do Builder**:
```java
User user = User.builder()
    .userId(UUID.randomUUID().toString())
    .username("john_doe")
    .email("john@example.com")
    .createdAt(Instant.now())
    .build();
```

**Sem Lombok, você precisaria escrever**:
```java
public class User {
    private String userId;
    private String username;
    
    // 50+ linhas de getters/setters/equals/hashCode/toString...
}
```

---

## Índices MongoDB

### Por que Índices?

MongoDB scans TODOS os documentos se não houver índice:

**Sem índice** (query lenta):
```javascript
db.users.find({ user_id: "550e8400..." })
// Scans: 1,000,000 documentos → ~500ms
```

**Com índice** (query rápida):
```javascript
db.users.createIndex({ user_id: 1 })
db.users.find({ user_id: "550e8400..." })
// Scans: 1 documento (B-tree lookup) → ~1ms
```

---

### Tipos de Índice

#### 1. Índice Único (Unique Index)
Garante valores únicos, impede duplicatas.

```java
@Indexed(unique = true)
private String email;
```

**Tentativa de inserir email duplicado**:
```java
userRepository.save(new User("user1", "john@example.com"));
userRepository.save(new User("user2", "john@example.com"));
// DuplicateKeyException: E11000 duplicate key error
```

---

#### 2. Índice Composto (Compound Index)
Otimiza queries com múltiplos filtros.

```java
@CompoundIndex(def = "{'user_id': 1, 'platform': 1}", unique = true)
```

**Ordem importa**:
- `(user_id, platform)`: Otimiza queries com ambos os campos OU apenas `user_id`
- Não otimiza query apenas com `platform` (ordem errada)

**Regra**: Coloque o campo mais seletivo PRIMEIRO.

---

#### 3. Índice de Array (Array Index)
Para queries em arrays embedded.

```java
private List<String> participants;
```

```javascript
db.conversations.createIndex({ participants: 1 })
```

**Query otimizada**:
```java
conversationRepository.findByParticipantsContaining(userId);
// MongoDB usa índice para encontrar conversas onde participants array contém userId
```

---

### Estratégia de Índices do Sistema

| Entidade       | Índice                      | Uso                                    |
|----------------|-----------------------------|----------------------------------------|
| User           | `user_id` (único)           | Lookup principal                       |
| User           | `username` (único)          | Busca de usuários                      |
| User           | `email` (único)             | Login                                  |
| Conversation   | `conversation_id` (único)   | Lookup principal                       |
| Conversation   | `participants`              | "Conversas do usuário X"               |
| Message        | `message_id` (único)        | Lookup principal                       |
| Message        | `(conversation_id, seq)`    | Histórico ordenado                     |
| LinkedAccount  | `(user_id, platform)`       | "Plataformas do usuário X"             |
| LinkedAccount  | `(platform, external_id)`   | "Usuário dono de conta externa Y"      |

---

## Relacionamentos

MongoDB é **NoSQL orientado a documentos**, não tem JOINs nativos como SQL.

### Estratégias de Relacionamento

#### 1. Referência (Foreign Key)
Armazena apenas o ID do documento relacionado.

```java
public class Message {
    private String conversationId; // Referência para Conversation
    private String senderId;       // Referência para User
}
```

**Vantagens**:
- Normalização: Atualizar User.username não requer atualizar todas as mensagens
- Economia de espaço: ID é pequeno (String UUID)

**Desvantagens**:
- Requer múltiplas queries para obter dados completos:
  ```java
  Message msg = messageRepository.findById(messageId);
  User sender = userRepository.findById(msg.getSenderId());
  ```

---

#### 2. Embedding (Documento Embutido)
Armazena documento completo dentro de outro.

```java
public class Message {
    private List<MessageStateTransition> stateHistory; // Embedded
}
```

**Vantagens**:
- Uma query retorna tudo: `findById(messageId)` retorna mensagem + histórico
- Atômico: Atualizar mensagem + adicionar estado em única operação

**Desvantagens**:
- Duplicação: Se User fosse embedded em Message, atualizar username requer atualizar todas mensagens

---

#### 3. Array de Referências
Armazena lista de IDs relacionados.

```java
public class Conversation {
    private List<String> participants; // Array de user_id
}
```

**Quando usar**:
- Relacionamento many-to-many (ex: usuários ↔ conversas)
- Array tem tamanho limitado (conversas têm max ~100 participantes)

**Query**:
```java
conversationRepository.findByParticipantsContaining(userId);
// MongoDB: db.conversations.find({ participants: "550e8400..." })
```

---

### Mapeamento de Relacionamentos neste Sistema

```
┌──────────────┐
│     User     │
└──────┬───────┘
       │ 1
       │
       │ N
┌──────▼────────────┐
│  LinkedAccount    │  (Referência: user_id)
│                   │
│ - user_id         │
│ - platform        │
│ - external_id     │
└───────────────────┘

┌──────────────┐         ┌──────────────────┐
│     User     │◄────────┤   Conversation   │
│              │  N : M  │                  │
│ - user_id    │         │ - participants[] │ (Array de referências)
└──────┬───────┘         └────────┬─────────┘
       │ 1                        │ 1
       │                          │
       │ N                        │ N
┌──────▼─────────────────────┬────▼─────┐
│         Message            │          │
│                            │          │ (Referências: conversation_id, sender_id)
│ - conversation_id (FK)     │          │
│ - sender_id (FK)           │          │
│ - state_history[] (embed)  │          │ (Embedding: MessageStateTransition)
└────────────────────────────┴──────────┘
```

---

## LinkedAccount: Multi-Platform Integration Pattern

### Problema

Sistema precisa rotear mensagens para plataformas externas (WhatsApp, Instagram, Telegram).

**Desafios**:
1. Cada plataforma tem formato de identificador diferente
2. Um usuário pode ter múltiplas contas externas
3. Webhooks de entrada precisam mapear conta externa → usuário interno

---

### Solução: LinkedAccount Entity

**Design Pattern**: **Adapter Pattern** + **Registry Pattern**

LinkedAccount atua como **tabela de tradução**:

```
Internal User → External Accounts
550e8400...   → [TELEGRAM:123456789, WHATSAPP:+5511987654321]
```

---

### Fluxo de Roteamento (Outbound)

**Cenário**: Usuário envia mensagem para destinatário via Telegram e WhatsApp.

```java
// 1. Buscar contas externas do destinatário
List<LinkedAccount> accounts = linkedAccountRepository.findByUserId(recipientId);
// Result: [LinkedAccount(TELEGRAM, "123456789"), LinkedAccount(WHATSAPP, "+5511...")]

// 2. Para cada plataforma, obter adapter correspondente
for (LinkedAccount account : accounts) {
    PlatformAdapter adapter = adapterRegistry.getAdapter(account.getPlatform());
    
    // 3. Enviar mensagem via adapter
    SendResult result = adapter.sendMessage(account.getExternalId(), messageText);
    
    if (result.isSuccess()) {
        log.info("Message sent via {} to {}", account.getPlatform(), account.getExternalId());
    }
}
```

**Por que índice composto `(user_id, platform)`?**

Query acima usa `findByUserId(recipientId)` → MongoDB scans apenas por `user_id`.
Índice composto `(user_id, platform)` permite lookup eficiente porque `user_id` é primeiro campo.

---

### Fluxo de Webhook (Inbound)

**Cenário**: Telegram entrega mensagem de usuário externo via webhook.

```java
// Webhook payload: { "from": { "id": "123456789" }, "text": "Hello" }
String telegramUserId = webhookPayload.getFrom().getId();

// 1. Reverse lookup: Qual usuário interno possui esta conta Telegram?
Optional<LinkedAccount> account = linkedAccountRepository.findByPlatformAndExternalId(
    Platform.TELEGRAM,
    telegramUserId
);

if (account.isEmpty()) {
    log.warn("Unknown Telegram user: {}", telegramUserId);
    return; // Ignorar mensagem de conta não vinculada
}

// 2. Rotear para usuário interno
String recipientUserId = account.get().getUserId();
messageService.deliverInboundMessage(recipientUserId, messageText);
```

**Por que índice composto `(platform, external_id)`?**

Query acima usa `findByPlatformAndExternalId(TELEGRAM, "123456789")`.
Índice permite lookup O(1) sem escanear todos os documentos.

---

### Validação de external_id

**Importante**: `LinkedAccount` NÃO valida formato de `external_id`.

**Quem valida?**:
1. **API de Link de Conta** (futuro): Valida antes de persistir
2. **PlatformAdapter**: Valida ao conectar/enviar mensagem

**Formatos por plataforma**:

| Plataforma | Formato                 | Regex                            | Exemplo            |
|------------|-------------------------|----------------------------------|--------------------|
| WhatsApp   | E.164 phone             | `^\+[1-9]\d{1,14}$`              | `+5511987654321`   |
| Instagram  | Username com @          | `^@[a-zA-Z0-9._]{1,30}$`         | `@john_doe`        |
| Telegram   | User ID numérico        | `^\d+$`                          | `123456789`        |

**Por que não validar na entidade?**

- **Flexibilidade**: Futuras plataformas podem ter formatos diferentes
- **Separação de responsabilidades**: Entidade é modelo de dados, adapters são lógica de negócio
- **Testabilidade**: Mocks podem usar IDs arbitrários sem quebrar validação

---

### Índices Únicos e Segurança

#### Índice 1: `(user_id, platform)` UNIQUE

**Garante**: Um usuário só pode vincular UMA conta por plataforma.

**Sem este índice**:
```java
// Usuário malicioso vincula 10 contas Instagram
save(LinkedAccount(userId, INSTAGRAM, "@account1"));
save(LinkedAccount(userId, INSTAGRAM, "@account2"));
// ...
```

**Com índice único**:
```java
save(LinkedAccount(userId, INSTAGRAM, "@account1")); // ✅ OK
save(LinkedAccount(userId, INSTAGRAM, "@account2")); // ❌ DuplicateKeyException
```

---

#### Índice 2: `(platform, external_id)` UNIQUE

**Garante**: Uma conta externa não pode ser reivindicada por múltiplos usuários.

**Sem este índice**:
```java
// Dois usuários reivindicam mesmo número WhatsApp
save(LinkedAccount("user1", WHATSAPP, "+5511987654321"));
save(LinkedAccount("user2", WHATSAPP, "+5511987654321")); // ⚠️ Hijacking!
```

**Com índice único**:
```java
save(LinkedAccount("user1", WHATSAPP, "+5511987654321")); // ✅ OK
save(LinkedAccount("user2", WHATSAPP, "+5511987654321")); // ❌ DuplicateKeyException
```

**Defesa em Profundidade**:
1. Índice único (primeira linha de defesa)
2. Verificação OTP via plataforma (usuário confirma posse)
3. Rate limiting em API de link

---

## Guia de Uso

### Criar Nova Entidade

```java
// Usar factory method (inclui timestamp automático)
User user = User.create(
    UUID.randomUUID().toString(),
    "john_doe",
    "john@example.com"
);

userRepository.save(user);
```

---

### Buscar por ID de Negócio

```java
// Buscar por user_id (não por MongoDB ObjectId)
Optional<User> user = userRepository.findByUserId("550e8400-e29b-41d4-a716-446655440001");

if (user.isPresent()) {
    System.out.println("Found user: " + user.get().getUsername());
}
```

---

### Vincular Conta Externa

```java
// 1. Validar se usuário já tem esta plataforma
if (linkedAccountRepository.existsByUserIdAndPlatform(userId, Platform.WHATSAPP)) {
    throw new AlreadyLinkedException("WhatsApp already linked");
}

// 2. Validar se conta externa já está reivindicada
String phoneNumber = "+5511987654321";
if (linkedAccountRepository.existsByPlatformAndExternalId(Platform.WHATSAPP, phoneNumber)) {
    throw new AccountClaimedException("This phone number is already linked");
}

// 3. Testar conexão via adapter (enviar código OTP)
PlatformAdapter adapter = adapterRegistry.getAdapter(Platform.WHATSAPP);
ConnectionResult result = adapter.connect(new WhatsAppCredentials(phoneNumber, apiKey));
if (!result.isSuccess()) {
    throw new PlatformConnectionException("WhatsApp connection failed");
}

// 4. Persistir vínculo
LinkedAccount account = LinkedAccount.create(userId, Platform.WHATSAPP, phoneNumber);
linkedAccountRepository.save(account);
```

---

### Buscar Plataformas de Usuário

```java
// Listar todas as plataformas vinculadas
List<LinkedAccount> accounts = linkedAccountRepository.findByUserId(userId);

for (LinkedAccount account : accounts) {
    System.out.println(account.getPlatform() + ": " + account.getExternalId());
}

// Output:
// TELEGRAM: 123456789
// WHATSAPP: +5511987654321
```

---

### Reverse Lookup (Webhook)

```java
// Telegram webhook recebe mensagem de user_id "123456789"
Optional<LinkedAccount> account = linkedAccountRepository.findByPlatformAndExternalId(
    Platform.TELEGRAM,
    "123456789"
);

if (account.isEmpty()) {
    log.warn("Unknown Telegram user");
    return;
}

String internalUserId = account.get().getUserId();
// Processar mensagem para usuário interno
```

---

## Glossário

### Domain-Driven Design (DDD)
Abordagem de modelagem onde código reflete conceitos de negócio (User, Conversation, Message).

### Aggregate Root
Entidade principal que gerencia acesso a entidades relacionadas (ex: Conversation gerencia Messages).

### NoSQL
"Not Only SQL" - bancos orientados a documentos (MongoDB), key-value (Redis), etc.

### Document
Unidade de armazenamento no MongoDB, equivalente a "row" em SQL. Formato JSON/BSON.

### Collection
Grupo de documentos no MongoDB, equivalente a "table" em SQL.

### Index
Estrutura de dados (B-tree) que otimiza queries, permitindo lookup O(log N) em vez de O(N).

### Compound Index
Índice em múltiplos campos, ordem importa: `(a, b)` otimiza queries com `a` ou `a+b`, mas não apenas `b`.

### Unique Index
Índice que rejeita valores duplicados, garantindo unicidade (ex: email, username).

### Embedding
Armazenar documento completo dentro de outro (ex: MessageStateTransition dentro de Message).

### Referencing
Armazenar apenas ID do documento relacionado (ex: Message.senderId referencia User.userId).

### Adapter Pattern
Padrão que unifica interfaces heterogêneas. LinkedAccount + PlatformAdapter permitem rotear mensagens para WhatsApp/Instagram/Telegram via interface comum.

### E.164
Formato internacional de telefone: `+` + código país + número. Ex: `+5511987654321` (Brasil).

### UUID (Universally Unique Identifier)
Identificador único de 128 bits, gerado sem coordenação central. Ex: `550e8400-e29b-41d4-a716-446655440001`.

### Lombok
Biblioteca Java que reduz boilerplate gerando getters/setters/constructors via anotações.

### Spring Data MongoDB
Framework que converte interfaces de repository em queries MongoDB automaticamente.

### Query Derivation
Spring Data analisa nome de método (ex: `findByUserId`) e gera query MongoDB automaticamente.

---

## Próximos Passos

1. **Implementar PlatformRoutingService** (T077): Lógica de roteamento multi-plataforma
2. **Circuit Breaker**: Resilience4j para lidar com falhas de adapters
3. **Testes de Integração**: Validar índices MongoDB, constraints únicos
4. **API de Link de Conta**: Endpoint para usuários vincularem plataformas externas

---

**Documentação Relacionada**:
- `src/main/java/com/chat/adapter/README.md`: Padrão Adapter e Registry
- `src/main/java/com/chat/repository/`: Repositories Spring Data
- `specs/001-ubiquitous-messaging-platform/data-model.md`: Especificação completa de entidades
