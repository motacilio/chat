# Guia Completo: Grafana e Métricas da API

**Data**: 2025-11-30  
**Objetivo**: Visualizar métricas da API (Circuit Breaker, Rate Limiting, MongoDB, Kafka) no Grafana

---

## 🚀 Quick Start: Iniciar Monitoramento

### 1. Iniciar Sistema Completo com Monitoramento

```powershell
# 1. Parar sistema antigo (se estiver rodando)
docker-compose down

# 2. Iniciar infraestrutura base
docker-compose up -d

# 3. Aguardar MongoDB Replica Set inicializar (30 segundos)
Start-Sleep -Seconds 30

# 4. Iniciar monitoramento (Prometheus + Grafana)
docker-compose -f docker-compose.monitoring.yml up -d

# 5. Verificar status
docker ps --filter "name=prometheus" --filter "name=grafana"
```

### 2. Acessar Interfaces

| Serviço | URL | Credenciais | Descrição |
|---------|-----|-------------|-----------|
| **Grafana** | http://localhost:3000 | admin/admin | Dashboards visuais |
| **Prometheus** | http://localhost:9090 | (sem auth) | Banco de métricas |
| **API Metrics** | http://localhost:8081/actuator/prometheus | (sem auth) | Endpoint de métricas |
| **API Health** | http://localhost:8081/actuator/health | (sem auth) | Status da aplicação |

---

## 📊 Configurar Grafana (Primeira Vez)

### Passo 1: Login Inicial

1. Acesse http://localhost:3000
2. Login: `admin`
3. Senha: `admin`
4. **IMPORTANTE**: Sistema pedirá para alterar senha → escolha uma nova ou clique "Skip"

### Passo 2: Adicionar Data Source Prometheus

1. **Menu lateral esquerdo** → ⚙️ **Configuration** → **Data sources**
2. Clique em **"Add data source"**
3. Selecione **Prometheus**
4. Configure:
   - **Name**: `Prometheus`
   - **URL**: `http://prometheus:9090`
   - **Access**: `Server (default)`
5. Clique em **"Save & Test"** → deve aparecer "Data source is working"

---

## 📈 Criar Dashboard para Métricas da API

### Opção 1: Dashboard Básico (Rápido)

#### Painel 1: Rate Limiting

1. Clique em **"+"** → **Dashboard** → **Add visualization**
2. Selecione data source: **Prometheus**
3. No campo **Metric**, cole:
   ```promql
   resilience4j_ratelimiter_available_permissions{name=~"sendMessage.*|fileUpload.*"}
   ```
4. Configure:
   - **Title**: "Rate Limiting - Tokens Disponíveis"
   - **Legend**: `{{name}}`
   - **Unit**: none
5. Clique em **Apply**

#### Painel 2: Circuit Breaker Status

1. Clique em **"Add"** → **Visualization**
2. Métrica:
   ```promql
   resilience4j_circuitbreaker_state{name=~"minio|whatsapp|instagram"}
   ```
3. Configure:
   - **Title**: "Circuit Breaker - Estado"
   - **Visualization**: Stat ou Gauge
   - **Value mappings**:
     - `0` → "Closed (OK)"
     - `1` → "Open (FAIL)"
     - `2` → "Half-Open (Testing)"
4. Clique em **Apply**

#### Painel 3: Taxa de Requisições

1. Adicionar nova visualização
2. Métrica:
   ```promql
   rate(messages_sent_total[1m])
   ```
3. Configure:
   - **Title**: "Mensagens por Segundo"
   - **Unit**: "messages/sec"
4. Clique em **Apply**

#### Painel 4: Latência p95

1. Adicionar nova visualização
2. Métrica:
   ```promql
   histogram_quantile(0.95, rate(message_latency_seconds_bucket[5m]))
   ```
3. Configure:
   - **Title**: "Latência p95 (SLO: <100ms)"
   - **Unit**: seconds
   - **Thresholds**: 
     - Verde: < 0.1 (100ms)
     - Amarelo: 0.1 - 0.2
     - Vermelho: > 0.2
4. Clique em **Apply**

5. **Salvar Dashboard**: Clique em 💾 (Save dashboard) → Nome: `API Escalabilidade`

---

### Opção 2: Dashboard Completo (Detalhado)

Crie um dashboard com painéis organizados:

#### Seção 1: Rate Limiting

```promql
# Tokens disponíveis por usuário
resilience4j_ratelimiter_available_permissions{name=~"sendMessage.*"}

# Taxa de rejeição
rate(resilience4j_ratelimiter_rejected_calls_total[1m])

# Timeout de rate limiting
resilience4j_ratelimiter_waiting_threads
```

#### Seção 2: Circuit Breaker

```promql
# Estado do circuit breaker (0=Closed, 1=Open, 2=Half-Open)
resilience4j_circuitbreaker_state

# Taxa de falha
resilience4j_circuitbreaker_failure_rate

# Chamadas totais por estado
resilience4j_circuitbreaker_calls_seconds_count

# Slow calls
resilience4j_circuitbreaker_slow_calls_total
```

#### Seção 3: MongoDB

```promql
# Connection pool usage (estimativa via JVM metrics)
jvm_threads_live_threads{application="chat-api"}

# MongoDB operations
rate(messages_sent_total[1m])
rate(messages_processed_total[1m])
```

#### Seção 4: Kafka

```promql
# Consumer lag (se configurado)
kafka_consumer_lag_seconds

# Mensagens processadas
rate(messages_processed_total{status="DELIVERED"}[1m])
```

#### Seção 5: Performance

```promql
# Latência p50, p95, p99
histogram_quantile(0.50, rate(message_latency_seconds_bucket[5m]))
histogram_quantile(0.95, rate(message_latency_seconds_bucket[5m]))
histogram_quantile(0.99, rate(message_latency_seconds_bucket[5m]))

# Taxa de sucesso
(sum(rate(messages_processed_total{status="DELIVERED"}[1m])) / sum(rate(messages_sent_total[1m]))) * 100

# Throughput
rate(messages_sent_total[1m])
```

---

## 🧪 Testar Métricas em Tempo Real

### Teste 1: Rate Limiting

```powershell
# Gerar 150 mensagens (limite é 100/min)
1..150 | ForEach-Object {
    curl -X POST http://localhost:9090/... -H "..." -d "..."
    Start-Sleep -Milliseconds 100
}

# No Grafana, você verá:
# - Tokens disponíveis diminuindo de 100 → 0
# - Taxa de rejeição aumentando após 100 mensagens
# - Rejeições com status RESOURCE_EXHAUSTED
```

### Teste 2: Circuit Breaker

```powershell
# 1. Parar MinIO (simular falha)
docker stop minio

# 2. Tentar upload de arquivo 10 vezes
1..10 | ForEach-Object {
    curl -X POST http://localhost:8081/api/files/initiate -H "..." -d "..."
}

# 3. No Grafana, você verá:
# - Circuit breaker "minio" mudando de 0 (Closed) para 1 (Open)
# - Taxa de falha aumentando de 0% → 50% → Circuit abre
# - Fallback method sendo chamado

# 4. Reiniciar MinIO
docker start minio

# 5. Aguardar 10 segundos (wait duration)
Start-Sleep -Seconds 10

# 6. Grafana mostrará:
# - Circuit mudando para 2 (Half-Open)
# - Próxima chamada de teste → Se sucesso, volta para 0 (Closed)
```

### Teste 3: Throughput

```powershell
# Gerar carga contínua (100 msg/s por 1 minuto)
$startTime = Get-Date
while ((Get-Date) -lt $startTime.AddMinutes(1)) {
    curl -X POST http://localhost:9090/... -H "..." -d "..."
    Start-Sleep -Milliseconds 10
}

# No Grafana, você verá:
# - Throughput subindo para ~100 msg/s
# - Latência p95 mantendo < 100ms (SLO)
# - Connection pool usage aumentando
```

---

## 🔍 Queries Úteis do Prometheus

Acesse http://localhost:9090/graph e execute:

### Verificar Métricas Disponíveis

```promql
# Listar todas as métricas
{job="chat-api"}

# Métricas de Resilience4j
resilience4j_circuitbreaker_state
resilience4j_ratelimiter_available_permissions

# Métricas customizadas
messages_sent_total
messages_processed_total
message_latency_seconds
```

### Alertas Recomendados

```promql
# Circuit breaker aberto por mais de 1 minuto
resilience4j_circuitbreaker_state{state="open"} > 0

# Taxa de erro > 1%
(sum(rate(messages_processed_total{status="FAILED"}[5m])) 
 / sum(rate(messages_sent_total[5m]))) * 100 > 1

# Latência p95 > 100ms (SLO breach)
histogram_quantile(0.95, rate(message_latency_seconds_bucket[5m])) > 0.1

# Rate limiting rejeitando > 10 req/s
rate(resilience4j_ratelimiter_rejected_calls_total[1m]) > 10
```

---

## 📋 Checklist de Verificação

Após iniciar Grafana e Prometheus, verifique:

- [ ] Grafana acessível em http://localhost:3000
- [ ] Prometheus acessível em http://localhost:9090
- [ ] Data source Prometheus configurado no Grafana
- [ ] Endpoint /actuator/prometheus retorna métricas
- [ ] Prometheus está coletando métricas (Targets → chat-api = UP)
- [ ] Dashboard criado com painéis de Rate Limiting e Circuit Breaker
- [ ] Métricas atualizando em tempo real

---

## 🐛 Troubleshooting

### Grafana não conecta ao Prometheus

**Problema**: "Bad Gateway" ou "Connection refused"

**Solução**:
```powershell
# 1. Verificar se Prometheus está rodando
docker ps --filter "name=prometheus"

# 2. Verificar rede Docker
docker network inspect chat_default

# 3. Testar conectividade dentro do container Grafana
docker exec grafana curl http://prometheus:9090/-/healthy
```

### Métricas não aparecem no Grafana

**Problema**: Queries retornam vazio

**Solução**:
```powershell
# 1. Verificar se API está expondo métricas
curl http://localhost:8081/actuator/prometheus | Select-String "resilience4j"

# 2. Verificar se Prometheus está coletando
# Acesse http://localhost:9090/targets
# chat-api deve estar "UP"

# 3. Se DOWN, verificar config do Prometheus
docker exec prometheus cat /etc/prometheus/prometheus.yml
```

### Dashboard em branco

**Problema**: Painéis mostram "No data"

**Solução**:
```powershell
# 1. Gerar tráfego na API
curl http://localhost:8081/actuator/health

# 2. Ajustar time range no Grafana (canto superior direito)
# Selecione "Last 5 minutes" ou "Last 15 minutes"

# 3. Verificar query no painel (Edit)
# Testar query diretamente no Prometheus primeiro
```

---

## 📚 Recursos Adicionais

- **Prometheus Query Examples**: https://prometheus.io/docs/prometheus/latest/querying/examples/
- **Grafana Dashboards**: https://grafana.com/grafana/dashboards/
- **Resilience4j Metrics**: https://resilience4j.readme.io/docs/micrometer

---

## 🎯 Métricas Essenciais para SLOs

| Métrica | Query | SLO Target | Alerta |
|---------|-------|------------|--------|
| Latência p95 | `histogram_quantile(0.95, ...)` | < 100ms | > 100ms |
| Taxa de sucesso | `(delivered/sent) * 100` | > 99% | < 99% |
| Throughput | `rate(messages_sent_total[1m])` | > 1000 msg/s | < 1000 msg/s |
| Circuit Breaker | `resilience4j_circuitbreaker_state` | 0 (Closed) | 1 (Open) |
| Rate Limiting | `available_permissions` | > 0 | 0 (exhausted) |

---

**Pronto para usar!** Execute `docker-compose -f docker-compose.monitoring.yml up -d` e acesse http://localhost:3000 🚀
