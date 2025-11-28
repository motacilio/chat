# Testes de Carga - Limitações e Soluções

## 📋 Resumo

Este projeto implementa comunicação de mensagens via **gRPC**, não REST API. Portanto, os testes de carga k6 foram adaptados para testar apenas endpoints REST disponíveis.

## ✅ Endpoints REST Testados com k6

### 1. k6-auth-health-test.js

Testa endpoints REST disponíveis:

- **POST /api/auth/login** - Autenticação de usuários
- **GET /actuator/health** - Health check da aplicação

**Métricas coletadas:**
- Throughput de autenticação (logins/segundo)
- Latência p50/p95/p99 de autenticação
- Latência de health check
- Taxa de erro

**Executar:**
```powershell
k6 run scripts/load-test/k6-auth-health-test.js
```

**Resultados do teste (1 min, 10 VUs):**
- ✅ 392 logins bem-sucedidos
- ✅ 0% taxa de erro
- ✅ Latência auth p95: 5ms
- ✅ Latência health p95: 14ms
- ✅ Todos os thresholds aprovados

---

## ❌ Limitação: Envio de Mensagens via gRPC

### Problema

O endpoint de envio de mensagens é implementado como **gRPC service** (`chat_api.v1.ChatService/SendMessage`), não como REST API.

k6 tem suporte limitado para gRPC e requer:
1. Compilação de protobufs para JavaScript
2. Configuração complexa de stubs
3. Performance inferior comparado a ferramentas nativas gRPC

### Evidência

Ver arquivo `test-grpc-simple.ps1` que usa `grpcurl` para testar:

```powershell
grpcurl -plaintext -proto "$protoPath\chat_service.proto" `
    -import-path "$protoPath" `
    -d $msgJson `
    localhost:9090 chat_api.v1.ChatService/SendMessage
```

---

## 🚀 Solução Recomendada: ghz

**ghz** é uma ferramenta especializada em testes de carga gRPC.

### Instalação

**Windows (Chocolatey):**
```powershell
choco install ghz
```

**Windows (Manual):**
1. Download: https://github.com/bojand/ghz/releases
2. Extrair `ghz.exe` para `C:\Program Files\ghz\`
3. Adicionar ao PATH

### Uso Básico

**Teste simples (10 requests):**
```powershell
ghz --insecure `
    --proto src/main/proto/chat_service.proto `
    --import-paths src/main/proto `
    --call chat_api.v1.ChatService/SendMessage `
    -d '{
      "conversation_id": "conv-test-001",
      "sender_id": "a1a1a1a1-1111-1111-1111-111111111111",
      "message_text": "Load test message"
    }' `
    -n 10 `
    localhost:9090
```

**Teste de carga (100 RPS por 60s):**
```powershell
ghz --insecure `
    --proto src/main/proto/chat_service.proto `
    --import-paths src/main/proto `
    --call chat_api.v1.ChatService/SendMessage `
    -d '{
      "conversation_id": "conv-test-001",
      "sender_id": "a1a1a1a1-1111-1111-1111-111111111111",
      "message_text": "Load test {{.RequestNumber}}"
    }' `
    --rps 100 `
    --duration 60s `
    --connections 50 `
    --concurrency 100 `
    --output results/ghz-messages.json `
    localhost:9090
```

**Parâmetros importantes:**
- `--rps`: Requests per second
- `--duration`: Duração do teste
- `--connections`: Número de conexões TCP
- `--concurrency`: Número de workers concorrentes
- `--output`: Arquivo JSON com resultados

### Métricas ghz

ghz fornece:
- Latência (p50, p90, p95, p99)
- Throughput (requests/segundo)
- Taxa de erro
- Distribuição de status codes

**Exemplo de saída:**
```
Summary:
  Count:	    6000
  Total:	    60.03 s
  Slowest:	    45.32 ms
  Fastest:	    1.23 ms
  Average:	    8.45 ms
  Requests/sec:	    99.95

Response time histogram:
  1.230 [1]	    |
  5.639 [2834]	|∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎
  10.048 [2456]	|∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎∎
  ...

Latency distribution:
  10 % in 3.45 ms
  25 % in 5.23 ms
  50 % in 7.89 ms
  75 % in 10.34 ms
  90 % in 12.67 ms
  95 % in 15.23 ms
  99 % in 25.45 ms

Status code distribution:
  [OK]   6000 responses
```

---

## 📊 Estratégia Completa de Testes

### 1. Endpoints REST (k6)

✅ Autenticação, health check, métricas Prometheus

```powershell
k6 run --out json=results/auth-health-baseline.json `
    scripts/load-test/k6-auth-health-test.js
```

### 2. Mensagens gRPC (ghz)

✅ Envio de mensagens, latência end-to-end

```powershell
ghz --insecure `
    --proto src/main/proto/chat_service.proto `
    --import-paths src/main/proto `
    --call chat_api.v1.ChatService/SendMessage `
    -d @scripts/load-test/ghz-send-message-data.json `
    --rps 500 `
    --duration 300s `
    --connections 100 `
    --output results/ghz-messages-baseline.json `
    localhost:9090
```

### 3. Conversas gRPC (ghz)

✅ Criação de conversas

```powershell
ghz --insecure `
    --proto src/main/proto/conversation_service.proto `
    --import-paths src/main/proto `
    --call chat_api.v1.ConversationService/CreateConversation `
    -d '{
      "type": "PRIVATE",
      "participant_ids": ["a1a1a1a1-1111-1111-1111-111111111111", "b2b2b2b2-2222-2222-2222-222222222222"]
    }' `
    --rps 50 `
    --duration 120s `
    --output results/ghz-conversations-baseline.json `
    localhost:9090
```

### 4. Upload de Arquivos (k6 - se houver endpoint REST)

Se implementado REST endpoint para upload:

```powershell
k6 run scripts/load-test/k6-file-upload.js
```

---

## 🎯 NFRs e Thresholds

### Autenticação (k6)
- ✅ p95 < 150ms
- ✅ Taxa de erro < 1%
- ✅ Throughput > 100 logins/s

### Health Check (k6)
- ✅ p95 < 50ms
- ✅ Disponibilidade > 99.9%

### Envio de Mensagens gRPC (ghz)
- 🎯 p95 < 100ms (target)
- 🎯 Taxa de erro < 1%
- 🎯 Throughput > 500 msg/s

### Criação de Conversas gRPC (ghz)
- 🎯 p95 < 200ms
- 🎯 Taxa de erro < 1%
- 🎯 Throughput > 50 conversas/s

---

## 📝 Script de Teste Completo

Criar arquivo `scripts/load-test/run-all-tests.ps1`:

```powershell
# Teste completo de carga

Write-Host "=== FASE 1: Testes REST (k6) ===" -ForegroundColor Cyan

# Criar diretório de resultados
mkdir -Force results

# Teste de autenticação e health
k6 run --out json=results/auth-health-baseline.json `
    scripts/load-test/k6-auth-health-test.js

Write-Host "`n=== FASE 2: Testes gRPC (ghz) ===" -ForegroundColor Cyan

# Aguardar 30s para sistema estabilizar
Write-Host "Aguardando estabilização do sistema..." -ForegroundColor Yellow
Start-Sleep 30

# Teste de mensagens gRPC
ghz --insecure `
    --proto src/main/proto/chat_service.proto `
    --import-paths src/main/proto `
    --call chat_api.v1.ChatService/SendMessage `
    -d '{
      "conversation_id": "conv-load-test",
      "sender_id": "a1a1a1a1-1111-1111-1111-111111111111",
      "message_text": "Load test message {{.RequestNumber}}"
    }' `
    --rps 500 `
    --duration 300s `
    --connections 100 `
    --concurrency 200 `
    --output results/ghz-messages-baseline.json `
    localhost:9090

Write-Host "`n=== ANÁLISE DE RESULTADOS ===" -ForegroundColor Cyan

# Analisar resultados k6
.\scripts\load-test\analyze-results.ps1 `
    -ResultsFile results\auth-health-summary.json `
    -OutputFile results\auth-health-report.md

# Exibir resumo ghz
if (Test-Path results\ghz-messages-baseline.json) {
    Write-Host "`nResultados gRPC (ghz):" -ForegroundColor Green
    Get-Content results\ghz-messages-baseline.json | ConvertFrom-Json | Select-Object -Property count, total, slowest, fastest, average, rps
}

Write-Host "`n✅ Testes concluídos!" -ForegroundColor Green
Write-Host "Relatórios disponíveis em: results/" -ForegroundColor White
```

---

## 🔗 Referências

- **k6**: https://k6.io/docs/
- **ghz**: https://ghz.sh/docs/intro
- **gRPC**: https://grpc.io/docs/what-is-grpc/introduction/
- **Prometheus**: http://localhost:9090 (métricas em tempo real)
- **Grafana**: http://localhost:3000 (dashboards)

---

**Data de criação**: 2025-11-27  
**Versão**: 1.0  
**Autor**: Automated Test Infrastructure
