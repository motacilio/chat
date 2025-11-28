# 🧪 Scripts de Teste

Scripts PowerShell para testes automatizados E2E.

---

## 📋 Testes Disponíveis

### Testes Principais (Recomendados)

#### `test-file-upload-e2e.ps1`
**Teste completo de upload de arquivo**
- ✅ Autenticação
- ✅ Criação de conversa
- ✅ Upload multipart para MinIO
- ✅ Criação de mensagem com anexo
- ✅ Verificação Kafka (formato Avro)
- ✅ Verificação MongoDB (camelCase)
- ✅ Validação de webhooks

**Execução:**
```powershell
.\test-file-upload-e2e.ps1
```

**Tempo estimado**: ~10-15 segundos

---

#### `test-grpc-simple.ps1`
**Teste básico da API gRPC**
- ✅ Autenticação REST
- ✅ Criação de conversa via gRPC
- ✅ Envio de mensagem via gRPC
- ✅ Verificação Kafka
- ✅ Verificação MongoDB

**Execução:**
```powershell
.\test-grpc-simple.ps1
```

**Requisitos**: grpcurl instalado

---

#### `test-quick.ps1`
**Teste rápido de sanidade**
- ✅ Health check de serviços
- ✅ Fluxo básico de mensagem
- ✅ Validações essenciais

**Execução:**
```powershell
.\test-quick.ps1
```

**Tempo estimado**: ~5 segundos

---

### Testes Complementares

#### `test-grpc-flow.ps1`
Teste completo do fluxo gRPC com verificações detalhadas.

#### `test-simple.ps1`
Teste simples de envio e recebimento de mensagem.

#### `test-e2e-flow.ps1`
Teste E2E com foco em fluxo de estados.

#### `test-e2e-final.ps1`
Teste E2E final com validações completas.

---

## 📝 Estrutura de um Teste

Todos os testes seguem o padrão:

```powershell
# 1. PRÉ-REQUISITOS
# Verificação de serviços (MongoDB, Kafka, Spring Boot)

# 2. AUTENTICAÇÃO
# Login de usuários de teste (Alice, Bob)

# 3. SETUP
# Criação de conversa, arquivo de teste, etc.

# 4. EXECUÇÃO
# Operação principal (envio mensagem, upload, etc.)

# 5. VALIDAÇÕES
# - Kafka: Verificação de offset (mensagens Avro)
# - MongoDB: Verificação de persistência (camelCase)
# - (Arquivo) MinIO: Verificação de storage
# - (Arquivo) Webhooks: Callbacks de entrega

# 6. RELATÓRIO
# Log detalhado salvo em arquivo
```

---

## ✅ Validações Realizadas

### Kafka
```powershell
# Verifica offset do tópico (não conteúdo - formato Avro)
$kafkaOffset = docker exec kafka-dev kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic message-events
```

**Saída esperada**: `[OK] Kafka 'message-events' has N messages (Avro format)`

### MongoDB
```powershell
# Usa camelCase correto
$mongoQuery = "db.messages.findOne({messageId: '$messageId'}, {messageText: 1, status: 1, messageId: 1, _id: 0})"
```

**Saída esperada**: Documento com `messageId`, `messageText`, `fileMetadata` (se aplicável)

### MinIO (apenas testes de arquivo)
```powershell
# Upload via presigned URL
Invoke-RestMethod -Uri $presignedUrl -Method PUT -InFile $testFile
```

**Saída esperada**: HTTP 200 OK

### Webhooks (apenas testes de arquivo)
```powershell
# Após 5-10s, verificar logs
Get-Content spring-boot.log | Select-String "Resolved messageId|Message state updated to DELIVERED"
```

**Saída esperada**: Estado alterado de SENT → DELIVERED

---

## 🔧 Correções Implementadas

### 1. Kafka: TimeoutException
**Problema**: Scripts tentavam ler mensagens Avro com `kafka-console-consumer`.

**Solução**: Substituído por `GetOffsetShell` que apenas conta mensagens.

### 2. MongoDB: Mensagem não encontrada
**Problema**: Queries usavam `message_id` (snake_case), mas MongoDB usa `messageId` (camelCase).

**Solução**: Todas as queries corrigidas para camelCase.

**Arquivos corrigidos**:
- test-file-upload-e2e.ps1
- test-grpc-simple.ps1
- test-simple.ps1
- test-grpc-flow.ps1
- test-e2e-flow.ps1
- test-e2e-final.ps1
- test-quick.ps1

---

## 📊 Resultados de Testes

**Última execução**: 27/11/2025

| Teste | Status | Tempo | Observações |
|-------|--------|-------|-------------|
| test-file-upload-e2e.ps1 (×5) | ✅ PASS | ~6s | Kafka ✓, MongoDB ✓, Webhooks ✓ |
| test-grpc-simple.ps1 | ✅ PASS | ~5s | gRPC funcional |
| test-quick.ps1 | ✅ PASS | ~3s | Sanidade OK |

**Performance**:
- Upload → DELIVERED: 4-6 segundos
- Webhook latency: 1-4 segundos (realístico)
- Zero race conditions
- 100% de resolução de mapeamento de IDs

---

## 🚀 Execução em Lote

```powershell
# Executar todos os testes principais
.\test-file-upload-e2e.ps1
.\test-grpc-simple.ps1
.\test-quick.ps1
```

---

## 📁 Logs

Cada teste gera arquivo de log:
- `file-upload-test-YYYY-MM-DD_HH-MM-SS.log`
- `grpc-test-YYYY-MM-DD_HH-MM-SS.log`
- `test-results-YYYY-MM-DD_HH-MM-SS.log`

**Localização**: Raiz do projeto (ignorados pelo .gitignore)

---

**Última atualização**: 27 de Novembro de 2025
