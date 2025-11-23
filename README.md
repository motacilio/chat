# 💬 Chat API - Mensageria em Tempo Real

> API de mensageria com gRPC, Apache Kafka e MongoDB

[![Java](https://img.shields.io/badge/Java-17-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![gRPC](https://img.shields.io/badge/gRPC-1.64.0-blue)](https://grpc.io/)
[![Kafka](https://img.shields.io/badge/Kafka-7.5.0-black)](https://kafka.apache.org/)
[![MongoDB](https://img.shields.io/badge/MongoDB-7.0-green)](https://www.mongodb.com/)

---

## 📋 Índice

1. [Inicialização Rápida](#-inicialização-rápida)
2. [Endpoints Disponíveis](#-endpoints-disponíveis)
3. [Exemplos de Uso](#-exemplos-de-uso)
4. [Instruções de Execução](#-instruções-de-execução)

---

## 🚀 Inicialização Rápida

### Script de Inicialização Automática (Recomendado)

```powershell
# Executar script que inicia TUDO automaticamente
.\start.ps1

# Opções disponíveis:
.\start.ps1 -SkipBuild      # Não recompilar (usa JAR existente)
.\start.ps1 -Rebuild        # Recriar containers Docker
```

**O que o script faz:**
1. ✅ Valida pré-requisitos (Docker, Java, Maven)
2. ✅ Libera portas ocupadas (com confirmação)
3. ✅ Inicia Docker Compose (MongoDB + Kafka + Zookeeper + Kafka UI)
4. ✅ Aguarda serviços estarem prontos
5. ✅ Compila aplicação Maven
6. ✅ Inicia Spring Boot
7. ✅ Valida health checks
8. ✅ Exibe resumo com URLs e comandos úteis

### Parar Serviços

```powershell
# Parar tudo (preserva dados)
.\stop.ps1

# Parar tudo e remover dados
.\stop.ps1 -RemoveData
```

---

### Inicialização Manual (Alternativa)

```powershell
# 1. Iniciar infraestrutura (MongoDB + Kafka)
docker-compose -f docker-compose.dev.yml up -d

# 2. Compilar aplicação
mvn clean package -DskipTests

# 3. Executar Spring Boot
java -jar target/meu-projeto-chat-1.0.0-SNAPSHOT.jar
```

**Serviços iniciados:**
- **MongoDB**: `localhost:27017` (usuário: `admin`, senha: `password`)
- **Kafka**: `localhost:9092`
- **Zookeeper**: `localhost:2181`
- **Kafka UI**: http://localhost:8080
- **gRPC API**: `localhost:9090`
- **Actuator**: http://localhost:8081/actuator/health

---

## 📊 Endpoints Disponíveis

### gRPC API (porta 9090)

| Serviço | Método | Descrição |
|---------|--------|-----------|
| `ChatService` | `SendMessage` | Envia nova mensagem |
| `ChatService` | `GetMessageStatus` | Consulta status de mensagem |
| `ChatService` | `MarkMessageAsRead` | Marca mensagem como lida |
| `ChatService` | `StreamMessages` | Stream de mensagens em tempo real |
| `ConversationService` | `CreateConversation` | Cria conversa privada 1:1 |
| `ConversationService` | `ListConversations` | Lista conversas do usuário |
| `ConversationService` | `GetConversationHistory` | Histórico de mensagens |

### HTTP Actuator (porta 8081)

| Endpoint | Descrição |
|----------|-----------|
| `/actuator/health` | Health check da aplicação |
| `/actuator/metrics` | Métricas do sistema |
| `/actuator/info` | Informações da build |

---

## 💡 Exemplos de Uso

### ⚠️ Importante: Formato de UUIDs

Todos os IDs devem usar formato **RFC 4122**: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`

✅ **CORRETO**: `a1a1a1a1-1111-1111-1111-111111111111`  
❌ **ERRADO**: `alice`, `test-123`, `msg-001`

**Gerar UUID no PowerShell:**
```powershell
[guid]::NewGuid().ToString()
```

---

### 1️⃣ Criar Conversa

**Método**: `ConversationService/CreateConversation`

```json
{
  "type": "PRIVATE",
  "participant_ids": [
    "a1a1a1a1-1111-1111-1111-111111111111",
    "b2b2b2b2-2222-2222-2222-222222222222"
  ]
}
```

**Response:**
```json
{
  "conversationId": "3184a104-6171-45d5-b541-7ee8f36d1062",
  "type": "PRIVATE",
  "participants": [
    {"userId": "a1a1a1a1-1111-1111-1111-111111111111"},
    {"userId": "b2b2b2b2-2222-2222-2222-222222222222"}
  ],
  "createdAt": "2025-11-23T20:00:00.000Z"
}
```

---

### 2️⃣ Enviar Mensagem

**Método**: `ChatService/SendMessage`

```json
{
  "conversation_id": "3184a104-6171-45d5-b541-7ee8f36d1062",
  "sender_id": "a1a1a1a1-1111-1111-1111-111111111111",
  "recipient_id": "b2b2b2b2-2222-2222-2222-222222222222",
  "message_id": "00000001-0000-0000-0000-000000000001",
  "message_text": "Olá! Esta é minha primeira mensagem."
}
```

**Response:**
```json
{
  "messageId": "00000001-0000-0000-0000-000000000001",
  "timestamp": "2025-11-23T20:01:00.000Z",
  "sequenceNumber": "1"
}
```

---

### 3️⃣ Consultar Status

**Método**: `ChatService/GetMessageStatus`

```json
{
  "message_id": "00000001-0000-0000-0000-000000000001",
  "requester_id": "a1a1a1a1-1111-1111-1111-111111111111"
}
```

**Response:**
```json
{
  "messageId": "00000001-0000-0000-0000-000000000001",
  "currentStatus": "SENT",
  "stateHistory": [
    {
      "status": "SENT",
      "timestamp": "2025-11-23T20:01:00.000Z"
    }
  ]
}
```

---

### 4️⃣ Marcar Como Lida

**Método**: `ChatService/MarkMessageAsRead`

```json
{
  "message_id": "00000001-0000-0000-0000-000000000001",
  "reader_id": "b2b2b2b2-2222-2222-2222-222222222222"
}
```

**Response:**
```json
{
  "messageId": "00000001-0000-0000-0000-000000000001",
  "newStatus": "READ",
  "timestamp": "2025-11-23T20:02:00.000Z"
}
```

---

### 5️⃣ Histórico de Conversa

**Método**: `ConversationService/GetConversationHistory`

```json
{
  "conversation_id": "3184a104-6171-45d5-b541-7ee8f36d1062",
  "requester_id": "a1a1a1a1-1111-1111-1111-111111111111",
  "limit": 10
}
```

**Response:**
```json
{
  "messages": [
    {
      "messageId": "00000001-0000-0000-0000-000000000001",
      "senderId": "a1a1a1a1-1111-1111-1111-111111111111",
      "messageText": "Olá! Esta é minha primeira mensagem.",
      "timestamp": "2025-11-23T20:01:00.000Z",
      "sequenceNumber": "1",
      "stateHistory": [
        {
          "status": "SENT",
          "timestamp": "2025-11-23T20:01:00.000Z"
        },
        {
          "status": "READ",
          "timestamp": "2025-11-23T20:02:00.000Z",
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

### 6️⃣ Listar Conversas

**Método**: `ConversationService/ListConversations`

```json
{
  "user_id": "a1a1a1a1-1111-1111-1111-111111111111",
  "limit": 20
}
```

**Response:**
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
      "lastMessageAt": "2025-11-23T20:01:00.000Z",
      "createdAt": "2025-11-23T20:00:00.000Z"
    }
  ],
  "pagination": {
    "totalCount": "1",
    "hasMore": false
  }
}
```

---

## 🛠️ Instruções de Execução

### Pré-requisitos

- Java 17+
- Maven 3.6+
- Docker & Docker Compose

```powershell
# Verificar versões instaladas
java -version
mvn -version
docker --version
```

---

### Passo 1: Iniciar Infraestrutura

```powershell
# Iniciar MongoDB + Kafka via Docker Compose
docker-compose -f docker-compose.dev.yml up -d

# Verificar containers
docker ps
```

**Serviços esperados:**
```
CONTAINER ID   IMAGE                            STATUS    PORTS
abc123def456   mongo:7.0                        Up        0.0.0.0:27017->27017/tcp
def456ghi789   confluentinc/cp-kafka:7.5.0      Up        0.0.0.0:9092->9092/tcp
ghi789jkl012   confluentinc/cp-zookeeper:7.5.0  Up        2181/tcp
jkl012mno345   provectuslabs/kafka-ui:latest    Up        0.0.0.0:8080->8080/tcp
```

---

### Passo 2: Compilar Aplicação

```powershell
# Compilar código Java + gerar classes Protobuf
mvn clean package -DskipTests
```

**Arquivos gerados:**
- JAR: `target/meu-projeto-chat-1.0.0-SNAPSHOT.jar`
- Classes Protobuf: `target/generated-sources/protobuf/java/`

---

### Passo 3: Executar Spring Boot

```powershell
# Iniciar aplicação
java -jar target/meu-projeto-chat-1.0.0-SNAPSHOT.jar
```

**Aguardar mensagens de confirmação:**
```
✓ Started ChatApiApplication in X seconds
✓ gRPC Server started, listening on port 9090
✓ partitions assigned: [message-events-0, ...]
```

---

### Passo 4: Testar Endpoints

#### Opção 1: Postman (Recomendado)

1. Postman → **New** → **gRPC Request**
2. URL: `localhost:9090` (desmarcar **Use TLS**)
3. **Select a method** → `chat.ChatService/SendMessage`
4. Cole o payload de exemplo (ver seção [Exemplos de Uso](#-exemplos-de-uso))
5. **Invoke**

**Guia completo**: [POSTMAN-EXAMPLES.md](./POSTMAN-EXAMPLES.md)

#### Opção 2: grpcurl (CLI)

```powershell
# Instalar grpcurl
scoop install grpcurl

# Testar endpoint
grpcurl -plaintext -d '{
  "type": "PRIVATE",
  "participant_ids": [
    "a1a1a1a1-1111-1111-1111-111111111111",
    "b2b2b2b2-2222-2222-2222-222222222222"
  ]
}' localhost:9090 conversation.ConversationService/CreateConversation
```

---

### Passo 5: Monitorar Infraestrutura

#### Kafka UI
http://localhost:8080
- Visualizar topics: `message-events`, `state-update-events`
- Monitorar consumer lag

#### MongoDB
```powershell
# Conectar ao MongoDB
docker exec -it mongodb-dev mongosh -u admin -p password --authenticationDatabase admin

# Consultar mensagens
use chat
db.messages.find().pretty()

# Consultar conversas
db.conversations.find().pretty()
```

#### Application Health
http://localhost:8081/actuator/health

**Response esperado:**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "mongo": {"status": "UP"}
  }
}
```

---

### Troubleshooting

#### Aplicação não inicia

```powershell
# Verificar portas ocupadas
netstat -ano | findstr ":9090"
netstat -ano | findstr ":8081"

# Matar processo se necessário
taskkill /PID <PID> /F
```

#### Containers não iniciam

```powershell
# Ver status de todos os containers
docker ps -a

# Ver logs
docker logs mongodb-dev --tail 50
docker logs kafka-dev --tail 50

# Recriar containers (apaga dados!)
docker-compose -f docker-compose.dev.yml down -v
docker-compose -f docker-compose.dev.yml up -d
```

#### Kafka consumer não conecta

```powershell
# Verificar topics criados
docker exec kafka-dev kafka-topics --list --bootstrap-server localhost:9092

# Criar topic manualmente se necessário
docker exec kafka-dev kafka-topics --create \
  --topic message-events \
  --partitions 10 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092
```

---

## 📁 Estrutura do Projeto

```
chat/
├── src/main/
│   ├── java/com/chat/
│   │   ├── grpc/              # Implementação gRPC
│   │   ├── service/           # Lógica de negócio
│   │   ├── model/             # Entidades
│   │   ├── repository/        # MongoDB repositories
│   │   └── worker/            # Kafka consumers
│   ├── proto/                 # Protobuf definitions
│   └── resources/
│       └── application.properties
├── docker-compose.dev.yml     # Infraestrutura Docker
├── pom.xml                    # Maven dependencies
├── POSTMAN-EXAMPLES.md        # Exemplos Postman
└── README.md
```

---

## 🔧 Configuração

### application.properties

```properties
# gRPC Server
grpc.server.port=9090

# MongoDB
spring.data.mongodb.uri=mongodb://admin:password@localhost:27017/chat?authSource=admin

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=message-consumer-group
spring.kafka.consumer.enable-auto-commit=false

# Actuator
management.server.port=8081
management.endpoints.web.exposure.include=health,info,metrics
```

### docker-compose.dev.yml

```yaml
services:
  mongodb-dev:
    image: mongo:7.0
    container_name: mongodb-dev
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: password

  kafka-dev:
    image: confluentinc/cp-kafka:7.5.0
    container_name: kafka-dev
    ports:
      - "9092:9092"
    environment:
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
      KAFKA_NUM_PARTITIONS: 10

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: kafka-ui-dev
    ports:
      - "8080:8080"
```

---

## 📚 Documentação Adicional

- **[POSTMAN-EXAMPLES.md](./POSTMAN-EXAMPLES.md)** - Exemplos completos com UUIDs válidos
- **[specs/](./specs/001-ubiquitous-messaging-platform/)** - Especificações técnicas detalhadas
- **[contracts/](./specs/001-ubiquitous-messaging-platform/contracts/)** - Contratos Protobuf

---

## 📝 Licença

Este projeto está sob a licença MIT.

---

**Última atualização**: 23 de Novembro de 2025
