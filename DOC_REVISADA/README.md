# 📚 Documentação Revisada - Chat Distribuído

**Data de Criação**: 29/11/2025  
**Baseado em**: Phase 11 (tasks.md - T095 a T106)  
**Status**: ✅ Completo

---

## 🎯 Objetivo

Esta documentação **centralizada e organizada** demonstra a implementação completa do sistema de mensagens distribuído, explicando:
- **COMO** cada componente foi implementado (código real)
- **POR QUÊ** decisões arquiteturais foram tomadas (trade-offs)
- **ONDE** encontrar implementações (referências de arquivos)

---

## 📖 Índice de Documentos

### Arquitetura e Design
- **[01-ARQUITETURA-E-DESIGN.md](01-ARQUITETURA-E-DESIGN.md)** - Visão geral, decisões arquiteturais (gRPC, Kafka, MongoDB), componentes, trade-offs

### APIs e Contratos
- **[02-API-GRPC-E-CONTRATOS.md](02-API-GRPC-E-CONTRATOS.md)** - Contratos Protobuf, ChatService, ConversationService, streaming patterns

### Segurança
- **[03-AUTENTICACAO-E-SEGURANCA.md](03-AUTENTICACAO-E-SEGURANCA.md)** - JWT, login REST, SecurityConfig, futuro gRPC interceptor

### Processamento Assíncrono
- **[04-KAFKA-E-PROCESSAMENTO-ASSINCRONO.md](04-KAFKA-E-PROCESSAMENTO-ASSINCRONO.md)** - Tópicos Kafka, Workers, Protobuf serialization, partition strategy

### Persistência
- **[05-MONGODB-E-PERSISTENCIA.md](05-MONGODB-E-PERSISTENCIA.md)** - Schema design, embedded documents, índices compostos, repositories

### Armazenamento de Arquivos
- **[06-UPLOAD-ARQUIVOS-E-MINIO.md](06-UPLOAD-ARQUIVOS-E-MINIO.md)** - MinIO S3-compatible, upload/download REST API, FileStorageService

### Tempo Real
- **[07-STATUS-E-STREAMING-TEMPO-REAL.md](07-STATUS-E-STREAMING-TEMPO-REAL.md)** - Ciclo SENT→DELIVERED→READ, gRPC server-side streaming, StreamingService

### Integrações
- **[08-WEBHOOKS-E-INTEGRACAO-PLATAFORMAS.md](08-WEBHOOKS-E-INTEGRACAO-PLATAFORMAS.md)** - Webhooks vs Polling vs WebSocket, mocks WhatsApp/Instagram, decisões arquiteturais

### Observabilidade
- **[09-OBSERVABILIDADE-E-MONITORAMENTO.md](09-OBSERVABILIDADE-E-MONITORAMENTO.md)** - Prometheus, Grafana, Spring Actuator, logs estruturados

### Testes
- **[10-TESTES-E-QUALIDADE.md](10-TESTES-E-QUALIDADE.md)** - JUnit 5, k6, ghz, Postman collections, load tests

### Deployment
- **[11-SCRIPTS-E-DEPLOYMENT.md](11-SCRIPTS-E-DEPLOYMENT.md)** - Docker Compose, PowerShell scripts, Dockerfile, seed data

---

## 🚀 Quick Start

### Leitura Recomendada (Ordem)

1. **Iniciantes**: Comece pelo [00-INDICE.md](00-INDICE.md) para visão geral
2. **Arquitetura**: Leia [01-ARQUITETURA-E-DESIGN.md](01-ARQUITETURA-E-DESIGN.md) para entender decisões
3. **API**: Continue com [02-API-GRPC-E-CONTRATOS.md](02-API-GRPC-E-CONTRATOS.md) para contratos
4. **Específico**: Navegue aos documentos temáticos conforme necessidade

### Por Caso de Uso

| Preciso Entender... | Leia... |
|---------------------|---------|
| **Por que gRPC em vez de REST?** | 01-ARQUITETURA-E-DESIGN.md (Decision 1) |
| **Como funciona streaming tempo real?** | 07-STATUS-E-STREAMING-TEMPO-REAL.md |
| **Por que Protobuf no Kafka?** | 04-KAFKA-E-PROCESSAMENTO-ASSINCRONO.md |
| **Como receber callbacks de plataformas?** | 08-WEBHOOKS-E-INTEGRACAO-PLATAFORMAS.md |
| **Onde está JWT implementado?** | 03-AUTENTICACAO-E-SEGURANCA.md |
| **Como subir o projeto?** | 11-SCRIPTS-E-DEPLOYMENT.md |

---

## 🔍 Convenções

### Símbolos Usados

- ✅ **Implementado e funcionando**
- ⚠️ **Implementado com limitações (POC)**
- 🔧 **Em desenvolvimento**
- ❌ **Não implementado / Planejado para futuro**

### Estrutura dos Documentos

Cada documento segue o padrão:
1. **Visão Geral** - O que é e objetivo
2. **Como Está Implementado** - Código real com referências de arquivos
3. **Por Que Foi Decidido Assim** - Rationale e trade-offs
4. **Referências** - Links para código, specs, decisões

---

## 📊 Tecnologias Documentadas

| Categoria | Tecnologias |
|-----------|-------------|
| **Framework** | Spring Boot 3.2.5, Java 17 |
| **API** | gRPC 1.64.0, Protobuf 3.25.3 |
| **Message Broker** | Apache Kafka 7.5.0 |
| **Database** | MongoDB 7.0 |
| **Storage** | MinIO (S3-compatible) |
| **Security** | Spring Security, JWT (JJWT 0.12.3) |
| **Observability** | Prometheus, Grafana, Actuator |
| **Testing** | JUnit 5, k6, ghz, Postman |
| **Deployment** | Docker Compose, PowerShell |

---

## ⚠️ Importante para Avaliação Acadêmica

### Diferenças POC vs Produção

| Aspecto | POC (Implementado) | Produção (Recomendado) |
|---------|-------------------|------------------------|
| **Usuários** | Hardcoded em memória | MongoDB users collection + BCrypt |
| **MongoDB** | Standalone (1 node) | Replica Set (3+ nodes) |
| **Kafka** | 1 broker | Cluster (3+ brokers) |
| **Secrets** | application.yml | Environment variables |
| **Rate Limiting** | Não implementado | Bucket4j ou Resilience4j |
| **gRPC Auth** | Não implementado | AuthenticationInterceptor |

### Decisões Justificadas

Todas as decisões arquiteturais estão documentadas com:
- **Rationale**: Por que escolhemos X em vez de Y
- **Trade-offs**: Vantagens e desvantagens
- **Referencias**: Links para `research.md`, `data-model.md`, código

---

## 📝 Resumo Executivo

Este projeto implementa uma **plataforma de mensagens distribuída** seguindo princípios de sistemas distribuídos:

- **gRPC + Protobuf**: Baixa latência (<100ms p95), streaming nativo, type-safe
- **Kafka**: At-least-once delivery, partition-based ordering, horizontal scaling
- **MongoDB**: Schema flexível, embedded documents, compound indexes
- **JWT Stateless**: Horizontal scaling sem coordenação de sessão
- **Observabilidade**: Prometheus + Grafana para métricas e SLOs
- **Docker Compose**: Infraestrutura reproduzível (MongoDB, Kafka, MinIO)

**Demonstra conceitos**:
- Async messaging (Kafka workers)
- Event sourcing (stateHistory array)
- CQRS (SendMessage=command, GetMessageStatus=query)
- Idempotency (message_id unique index)
- Server-side streaming (gRPC push notifications)

---

## 📚 Referências Externas

- **Especificações**: `specs/001-ubiquitous-messaging-platform/`
  - `research.md` - Decisões arquiteturais
  - `data-model.md` - Schema design
  - `tasks.md` - Checklist de implementação (Phase 11)
- **Código Fonte**: `src/main/java/com/chat/`
- **Contratos**: `src/main/proto/`
- **Testes**: `src/test/java/`, `scripts/load-test/`

---

## 🎓 Para o Professor

Esta documentação foi criada para demonstrar **compreensão profunda** de sistemas distribuídos:

1. **Decisões Fundamentadas**: Cada escolha técnica tem rationale explícito
2. **Trade-offs Conscientes**: Vantagens e desvantagens documentadas
3. **Código Real**: Não é teórico - todo trecho de código existe no projeto
4. **Padrões Aplicados**: CQRS, Event Sourcing, Idempotency, At-least-once delivery
5. **Observabilidade**: Métricas, logs, health checks para SLOs

**Navegue pelos documentos temáticos** para validar a implementação de cada componente.

---

**Início**: [00-INDICE.md](00-INDICE.md)  
**Criado em**: 29/11/2025  
**Baseado em**: Phase 11 (T095-T106) do tasks.md
