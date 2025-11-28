# 💬 Chat API - Plataforma de Mensageria Ubíqua

> API REST/gRPC para mensageria em tempo real com upload de arquivos e integração multiplataforma

[![Java](https://img.shields.io/badge/Java-17-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![gRPC](https://img.shields.io/badge/gRPC-1.64.0-blue)](https://grpc.io/)
[![Kafka](https://img.shields.io/badge/Kafka-7.5.0-black)](https://kafka.apache.org/)
[![MongoDB](https://img.shields.io/badge/MongoDB-7.0-green)](https://www.mongodb.com/)
[![MinIO](https://img.shields.io/badge/MinIO-latest-red)](https://min.io/)

---

## 📋 Índice

1. [Visão Geral](#-visão-geral)
2. [Funcionalidades](#-funcionalidades)
3. [Inicialização Rápida](#-inicialização-rápida)
4. [Estrutura do Projeto](#-estrutura-do-projeto)
5. [Testes](#-testes)
6. [Documentação](#-documentação)
7. [Troubleshooting](#-troubleshooting)

---

## 🎯 Visão Geral

Plataforma de mensageria completa com suporte a:
- **Mensagens em tempo real** via gRPC e REST
- **Upload de arquivos** até 2GB (multipart resumível)
- **Integração multiplataforma** (WhatsApp, Instagram - mocks funcionais)
- **Rastreamento de estado** de mensagens (SENT → DELIVERED → READ)
- **Arquitetura event-driven** com Apache Kafka
- **Armazenamento** MongoDB (mensagens) + MinIO (arquivos)

---

## ✨ Funcionalidades

### Camada 1 (MVP)
- ✅ Autenticação JWT
- ✅ Conversas privadas 1:1
- ✅ Envio e recebimento de mensagens
- ✅ Histórico de conversas
- ✅ Rastreamento de estados
- ✅ API gRPC e REST

### Camada 2 (Implementado)
- ✅ Upload multipart de arquivos (chunked, resumível)
- ✅ Armazenamento em MinIO (S3-compatible)
- ✅ Mensagens com anexos
- ✅ Adaptadores mock para WhatsApp e Instagram
- ✅ Webhooks de entrega multiplataforma
- ✅ Sistema de mapeamento de IDs (interno ↔ plataforma)

---

## 🚀 Inicialização Rápida

### Pré-requisitos

```powershell
# Verificar instalações
java -version    # Java 17+
mvn -version     # Maven 3.6+
docker --version # Docker 20+
```

### Opção 1: Script Automatizado (Recomendado)

```powershell
# Iniciar tudo (infraestrutura + aplicação)
.\start.ps1

# Parar tudo
.\stop.ps1
```

### Opção 2: Manual

```powershell
# 1. Infraestrutura
docker-compose -f docker-compose.dev.yml up -d

# 2. MinIO
docker start minio

# 3. Aguardar serviços (20-30s)
Start-Sleep 30

# 4. Aplicação
mvn clean package -DskipTests
java -jar target/meu-projeto-chat-1.0.0-SNAPSHOT.jar
```

### Serviços Disponíveis

| Serviço | URL | Credenciais |
|---------|-----|-------------|
| **API REST** | http://localhost:8081 | - |
| **API gRPC** | localhost:9090 | - |
| **MinIO Console** | http://localhost:9001 | minioadmin / minioadmin |
| **Kafka UI** | http://localhost:8080 | - |
| **MongoDB** | localhost:27017 | admin / password |
| **Health Check** | http://localhost:8081/actuator/health | - |

---

## 📁 Estrutura do Projeto

```
chat/
├── src/main/java/com/chat/
│   ├── adapter/           # Integrações multiplataforma (WhatsApp, Instagram)
│   ├── config/            # Configurações (MongoDB, Kafka, MinIO, gRPC)
│   ├── controller/        # REST Controllers
│   ├── grpc/              # Implementações gRPC
│   ├── model/             # Entidades (Message, Conversation, FileMetadata)
│   ├── repository/        # MongoDB Repositories
│   ├── service/           # Lógica de negócio
│   └── worker/            # Kafka Consumers (delivery, state updates)
├── src/main/proto/        # Definições Protobuf
├── scripts/
│   ├── test/              # Scripts de teste E2E
│   └── setup/             # Scripts de seed/configuração
├── docs/
│   ├── arquitetura/       # Documentação arquitetural
│   ├── implementacao/     # Status de implementação
│   └── testes/            # Guias de teste
├── tools/                 # Ferramentas (grpcurl, Postman collections)
├── docker-compose.dev.yml # Infraestrutura local
├── start.ps1              # Script de inicialização
└── stop.ps1               # Script de parada
```

---

## 🧪 Testes

### Testes Automatizados E2E

```powershell
# Upload de arquivo completo
.\scripts\test\test-file-upload-e2e.ps1

# Fluxo gRPC simples
.\scripts\test\test-grpc-simple.ps1

# Teste rápido (health check + basic flow)
.\scripts\test\test-quick.ps1
```

### Testes Manuais (Postman)

1. Importar collection: `tools/Postman-Layer2-FileUpload-Tests.json`
2. Seguir guia: `docs/testing/POSTMAN-GUIDE.md`

### Validações Realizadas

Todos os testes E2E validam:
- ✅ Kafka: Mensagens publicadas em formato Avro
- ✅ MongoDB: Persistência com estrutura camelCase
- ✅ MinIO: Upload e download de arquivos
- ✅ Webhooks: Callbacks de entrega funcionais
- ✅ Mapeamento de IDs: Resolução entre ID interno e ID de plataforma
- ✅ Performance: ~4-6s de ponta a ponta (upload → DELIVERED)

**Última execução**: 27/11/2025 - 5/5 testes PASSED

---

## 📚 Documentação

### Arquitetura
- [ARQUITETURA.md](docs/arquitetura/ARQUITETURA.md) - Visão geral da arquitetura
- [DESIGN-MOCKS.md](docs/arquitetura/DESIGN-MOCKS.md) - Design dos adaptadores mock

### Implementação
- [IMPLEMENTACAO-CAMADA2.md](docs/implementacao/IMPLEMENTACAO-CAMADA2.md) - Documentação completa Camada 2
- [VERIFICACAO-FASE1.md](docs/implementacao/VERIFICACAO-FASE1.md) - Verificação MVP
- [VERIFICACAO-FASE2.md](docs/implementacao/VERIFICACAO-FASE2.md) - Verificação Camada 2
- [SOLUCAO-RACE-CONDITION-WEBHOOK.md](docs/implementacao/SOLUCAO-RACE-CONDITION-WEBHOOK.md) - Solução para race condition

### Testes
- [TESTES.md](docs/testes/TESTES.md) - Estratégia de testes
- [GUIA-POSTMAN.md](docs/testes/GUIA-POSTMAN.md) - Guia Postman
- [GUIA-TESTE-UPLOAD-ARQUIVO.md](docs/testes/GUIA-TESTE-UPLOAD-ARQUIVO.md) - Guia de testes de upload

---

## 🔧 Troubleshooting

### Aplicação não inicia

```powershell
# Verificar portas ocupadas
netstat -ano | findstr ":8081"
netstat -ano | findstr ":9090"

# Matar processo
taskkill /PID <PID> /F
```

### Kafka: Erro "TimeoutException"

**Causa**: Scripts de teste tentavam ler mensagens Avro com console-consumer (formato String).

**Solução**: Scripts corrigidos para usar `GetOffsetShell` (apenas conta mensagens).

### MongoDB: Mensagem não encontrada

**Causa**: Scripts buscavam com `message_id` (snake_case), mas MongoDB usa `messageId` (camelCase).

**Solução**: Todos os scripts corrigidos para usar camelCase.

### Race Condition em Webhooks

**Problema resolvido**: WebhookTriggerService agora dispara webhooks APÓS mapeamento salvo e commitado.

**Detalhes**: Ver `docs/implementacao/SOLUCAO-RACE-CONDITION-WEBHOOK.md`

### Containers Docker não iniciam

```powershell
# Recriar containers (apaga dados!)
docker-compose -f docker-compose.dev.yml down -v
docker-compose -f docker-compose.dev.yml up -d
```

---

## 🏗️ Arquitetura

### Fluxo de Mensagem com Arquivo

```
Cliente → POST /api/files/upload/initiate
       → PUT presigned-url (MinIO)
       → POST /api/files/upload/complete
       → Kafka: message-events
       → MessageDeliveryWorker
       → WhatsApp/Instagram Adapters (mock)
       → PlatformMessageMapping (salvo)
       → Ack Kafka
       → WebhookTriggerService (async)
       → POST /api/webhooks/{platform} (1-4s depois)
       → Kafka: state-update-events
       → MessageStateUpdateWorker
       → MongoDB: status = DELIVERED
```

### Tecnologias

- **API**: Spring Boot 3.2.5, gRPC 1.64.0
- **Mensageria**: Apache Kafka 7.5.0
- **Banco de Dados**: MongoDB 7.0
- **Armazenamento**: MinIO (S3-compatible)
- **Build**: Maven 3.9+, Java 17

---

## 📊 Métricas de Performance

| Operação | Tempo Médio |
|----------|-------------|
| Envio de mensagem texto | ~200ms |
| Upload arquivo (< 10MB) | ~1-2s |
| Upload arquivo (100MB) | ~10-15s |
| Webhook callback | 1-4s após delivery |
| E2E (upload → DELIVERED) | ~4-6s |

---

## 📝 Licença

MIT License

---

## 👥 Contribuindo

1. Fork do projeto
2. Criar feature branch (`git checkout -b feature/nova-funcionalidade`)
3. Commit mudanças (`git commit -m 'Adiciona nova funcionalidade'`)
4. Push para branch (`git push origin feature/nova-funcionalidade`)
5. Abrir Pull Request

---

**Última atualização**: 27 de Novembro de 2025  
**Status**: ✅ Produção Ready (Camada 2 completa)
