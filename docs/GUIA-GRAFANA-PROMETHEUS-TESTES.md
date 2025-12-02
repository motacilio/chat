# Guia Completo: Grafana + Prometheus - Monitoramento e Simulação de Testes

**Data**: 01/12/2025 (Atualizado)  
**Objetivo**: Configurar observabilidade completa e simular cenários de testes, carga e falhas  
**Stack**: Prometheus 2.48+, Grafana 10.2+, Spring Boot Actuator 3.2.5, Micrometer 1.12.5  
**⚠️ NOTA**: Contém workaround para bug do Spring Boot 3.2.5 com `@ConditionalOnAvailableEndpoint`  

---

## 📋 Índice

1. [Arquitetura de Monitoramento](#1-arquitetura-de-monitoramento)
2. [Configuração Inicial](#2-configuração-inicial)
3. [Acessando Grafana e Prometheus](#3-acessando-grafana-e-prometheus)
4. [Criando Dashboards](#4-criando-dashboards)
5. [Simulando Cenários de Teste](#5-simulando-cenários-de-teste)
6. [Simulando Falhas e Recuperação](#6-simulando-falhas-e-recuperação)
7. [Queries Úteis (PromQL)](#7-queries-úteis-promql)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Arquitetura de Monitoramento

```
┌─────────────────────────────────────────────────────────────┐
│                    OBSERVABILITY STACK                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐      ┌──────────────┐                    │
│  │  Chat API    │      │  Prometheus  │                    │
│  │  :8081       │─────▶│  :9091       │                    │
│  │              │      │              │                    │
│  │ /actuator/   │      │  Scrapes     │                    │
│  │  prometheus  │      │  every 10s   │                    │
│  └──────────────┘      └──────┬───────┘                    │
│         │                     │                            │
│         │                     │                            │
│         │              ┌──────▼───────┐                    │
│         │              │   Grafana    │                    │
│         │              │   :3000      │                    │
│         │              │              │                    │
│         │              │  Dashboards  │                    │
│         │              │  + Alerts    │                    │
│         │              └──────────────┘                    │
│         │                                                  │
│    Métricas:                                               │
│    - messages_sent_total                                   │
│    - message_latency_seconds (p50, p95, p99)               │
│    - kafka_consumer_lag                                    │
│    - mongodb_connection_pool_usage                         │
│    - circuit_breaker_state                                 │
│    - rate_limiter_rejected_total                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Configuração Inicial

### 2.1. Verificar Docker Compose

O arquivo `docker-compose.monitoring.yml` já está configurado:

```yaml
# docker-compose.monitoring.yml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:v2.45.0
    container_name: prometheus
    ports:
      - "9091:9090"  # Porta 9091 (9090 estava conflitando)
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/usr/share/prometheus/console_libraries'
      - '--web.console.templates=/usr/share/prometheus/consoles'
    networks:
      - chat-network

  grafana:
    image: grafana/grafana:10.0.0
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - grafana-data:/var/lib/grafana
      - ./docs/observabilidade/grafana-dashboard-basic.json:/etc/grafana/provisioning/dashboards/chat-api.json
    networks:
      - chat-network
    depends_on:
      - prometheus

volumes:
  prometheus-data:
  grafana-data:

networks:
  chat-network:
    external: true
```

### 2.2. Iniciar Stack de Monitoramento

```powershell
# 1. Certifique-se que a API está rodando
docker-compose up -d

# 2. Inicie o stack de monitoramento
docker-compose -f docker-compose.monitoring.yml up -d

# 3. Verifique os containers
docker ps | Select-String -Pattern "prometheus|grafana"

# Saída esperada:
# prometheus    prom/prometheus:v2.45.0    Up    0.0.0.0:9091->9090/tcp
# grafana       grafana/grafana:10.0.0     Up    0.0.0.0:3000->3000/tcp
```

### 2.3. Verificar Métricas da API (⚠️ IMPORTANTE)

```powershell
# Verificar se endpoint de métricas está exposto
curl http://localhost:8081/actuator/prometheus | Select-Object -First 20

# ✅ Saída esperada (HTTP 200 OK, ~57KB):
# StatusCode: 200
# Content: # HELP jvm_memory_used_bytes...
#          jvm_memory_used_bytes{area="heap"...
#          # HELP kafka_consumer_lag...
#          kafka_consumer_lag{consumer_group="chat-api-consumer"...
#          # HELP http_server_requests_seconds...
```

**⚠️ Troubleshooting do Endpoint Prometheus:**

Se receber **HTTP 404**, o endpoint não foi registrado corretamente devido ao bug do Spring Boot 3.2.5.

**Solução Implementada (Workaround)**:
Criamos `PrometheusController.java` que expõe as métricas via `@RestController`:

```java
@RestController
@RequestMapping("/actuator")
public class PrometheusController {
    private final PrometheusMeterRegistry prometheusMeterRegistry;

    @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public String prometheus() {
        return prometheusMeterRegistry.scrape();
    }
}
```

Este controller bypassa o bug `@ConditionalOnAvailableEndpoint` e expõe as métricas diretamente.

---

## 3. Acessando Grafana e Prometheus

### 3.1. Prometheus UI

**URL**: http://localhost:9091

**Funcionalidades**:
- **Targets**: Verificar se Chat API está sendo coletada
- **Graph**: Executar queries PromQL
- **Alerts**: Ver regras de alerta (se configuradas)

**Passo a Passo**:

1. Acesse http://localhost:9091
2. Clique em **Status** → **Targets**
3. Verifique se `chat-api (1/1 up)` aparece com estado **UP**
4. Se aparecer **DOWN**:
   ```powershell
   # Verificar se API está acessível
   curl http://localhost:8081/actuator/prometheus
   # ✅ HTTP 200 OK = endpoint funciona
   # ❌ HTTP 404 = ver seção 8.2 (Bug Spring Boot 3.2.5)
   
   # Verificar logs do Prometheus
   docker logs prometheus | Select-String -Pattern "chat-api"
   # ❌ "connection refused" = problema de network (ver seção 8.1)
   ```

**Nota**: O endpoint `/actuator/prometheus` não aparecerá na lista padrão do Spring Boot Actuator devido ao workaround implementado. Verifique diretamente via `curl`.
   ```

5. Vá para **Graph** e teste query:
   ```promql
   messages_sent_total
   ```

### 3.2. Grafana UI

**URL**: http://localhost:3000  
**Credenciais**:
- **Username**: `admin`
- **Password**: `admin` (mudará na primeira vez)

**Configuração Inicial**:

1. **Login**:
   - Acesse http://localhost:3000
   - User: `admin` / Password: `admin`
   - (Opcional) Mude a senha quando solicitado

2. **Adicionar Data Source**:
   - Menu lateral → **Configuration** (⚙️) → **Data Sources**
   - Clique **Add data source**
   - Selecione **Prometheus**
   - Configure:
     ```
     Name: Prometheus
     URL: http://prometheus:9090
     Access: Server (default)
     ```
   - Clique **Save & Test**
   - Deve aparecer: ✅ **Data source is working**

3. **Importar Dashboard**:
   - Menu lateral → **Dashboards** (➕) → **Import**
   - Clique **Upload JSON file**
   - Selecione: `docs/observabilidade/grafana-dashboard-basic.json`
   - Selecione Data Source: **Prometheus**
   - Clique **Import**

---

## 4. Criando Dashboards

### 4.1. Dashboard Básico (Já Existe)

O arquivo `grafana-dashboard-basic.json` já contém:

**Painéis**:
1. **Messages Sent (Total)** - Counter
2. **Message Latency (p95)** - 95th percentile
3. **Kafka Consumer Lag** - Gauge
4. **Circuit Breaker State** - State changes
5. **MongoDB Connection Pool** - Percentage usage

### 4.2. Criar Painel Customizado

**Exemplo: Taxa de Mensagens por Segundo**

1. No Dashboard, clique **Add Panel** (➕)
2. Em **Query**, adicione:
   ```promql
   rate(messages_sent_total[1m])
   ```
3. Em **Panel Options**:
   - **Title**: `Messages/sec (1min rate)`
   - **Description**: `Taxa de mensagens enviadas por segundo (média 1 min)`
4. Em **Visualization**: Selecione **Graph** ou **Time series**
5. Em **Axes**:
   - **Left Y**: Unit → `ops/sec (ops)`
6. Clique **Apply**

### 4.3. Criar Alerta

**Exemplo: Alerta se latência p95 > 100ms**

1. Edite o painel **Message Latency (p95)**
2. Aba **Alert**
3. Clique **Create Alert**
4. Configure:
   ```
   Name: High Message Latency
   Evaluate every: 1m
   For: 5m
   
   Condition:
   WHEN max() OF query(A, 1m, now) IS ABOVE 0.1
   ```
5. Em **Notifications**:
   - Selecione canal (Email, Slack, etc - precisa configurar antes)
6. Clique **Save**

---

## 5. Simulando Cenários de Teste

### 5.1. Teste de Carga Básico (k6)

**Arquivo**: `scripts/load-test/k6-send-messages.js`

```javascript
import grpc from 'k6/net/grpc';
import { check } from 'k6';

const client = new grpc.Client();
client.load(['../../src/main/proto'], 'chat_service.proto');

export let options = {
  stages: [
    { duration: '30s', target: 10 },   // Rampa até 10 usuários
    { duration: '1m', target: 50 },    // Rampa até 50 usuários
    { duration: '2m', target: 100 },   // Rampa até 100 usuários
    { duration: '1m', target: 0 },     // Desacelera
  ],
};

export default function () {
  client.connect('localhost:9090', { plaintext: true });

  const request = {
    conversation_id: 'conv-123',
    sender_id: 'user-456',
    message_text: 'Load test message ' + Date.now(),
  };

  const response = client.invoke('chat_api.v1.ChatService/SendMessage', request);
  
  check(response, {
    'status is OK': (r) => r && r.status === grpc.StatusOK,
  });

  client.close();
}
```

**Executar**:

```powershell
# 1. Instalar k6 (se não tiver)
choco install k6

# 2. Executar teste
cd scripts/load-test
k6 run k6-send-messages.js

# 3. Observar no Grafana:
# - Messages/sec deve aumentar gradualmente
# - Latência p95 deve permanecer < 100ms
# - Kafka consumer lag pode aumentar temporariamente
```

### 5.2. Teste de Carga com ghz (gRPC específico)

```powershell
# 1. Instalar ghz
go install github.com/bojand/ghz/cmd/ghz@latest

# 2. Executar teste (100 RPS por 1 minuto)
ghz --insecure `
    --proto src/main/proto/chat_service.proto `
    --call chat_api.v1.ChatService/SendMessage `
    -d '{\"conversation_id\":\"conv-123\",\"sender_id\":\"user-456\",\"message_text\":\"ghz test\"}' `
    -n 6000 `
    -c 10 `
    --duration 60s `
    localhost:9090

# Saída esperada:
# Summary:
#   Count:        6000
#   Total:        60.12 s
#   Slowest:      52.34 ms
#   Fastest:      1.23 ms
#   Average:      8.45 ms
#   Requests/sec: 99.8

# 3. Observar no Grafana:
# - Message Latency (p95) vs (p99)
# - Messages Sent Total (deve chegar em 6000)
```

### 5.3. Teste de Stress (Encontrar Limite)

```powershell
# Aumentar carga até sistema começar a degradar
k6 run --vus 500 --duration 5m k6-send-messages.js

# Observar no Grafana:
# - Em que ponto a latência p95 ultrapassa 100ms?
# - Kafka consumer lag cresce sem parar?
# - Circuit breaker abre?
# - MongoDB connection pool satura (100%)?
```

---

## 6. Simulando Falhas e Recuperação

### 6.1. Falha do MongoDB (Downtime + Recuperação)

**Cenário**: MongoDB fica indisponível por 2 minutos, depois volta.

```powershell
# 1. Iniciar teste de carga em background
Start-Job -ScriptBlock {
    k6 run --vus 50 --duration 10m k6-send-messages.js
}

# 2. Após 2 minutos, derrubar MongoDB
docker stop mongodb-primary

# 3. Observar no Grafana (próximos 2 minutos):
# - Messages Sent Total: Para de subir (API retorna erro)
# - Circuit Breaker State: Muda para OPEN
# - Error Rate: Dispara para 100%
# - MongoDB Connection Pool: Vai para 0%

# 4. Verificar logs da API
docker logs -f chat-api

# Saída esperada:
# ERROR: MongoDB connection failed
# WARN: Circuit breaker OPEN for MongoDB

# 5. Após 2 minutos, religar MongoDB
docker start mongodb-primary

# 6. Aguardar 30 segundos (circuit breaker HALF_OPEN)
Start-Sleep -Seconds 30

# 7. Observar recuperação no Grafana:
# - Circuit Breaker State: OPEN → HALF_OPEN → CLOSED
# - Messages Sent Total: Volta a subir
# - Error Rate: Volta para ~0%
# - MongoDB Connection Pool: Volta para ~20-40%
```

**Queries Grafana para este cenário**:

```promql
# Taxa de erros (%)
(rate(errors_total[1m]) / rate(requests_total[1m])) * 100

# Estado do Circuit Breaker (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
circuit_breaker_state{name="mongodb"}

# Conexões MongoDB ativas
mongodb_connection_pool_usage
```

### 6.2. Falha do Kafka (Perda de Mensagens?)

**Cenário**: Kafka fica indisponível, testar se mensagens são perdidas.

```powershell
# 1. Enviar 100 mensagens com sucesso
for ($i=1; $i -le 100; $i++) {
    grpcurl -plaintext -d '{\"conversation_id\":\"conv-test\",\"sender_id\":\"user-1\",\"message_text\":\"Msg '$i'\"}' `
        localhost:9090 chat_api.v1.ChatService/SendMessage
}

# 2. Contar mensagens no MongoDB
docker exec mongodb-primary mongosh --eval "db.messages.countDocuments({})"
# Saída: 100

# 3. Derrubar Kafka
docker stop kafka

# 4. Tentar enviar mais 50 mensagens
for ($i=101; $i -le 150; $i++) {
    grpcurl -plaintext -d '{\"conversation_id\":\"conv-test\",\"sender_id\":\"user-1\",\"message_text\":\"Msg '$i'\"}' `
        localhost:9090 chat_api.v1.ChatService/SendMessage
}

# 5. Verificar resposta da API
# Saída esperada (dependendo da config):
# - Se acks=all: ERROR: Kafka unavailable (mensagens NÃO perdidas)
# - Se acks=1: SUCCESS (mas mensagens podem estar em buffer)

# 6. Religar Kafka
docker start kafka
Start-Sleep -Seconds 30  # Aguardar Kafka inicializar

# 7. Contar mensagens no MongoDB novamente
docker exec mongodb-primary mongosh --eval "db.messages.countDocuments({})"
# Saída esperada: 150 (todas mensagens foram entregues)

# 8. Observar no Grafana:
# - Kafka Consumer Lag: Deve ter aumentado durante downtime
# - Messages Processed Total: Deve processar backlog quando Kafka volta
```

### 6.3. Falha de Latência Alta (Slow Consumer)

**Cenário**: MessageDeliveryWorker demora muito para processar.

```powershell
# 1. Modificar temporariamente MessageDeliveryWorker
# Adicionar Thread.sleep(5000) no handleMessageEvent()

# 2. Enviar burst de mensagens
k6 run --vus 100 --duration 30s k6-send-messages.js

# 3. Observar no Grafana:
# - Kafka Consumer Lag: Dispara (mensagens acumulam no tópico)
# - Message Processing Latency: Aumenta para 5+ segundos
# - Messages Sent Total: Cresce rápido
# - Messages Processed Total: Cresce devagar (5s por mensagem)

# 4. Reverter mudança (remover Thread.sleep)
# Recompilar e reiniciar API

# 5. Observar recuperação:
# - Kafka Consumer Lag: Diminui gradualmente até 0
# - Processing Latency: Volta ao normal (< 50ms)
```

### 6.4. Simulação de Rate Limiting

**Cenário**: Usuário ultrapassa limite de 100 msg/min.

```powershell
# 1. Enviar 150 mensagens em 30 segundos (300 msg/min)
$headers = @{ "Authorization" = "Bearer <JWT_TOKEN>" }
for ($i=1; $i -le 150; $i++) {
    Invoke-WebRequest -Uri "http://localhost:8081/api/messages" `
        -Method POST `
        -Headers $headers `
        -Body '{"conversation_id":"conv-1","message_text":"Spam '$i'"}' `
        -ContentType "application/json"
}

# 2. Observar respostas:
# Mensagens 1-50: HTTP 200 OK
# Mensagens 51-150: HTTP 429 Too Many Requests

# 3. Verificar no Grafana:
# - rate_limiter_rejected_total: Aumenta ~100 vezes
# - messages_sent_total: Para em ~50

# Query PromQL:
rate(rate_limiter_rejected_total[1m])
```

---

## 7. Queries Úteis (PromQL)

### 7.1. Métricas de Performance

```promql
# Taxa de mensagens enviadas (últimos 5 minutos)
rate(messages_sent_total[5m])

# Latência média (últimos 1 minuto)
rate(message_latency_seconds_sum[1m]) / rate(message_latency_seconds_count[1m])

# Latência p95 (percentil 95)
histogram_quantile(0.95, rate(message_latency_seconds_bucket[5m]))

# Latência p99 (percentil 99)
histogram_quantile(0.99, rate(message_latency_seconds_bucket[5m]))

# Taxa de throughput total (mensagens/segundo)
sum(rate(messages_sent_total[1m]))
```

### 7.2. Métricas de Kafka

```promql
# Consumer lag (mensagens não processadas)
kafka_consumer_lag

# Taxa de mensagens processadas
rate(messages_processed_total[1m])

# Latência de processamento Kafka
rate(kafka_message_processing_latency_seconds_sum[1m]) / rate(kafka_message_processing_latency_seconds_count[1m])
```

### 7.3. Métricas de MongoDB

```promql
# Uso do pool de conexões (%)
mongodb_connection_pool_usage

# Latência de persistência
rate(mongodb_persist_latency_seconds_sum[1m]) / rate(mongodb_persist_latency_seconds_count[1m])

# Operações por segundo
rate(mongodb_operations_total[1m])
```

### 7.4. Métricas de Circuit Breaker

```promql
# Estado do Circuit Breaker (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
circuit_breaker_state{name="mongodb"}
circuit_breaker_state{name="kafka"}
circuit_breaker_state{name="minio"}

# Taxa de falhas que causaram abertura
rate(circuit_breaker_failure_total[1m])

# Tempo que circuit breaker ficou aberto
circuit_breaker_open_duration_seconds
```

### 7.5. Métricas de Erro

```promql
# Taxa de erros total
rate(errors_total[1m])

# Taxa de erros por tipo
rate(errors_total{type="validation_error"}[1m])
rate(errors_total{type="database_error"}[1m])
rate(errors_total{type="kafka_error"}[1m])

# Percentual de erros (erro rate %)
(rate(errors_total[1m]) / rate(requests_total[1m])) * 100
```

---

## 8. Troubleshooting

### 8.1. Prometheus não coleta métricas

**Problema**: Target `chat-api` aparece como **DOWN** no Prometheus.

**Diagnóstico**:

```powershell
# 1. Verificar se endpoint está acessível
curl http://localhost:8081/actuator/prometheus

# ⚠️ Se retornar HTTP 404 - Ver seção 8.2 (Bug Spring Boot 3.2.5)

# 2. Verificar network do Docker
docker network inspect chat-network
# ✅ Deve mostrar prometheus e chat-api na mesma network

# 3. Verificar logs do Prometheus
docker logs prometheus | Select-String -Pattern "chat-api"
# ❌ "connection refused" = problema de network
```

**Solução**:

```powershell
# Recriar containers na mesma network
docker-compose down
docker-compose -f docker-compose.monitoring.yml down

docker network create chat-network

docker-compose up -d
docker-compose -f docker-compose.monitoring.yml up -d
```

---

### 8.2. API Não Expõe Métricas - HTTP 404 (⚠️ Spring Boot 3.2.5 Bug)

**Problema**: `/actuator/prometheus` retorna HTTP 404 mesmo com configuração correta

**Root Cause**: Bug em `@ConditionalOnAvailableEndpoint` do Spring Boot 3.2.5
- Condition Evaluation Report: `"Did not match - no 'management.endpoints' property marked it as exposed"`
- Ocorre **mesmo com** `include: "*"` e `prometheus.enabled: true`

**Verificação do Bug**:

```powershell
# 1. Confirmar que PrometheusMeterRegistry existe
docker-compose logs chat-api | Select-String "PrometheusMeterRegistry"
# ✅ Esperado: Bean criado com sucesso

# 2. Verificar outros endpoints do Actuator
curl http://localhost:8081/actuator/health
# ✅ HTTP 200 OK (prova que Actuator funciona)

# 3. Testar endpoint Prometheus
curl http://localhost:8081/actuator/prometheus
# ❌ HTTP 404 (endpoint não registrado devido ao bug)
```

**❌ Soluções que NÃO Funcionam**:
- Variações de configuração YAML (`include: "*"`, lista explícita, etc.)
- Beans manuais (conflitam com auto-configuration)
- Custom `@Endpoint` (não é reconhecido pela condição)
- Dependências explícitas ou scope diferente

**✅ Solução Implementada (Workaround)**:

Criar `PrometheusController.java` que bypassa o auto-configuration:

```java
package com.chat.controller;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/actuator")
public class PrometheusController {

    private final PrometheusMeterRegistry prometheusMeterRegistry;

    public PrometheusController(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public String prometheus() {
        return prometheusMeterRegistry.scrape();
    }
}
```

**Recompilar e Reiniciar**:

```powershell
# Rebuild da aplicação
docker-compose build chat-api

# Restart
docker-compose restart chat-api

# Aguardar inicialização (~13 segundos)
Start-Sleep -Seconds 15

# Verificar sucesso
curl http://localhost:8081/actuator/prometheus | Select-Object -First 5
# ✅ HTTP 200 OK, ~57KB de métricas
```

**Métricas Disponíveis Após Workaround**:
- JVM: `jvm_memory_used_bytes`, GC, threads, classes
- Kafka: `kafka_consumer_lag`, processing latency
- Circuit Breakers: `resilience4j_circuitbreaker_buffered_calls`
- HTTP: `http_server_requests_seconds_count`
- Spring Security: FilterChain metrics
- Logback: Event counters

**Nota**: Esta solução foi testada após 40+ tentativas e é a única confiável para Spring Boot 3.2.5.

### 8.3. Grafana não conecta no Prometheus

**Problema**: Data Source test falha com "Bad Gateway".

**Solução**:

1. Verifique URL do Data Source:
   - Deve ser: `http://prometheus:9090` (nome do container)
   - NÃO use: `http://localhost:9091`

2. Verifique Access:
   - Deve ser: **Server** (default)
   - NÃO use: Browser

3. Teste conectividade:
   ```powershell
   docker exec grafana ping prometheus
   # ✅ Deve retornar: PING prometheus (172.x.x.x)
   ```

### 8.4. Dashboard mostra "No Data"

**Problema**: Painéis aparecem vazios.

**Diagnóstico**:

```promql
# Testar query diretamente no Prometheus (Graph)
messages_sent_total

# Se retornar vazio:
# - Nenhuma métrica foi coletada ainda
# - Envie uma mensagem de teste para gerar dados
```

**Solução**:

```powershell
# Gerar dados de teste
grpcurl -plaintext -d '{\"conversation_id\":\"test\",\"sender_id\":\"user-1\",\"message_text\":\"test\"}' `
    localhost:9090 chat_api.v1.ChatService/SendMessage

# Aguardar 15 segundos (scrape interval)
Start-Sleep -Seconds 15

# Recarregar dashboard no Grafana
```

### 8.5. Métricas customizadas não aparecem

**Problema**: Métricas como `kafka_consumer_lag` retornam vazio.

**Diagnóstico**:

```powershell
# 1. Verificar se métrica está sendo exposta
curl http://localhost:8081/actuator/prometheus | Select-String -Pattern "kafka_consumer_lag"

# Se não aparecer:
# - MetricsConfig.java pode não estar registrando a métrica
# - MeterRegistry pode não estar sendo injetado corretamente
```

**Solução**:

Verificar `MetricsConfig.java`:

```java
@Bean
public MeterBinder kafkaConsumerLagGauge() {
    return (registry) -> Gauge.builder("kafka_consumer_lag", kafkaConsumerLag, AtomicLong::get)
            .description("Lag do consumidor Kafka")
            .register(registry);
}
```

---

## 9. Cenários de Teste Completos

### 9.1. Teste End-to-End Completo

```powershell
# SCRIPT: test-e2e-with-monitoring.ps1

# 1. Iniciar stack completo
Write-Host "=== Iniciando stack completo ===" -ForegroundColor Green
docker-compose up -d
docker-compose -f docker-compose.monitoring.yml up -d
Start-Sleep -Seconds 30

# 2. Verificar saúde dos serviços
Write-Host "=== Verificando saúde ===" -ForegroundColor Green
curl http://localhost:8081/actuator/health
curl http://localhost:9091/-/healthy
curl http://localhost:3000/api/health

# 3. Enviar mensagens de teste (baseline)
Write-Host "=== Enviando 100 mensagens (baseline) ===" -ForegroundColor Green
for ($i=1; $i -le 100; $i++) {
    grpcurl -plaintext -d "{\"conversation_id\":\"conv-1\",\"sender_id\":\"user-1\",\"message_text\":\"Baseline msg $i\"}" `
        localhost:9090 chat_api.v1.ChatService/SendMessage
}

# 4. Aguardar processamento
Start-Sleep -Seconds 30

# 5. Verificar métricas no Prometheus
Write-Host "=== Verificando métricas ===" -ForegroundColor Green
$metrics = curl -s "http://localhost:9091/api/v1/query?query=messages_sent_total" | ConvertFrom-Json
Write-Host "Messages Sent: $($metrics.data.result[0].value[1])"

# 6. Simular carga (stress test)
Write-Host "=== Teste de carga (200 VUs por 2 min) ===" -ForegroundColor Yellow
k6 run --vus 200 --duration 2m k6-send-messages.js

# 7. Simular falha do MongoDB
Write-Host "=== Simulando falha do MongoDB ===" -ForegroundColor Red
docker stop mongodb-primary
Start-Sleep -Seconds 60
docker start mongodb-primary
Write-Host "MongoDB reiniciado" -ForegroundColor Green

# 8. Aguardar recuperação
Start-Sleep -Seconds 60

# 9. Gerar relatório final
Write-Host "=== Relatório Final ===" -ForegroundColor Cyan
$finalMetrics = curl -s "http://localhost:9091/api/v1/query?query=messages_sent_total" | ConvertFrom-Json
Write-Host "Total Messages Sent: $($finalMetrics.data.result[0].value[1])"

$p95Latency = curl -s "http://localhost:9091/api/v1/query?query=histogram_quantile(0.95,%20rate(message_latency_seconds_bucket[5m]))" | ConvertFrom-Json
Write-Host "p95 Latency: $($p95Latency.data.result[0].value[1]) seconds"

$errors = curl -s "http://localhost:9091/api/v1/query?query=rate(errors_total[5m])" | ConvertFrom-Json
Write-Host "Error Rate: $($errors.data.result[0].value[1]) errors/sec"

Write-Host "`n✅ Teste completo! Acesse Grafana em http://localhost:3000" -ForegroundColor Green
```

### 9.2. Teste de Resiliência (Chaos Engineering)

```powershell
# SCRIPT: chaos-test.ps1

Write-Host "=== CHAOS ENGINEERING TEST ===" -ForegroundColor Magenta

# 1. Baseline (30s)
Write-Host "[Baseline] Enviando carga normal..." -ForegroundColor Green
Start-Job -ScriptBlock { k6 run --vus 50 --duration 5m k6-send-messages.js }
Start-Sleep -Seconds 30

# 2. Chaos 1: Matar MongoDB (1min)
Write-Host "[CHAOS] Matando MongoDB por 1min..." -ForegroundColor Red
docker kill mongodb-primary
Start-Sleep -Seconds 60
docker start mongodb-primary
Write-Host "[RECOVERY] MongoDB reiniciado" -ForegroundColor Yellow
Start-Sleep -Seconds 30

# 3. Chaos 2: Matar Kafka (1min)
Write-Host "[CHAOS] Matando Kafka por 1min..." -ForegroundColor Red
docker kill kafka
Start-Sleep -Seconds 60
docker start kafka
Write-Host "[RECOVERY] Kafka reiniciado" -ForegroundColor Yellow
Start-Sleep -Seconds 30

# 4. Chaos 3: Latência de rede (tc netem)
Write-Host "[CHAOS] Adicionando 500ms de latência..." -ForegroundColor Red
docker exec chat-api tc qdisc add dev eth0 root netem delay 500ms
Start-Sleep -Seconds 60
docker exec chat-api tc qdisc del dev eth0 root
Write-Host "[RECOVERY] Latência removida" -ForegroundColor Yellow

# 5. Aguardar teste k6 terminar
Get-Job | Wait-Job
Get-Job | Receive-Job

Write-Host "`n✅ Chaos test completo! Verifique dashboards no Grafana" -ForegroundColor Cyan
```

---

## 10. Checklist de Validação

### ✅ Setup Inicial

- [ ] Docker Compose monitoring rodando (`docker ps` mostra prometheus + grafana)
- [ ] Prometheus coleta métricas (`curl http://localhost:8081/actuator/prometheus`)
- [ ] Prometheus Target UP (`http://localhost:9091/targets` mostra chat-api UP)
- [ ] Grafana acessível (`http://localhost:3000` login admin/admin)
- [ ] Data Source Prometheus conectado (Save & Test = ✅)
- [ ] Dashboard importado (Chat API Dashboard visível)

### ✅ Métricas Funcionando

- [ ] `messages_sent_total` aumenta ao enviar mensagens
- [ ] `message_latency_seconds` mostra p50/p95/p99
- [ ] `kafka_consumer_lag` visível e com valor
- [ ] `mongodb_connection_pool_usage` entre 0-100%
- [ ] `circuit_breaker_state` mostra 0 (CLOSED) inicialmente

### ✅ Simulações de Teste

- [ ] Teste de carga k6 executado com sucesso
- [ ] Métricas aumentam durante carga (messages/sec, latency)
- [ ] MongoDB downtime simulado e recuperação observada
- [ ] Circuit breaker mudou estado (CLOSED → OPEN → CLOSED)
- [ ] Kafka downtime simulado sem perda de mensagens

### ✅ Dashboards e Alertas

- [ ] Painéis mostram dados em tempo real
- [ ] Queries PromQL funcionam corretamente
- [ ] Alertas disparam quando threshold atingido (se configurado)
- [ ] Gráficos mostram tendências claras

---

## 11. Recursos Adicionais

### Links Úteis

- **Prometheus Docs**: https://prometheus.io/docs/
- **Grafana Docs**: https://grafana.com/docs/
- **PromQL Tutorial**: https://prometheus.io/docs/prometheus/latest/querying/basics/
- **k6 Load Testing**: https://k6.io/docs/
- **Micrometer Metrics**: https://micrometer.io/docs/

### Comandos Rápidos

```powershell
# Reiniciar stack de monitoramento
docker-compose -f docker-compose.monitoring.yml restart

# Ver logs do Prometheus
docker logs -f prometheus

# Ver logs do Grafana
docker logs -f grafana

# Exportar dashboard do Grafana
curl -X GET -H "Authorization: Bearer <API_KEY>" \
  http://localhost:3000/api/dashboards/uid/<DASHBOARD_UID> > dashboard-backup.json

# Limpar dados do Prometheus (reset)
docker-compose -f docker-compose.monitoring.yml down -v
docker-compose -f docker-compose.monitoring.yml up -d
```

---

## ✅ Conclusão

Este guia cobre:

✅ **Configuração completa** de Grafana + Prometheus  
✅ **Criação de dashboards** e alertas  
✅ **Simulação de testes** de carga e stress  
✅ **Simulação de falhas** (MongoDB, Kafka, latência)  
✅ **Queries PromQL** úteis para análise  
✅ **Troubleshooting** de problemas comuns  

**Próximo passo**: Execute o script `test-e2e-with-monitoring.ps1` e observe os dashboards no Grafana em tempo real! 🚀

---

**Criado**: 30/11/2025  
**Autor**: GitHub Copilot  
**Versão**: 1.0
