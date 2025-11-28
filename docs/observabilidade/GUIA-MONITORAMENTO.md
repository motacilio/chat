# Guia de Monitoramento - Prometheus e Grafana

**Data**: 27 de Novembro de 2025  
**Autor**: Chat API Team  
**Objetivo**: Guia completo para configurar e usar a stack de monitoramento (Prometheus + Grafana)

---

## 📋 Visão Geral

A stack de monitoramento implementa observabilidade completa da Chat API usando:

- **Prometheus**: Coleta e armazenamento de métricas time-series
- **Grafana**: Visualização de dashboards e alertas
- **Micrometer**: Biblioteca de instrumentação de métricas (Spring Boot)
- **Spring Boot Actuator**: Endpoints de health e métricas

---

## 🚀 Início Rápido

### Passo 1: Iniciar Stack de Monitoramento

```powershell
# Certifique-se de que a infraestrutura básica está rodando
docker-compose up -d

# Iniciar Prometheus e Grafana
docker-compose -f docker-compose.monitoring.yml up -d

# Verificar containers
docker ps --filter "name=prometheus" --filter "name=grafana"
```

### Passo 2: Compilar e Iniciar Chat API

```powershell
# Compilar com métricas habilitadas
mvn clean package -DskipTests

# Iniciar aplicação
java -jar target/meu-projeto-chat-1.0.0-SNAPSHOT.jar
```

### Passo 3: Acessar Interfaces

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (usuário: `admin`, senha: `admin`)
- **Métricas da API**: http://localhost:8081/actuator/prometheus

---

## 📊 Métricas Implementadas

### Métricas de Mensagens

| Métrica | Tipo | Descrição | Tags |
|---------|------|-----------|------|
| `messages_sent_total` | Counter | Total de mensagens enviadas | `type` (text/file) |
| `messages_validated_total` | Counter | Total de mensagens validadas | - |
| `messages_processed_total` | Counter | Mensagens processadas por status | `status` (SENT/DELIVERED/READ) |
| `messages_persisted_total` | Counter | Mensagens persistidas no MongoDB | - |
| `idempotent_requests_total` | Counter | Requisições idempotentes detectadas | - |

### Métricas de Latência (Timers/Histograms)

| Métrica | Tipo | Descrição | Percentis |
|---------|------|-----------|-----------|
| `message_latency_seconds` | Timer | Latência de processamento de mensagens | p50, p95, p99 |
| `message_validation_latency_seconds` | Timer | Latência de validação | p50, p95, p99 |
| `kafka_message_processing_latency_seconds` | Timer | Latência de processamento Kafka | p50, p95, p99 |
| `mongodb_persist_latency_seconds` | Timer | Latência de persistência MongoDB | p50, p95, p99 |
| `file_upload_latency_seconds` | Timer | Latência de upload de arquivos | p50, p95, p99 |
| `platform_delivery_latency_ms` | Timer | Latência de entrega por plataforma | p50, p95, p99 |

### Métricas de Infraestrutura (Gauges)

| Métrica | Tipo | Descrição | Range |
|---------|------|-----------|-------|
| `kafka_consumer_lag` | Gauge | Lag do consumidor Kafka (mensagens pendentes) | 0-N |
| `mongodb_connection_pool_usage` | Gauge | Uso do connection pool do MongoDB (%) | 0-100 |

### Métricas de Erros

| Métrica | Tipo | Descrição | Tags |
|---------|------|-----------|------|
| `errors_total` | Counter | Total de erros por tipo | `error_type`, `endpoint` |

---

## 📈 Dashboards Grafana

### Dashboard Básico: Chat API - Monitoramento de Mensagens

**Arquivo**: `docs/observabilidade/grafana-dashboard-basic.json`  
**UID**: `chat-api-basic`

#### Painéis Incluídos:

1. **Taxa de Mensagens (Messages/Second)**
   - Gráfico de linha mostrando mensagens enviadas/s e persistidas/s
   - Útil para monitorar throughput do sistema

2. **Latência de Processamento (p50/p95/p99)**
   - Percentis de latência de processamento de mensagens
   - Threshold: p95 < 100ms (linha amarela/vermelha)

3. **Kafka Consumer Lag** (Gauge)
   - Medidor mostrando lag do consumidor Kafka
   - Verde: < 1000, Amarelo: 1000-5000, Vermelho: > 5000

4. **MongoDB Connection Pool Usage** (Gauge)
   - Medidor mostrando uso do pool de conexões (%)
   - Verde: < 60%, Amarelo: 60-80%, Vermelho: > 80%

5. **Taxa de Erros (Errors/Second)**
   - Gráfico de linha mostrando erros por tipo
   - Identifica picos de erro

6. **Latência de Workers (Kafka & MongoDB)**
   - Compara latência de processamento Kafka vs persistência MongoDB
   - Ajuda a identificar gargalos

7. **Contadores Totais (Acumulados)**
   - Barras mostrando totais acumulados de mensagens

#### Como Importar Dashboard:

```
1. Acesse Grafana: http://localhost:3000
2. Login: admin/admin
3. Menu lateral → Dashboards → Import
4. Copie conteúdo de docs/observabilidade/grafana-dashboard-basic.json
5. Cole no campo JSON
6. Clique "Load" → "Import"
```

---

## 🔍 Queries Prometheus Úteis

### Taxa de Mensagens por Segundo

```promql
# Taxa de mensagens enviadas (últimos 5 minutos)
rate(messages_sent_total[5m])

# Taxa de mensagens processadas por status
rate(messages_processed_total{status="SENT"}[5m])
rate(messages_processed_total{status="DELIVERED"}[5m])
rate(messages_processed_total{status="READ"}[5m])
```

### Latência (Percentis)

```promql
# p95 de latência de mensagens (últimos 5 minutos)
histogram_quantile(0.95, rate(message_latency_seconds_bucket[5m]))

# p99 de latência de validação
histogram_quantile(0.99, rate(message_validation_latency_seconds_bucket[5m]))

# Latência média de upload de arquivo
rate(file_upload_latency_seconds_sum[5m]) / rate(file_upload_latency_seconds_count[5m])
```

### Infraestrutura

```promql
# Lag atual do consumidor Kafka
kafka_consumer_lag

# Uso do pool MongoDB (%)
mongodb_connection_pool_usage

# Taxa de erros por tipo
rate(errors_total[1m])
```

### Requisições Idempotentes

```promql
# Taxa de requisições duplicadas detectadas
rate(idempotent_requests_total[5m])

# Porcentagem de requisições idempotentes
(rate(idempotent_requests_total[5m]) / rate(messages_sent_total[5m])) * 100
```

---

## ⚠️ Alertas Recomendados

### Alerta 1: Latência Alta

```yaml
- alert: HighMessageLatency
  expr: histogram_quantile(0.95, rate(message_latency_seconds_bucket[5m])) > 0.1
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Latência p95 de mensagens acima de 100ms"
    description: "p95 = {{ $value }}s (threshold: 100ms)"
```

### Alerta 2: Taxa de Erros Alta

```yaml
- alert: HighErrorRate
  expr: rate(errors_total[1m]) > 10
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "Taxa de erros acima de 10 erros/segundo"
    description: "Taxa atual: {{ $value }} erros/s"
```

### Alerta 3: Kafka Consumer Lag Alto

```yaml
- alert: HighKafkaConsumerLag
  expr: kafka_consumer_lag > 1000
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Lag do consumidor Kafka alto"
    description: "Lag = {{ $value }} mensagens (threshold: 1000)"
```

### Alerta 4: Pool MongoDB Saturado

```yaml
- alert: MongoDBPoolSaturated
  expr: mongodb_connection_pool_usage > 80
  for: 3m
  labels:
    severity: critical
  annotations:
    summary: "Pool de conexões MongoDB acima de 80%"
    description: "Uso = {{ $value }}% (threshold: 80%)"
```

**Arquivo de Alertas**: Criar `prometheus-alerts.yml` e referenciar em `prometheus.yml`:

```yaml
rule_files:
  - "prometheus-alerts.yml"
```

---

## 🧪 Testes de Métricas

### Teste 1: Verificar Endpoint de Métricas

```powershell
# Verificar se endpoint está respondendo
Invoke-RestMethod -Uri "http://localhost:8081/actuator/prometheus" | Select-String "messages_sent_total"

# Saída esperada:
# messages_sent_total{...} 123.0
```

### Teste 2: Enviar Mensagens e Observar Métricas

```powershell
# Enviar 10 mensagens
for ($i=1; $i -le 10; $i++) {
    grpcurl -plaintext -d "{\"conversation_id\":\"test-conv\",\"sender_id\":\"alice\",\"message_text\":\"Teste $i\"}" localhost:9090 chat_api.v1.ChatService/SendMessage
    Start-Sleep -Milliseconds 500
}

# Verificar contador aumentou
Invoke-RestMethod -Uri "http://localhost:8081/actuator/prometheus" | Select-String "messages_sent_total"
```

### Teste 3: Validar Prometheus Scraping

```
1. Acesse Prometheus: http://localhost:9090
2. Vá em Status → Targets
3. Verifique se "chat-api" está UP e verde
4. Último scrape deve ser < 30s
```

### Teste 4: Validar Dashboard Grafana

```
1. Acesse Grafana: http://localhost:3000
2. Abra dashboard "Chat API - Monitoramento de Mensagens"
3. Envie mensagens via gRPC
4. Observe painéis atualizando em tempo real (refresh 10s)
```

---

## 🛠️ Troubleshooting

### Problema: Métricas não aparecem no Prometheus

**Causa**: Prometheus não consegue fazer scraping da API

**Solução**:
```powershell
# 1. Verificar se API está rodando
Invoke-RestMethod -Uri "http://localhost:8081/actuator/health"

# 2. Verificar se endpoint Prometheus está acessível
Invoke-RestMethod -Uri "http://localhost:8081/actuator/prometheus"

# 3. Verificar logs do Prometheus
docker logs prometheus

# 4. Verificar config do Prometheus
docker exec prometheus cat /etc/prometheus/prometheus.yml
```

### Problema: Dashboard Grafana sem dados

**Causa**: Data source Prometheus não configurado

**Solução**:
```
1. Grafana → Configuration → Data Sources
2. Add data source → Prometheus
3. URL: http://prometheus:9090
4. Save & Test
```

### Problema: Latência sempre 0

**Causa**: Métricas Timer não sendo registradas corretamente

**Solução**:
```powershell
# Verificar se histogram buckets estão aparecendo
Invoke-RestMethod -Uri "http://localhost:8081/actuator/prometheus" | Select-String "message_latency_seconds_bucket"

# Deve mostrar múltiplos buckets:
# message_latency_seconds_bucket{le="0.001",...} 0.0
# message_latency_seconds_bucket{le="0.01",...} 5.0
# message_latency_seconds_bucket{le="0.1",...} 50.0
```

### Problema: Consumer Lag não atualiza

**Causa**: Gauge não está sendo atualizado pelo código

**Solução**:
```java
// Adicionar no KafkaConsumer para atualizar lag
@Autowired
private MetricsConfig metricsConfig;

// Após consumir mensagem:
long currentOffset = ...;
long highWaterMark = ...;
long lag = highWaterMark - currentOffset;
metricsConfig.updateKafkaConsumerLag(lag);
```

---

## 📚 Referências

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Micrometer Documentation](https://micrometer.io/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

---

## ✅ Checklist de Implementação

- [X] Dependências Micrometer e Prometheus adicionadas ao `pom.xml`
- [X] `MetricsConfig.java` criado com métricas customizadas
- [X] Endpoint `/actuator/prometheus` configurado em `application.yml`
- [X] `prometheus.yml` criado com scrape configs
- [X] `docker-compose.monitoring.yml` criado com Prometheus e Grafana
- [X] `MessageService` instrumentado com Counters e Timers
- [X] `MessageDeliveryWorker` instrumentado com métricas de processamento
- [X] Dashboard Grafana básico criado (`grafana-dashboard-basic.json`)
- [ ] Alertas Prometheus configurados (opcional - Fase 1.2)
- [ ] Jaeger para distributed tracing (opcional - Fase 1.3)

---

**Próximos Passos**:
1. Testar stack de monitoramento com carga real
2. Criar alertas no Prometheus (prometheus-alerts.yml)
3. Implementar Jaeger para distributed tracing
4. Criar testes de carga com k6 (Fase 2)
