# K6 Load Testing Scripts

Scripts de teste de carga para validar desempenho e escalabilidade do sistema de mensagens.

## 📋 Pré-requisitos

### Instalar k6

**Opção 1: Chocolatey**
```powershell
choco install k6
```

**Opção 2: Winget**
```powershell
winget install k6
```

**Opção 3: Download Manual**
1. Acesse: https://github.com/grafana/k6/releases
2. Baixe o instalador para Windows
3. Adicione k6 ao PATH

**Verificar instalação:**
```powershell
k6 version
# Esperado: k6 v0.x.x
```

### Iniciar Infraestrutura

**1. Subir Docker Compose:**
```powershell
docker-compose up -d
```

**2. Subir API (em outro terminal):**
```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

**3. Verificar saúde:**
```powershell
Invoke-WebRequest http://localhost:8080/actuator/health
# Esperado: {"status":"UP"}
```

---

## 📂 Scripts Disponíveis

### 1. k6-send-messages.js - Teste de Envio de Mensagens

Simula carga de envio de mensagens via REST API com 3 cenários:

**Cenários:**
- **Warmup** (0-7min): 1→100 VUs (Virtual Users)
- **Load Test** (7-25min): 0→500→1000 VUs
- **Spike Test** (25-27min): 0→2000 VUs (pico de carga)

**Métricas customizadas:**
- `messages_created`: Contador de mensagens criadas
- `message_latency`: Latência de criação (p50/p95/p99)
- `errors`: Taxa de erro de mensagens
- `idempotent_requests`: Requisições com message_id duplicado

**Thresholds:**
- ✅ p95 < 100ms
- ✅ p99 < 200ms
- ✅ Taxa de erro < 1%

**Executar:**
```powershell
# Criar diretório de resultados
mkdir results -ErrorAction SilentlyContinue

# Rodar teste completo (27 minutos)
k6 run --out json=results/messages-full.json scripts/load-test/k6-send-messages.js

# Rodar apenas warmup (para debug)
k6 run --stage 2m:100,3m:100,2m:0 scripts/load-test/k6-send-messages.js
```

### 2. k6-file-upload.js - Teste de Upload de Arquivos

Testa upload de arquivos com diferentes tamanhos:

**Cenários:**
- **Small Files** (0-2min): 50 uploads/s, arquivos <1MB
- **Medium Files** (2m30s-5m30s): 10 uploads/s, arquivos 1-10MB
- **Large Files** (6m-10m): 2 uploads/s, arquivos 10-100MB

**Métricas customizadas:**
- `files_uploaded`: Contador de arquivos enviados
- `bytes_uploaded`: Total de bytes transferidos
- `upload_throughput_mbps`: Throughput em MB/s
- `upload_latency`: Latência de upload
- `upload_errors`: Taxa de erro de uploads

**Thresholds:**
- ✅ Small: p95 < 1s
- ✅ Medium: p95 < 5s
- ✅ Large: p95 < 30s
- ✅ Taxa de erro < 5%

**Executar:**
```powershell
# Rodar teste completo (10 minutos)
k6 run --out json=results/upload-full.json scripts/load-test/k6-file-upload.js

# Rodar apenas small files (para debug)
k6 run --stage 2m:20 scripts/load-test/k6-file-upload.js
```

### 3. analyze-results.ps1 - Análise de Resultados

Script PowerShell para processar JSON do k6 e gerar relatórios.

**Executar:**
```powershell
# Analisar resultados de mensagens
.\scripts\load-test\analyze-results.ps1 `
    -ResultsFile results\messages-full.json `
    -OutputFile results\messages-report.md

# Analisar resultados de upload
.\scripts\load-test\analyze-results.ps1 `
    -ResultsFile results\upload-full.json `
    -OutputFile results\upload-report.md
```

**Saída:**
- Relatório Markdown com tabelas de latência
- Status de thresholds (PASS/FAIL)
- Recomendações de otimização
- Resumo executivo

---

## 🎯 Fluxo de Teste Completo

### Passo a Passo

**1. Preparação:**
```powershell
# Subir infraestrutura
docker-compose up -d
docker-compose -f docker-compose.monitoring.yml up -d

# Aguardar 30s para estabilizar
Start-Sleep 30

# Verificar containers
docker ps
# Esperado: kafka, mongodb, minio, prometheus, grafana
```

**2. Subir API:**
```powershell
mvn clean package -DskipTests
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

**3. Executar Testes:**
```powershell
# Teste de mensagens (27min)
k6 run --out json=results/messages-baseline.json scripts/load-test/k6-send-messages.js

# Aguardar 5min para sistema estabilizar
Start-Sleep 300

# Teste de upload (10min)
k6 run --out json=results/upload-baseline.json scripts/load-test/k6-file-upload.js
```

**4. Analisar Resultados:**
```powershell
# Gerar relatórios
.\scripts\load-test\analyze-results.ps1 -ResultsFile results\messages-baseline.json -OutputFile results\messages-report.md
.\scripts\load-test\analyze-results.ps1 -ResultsFile results\upload-baseline.json -OutputFile results\upload-report.md

# Visualizar relatórios
code results\messages-report.md
code results\upload-report.md
```

**5. Monitorar via Grafana:**
```powershell
# Acessar dashboard
Start-Process http://localhost:3000

# Login: admin/admin
# Dashboard: "Chat API - Monitoring"
```

---

## 📊 Interpretação de Resultados

### Latência (p95)

- ✅ **< 100ms**: Excelente, sistema responsivo
- ⚠️ **100-200ms**: Aceitável, considere otimização
- ❌ **> 200ms**: Problema, investigar bottlenecks

### Taxa de Erro

- ✅ **< 1%**: Normal, erros esperados (rate limiting, validação)
- ⚠️ **1-5%**: Atenção, verificar logs
- ❌ **> 5%**: Crítico, sistema instável

### Throughput (mensagens/s)

- ✅ **> 100 msg/s**: Atende requisito base
- ✅ **> 500 msg/s**: Excelente capacidade
- ⚠️ **< 100 msg/s**: Investigar limitação (CPU, DB, Kafka)

### Upload Throughput (MB/s)

- ✅ **Small files > 10 MB/s**: Bom
- ✅ **Medium files > 5 MB/s**: Bom
- ✅ **Large files > 2 MB/s**: Aceitável

---

## 🔍 Troubleshooting

### Erro: "k6: command not found"

**Solução:**
```powershell
# Verificar PATH
$env:PATH -split ';' | Select-String k6

# Reinstalar k6
choco install k6 --force
```

### Erro: "Connection refused on localhost:8080"

**Solução:**
```powershell
# Verificar se API está rodando
netstat -ano | findstr :8080

# Iniciar API se necessário
mvn spring-boot:run
```

### Erro: "Too many open files"

**Solução:**
- Reduzir número de VUs nos cenários
- Aumentar timeout entre requisições
- Fechar conexões no script

### Threshold failing (p95 > 100ms)

**Possíveis causas:**
1. **MongoDB lento**: Verificar índices em `messages` collection
2. **Kafka lag**: Verificar consumer offset (`kafka_consumer_lag`)
3. **CPU saturado**: Verificar via Grafana (JVM CPU usage)
4. **Conexões esgotadas**: Verificar pool do MongoDB

**Debug:**
```powershell
# Verificar logs durante teste
docker logs chat-api-1 -f

# Verificar métricas Prometheus
Invoke-WebRequest http://localhost:8080/actuator/prometheus
```

---

## 📚 Referências

- [k6 Documentation](https://k6.io/docs/)
- [k6 Examples](https://k6.io/docs/examples/)
- [Prometheus Metrics](https://prometheus.io/docs/concepts/metric_types/)
- [Grafana Dashboards](https://grafana.com/grafana/dashboards/)

---

**Última atualização**: $(Get-Date -Format "yyyy-MM-dd")  
**Autor**: Automated Setup Script  
**Versão**: 1.0
