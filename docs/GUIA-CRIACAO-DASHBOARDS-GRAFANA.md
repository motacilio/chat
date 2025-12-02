# Guia Completo: Criação de Dashboards e Gráficos de Monitoramento

**Data**: 01/12/2025  
**Objetivo**: Tutorial passo a passo para criar dashboards profissionais no Grafana para monitoramento da Chat API  
**Nível**: Intermediário  

---

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Setup Inicial](#setup-inicial)
3. [Criando seu Primeiro Dashboard](#criando-seu-primeiro-dashboard)
4. [Queries PromQL por Categoria](#queries-promql-por-categoria)
5. [Tipos de Painéis e Quando Usar](#tipos-de-painéis-e-quando-usar)
6. [Dashboards Prontos](#dashboards-prontos)
7. [Alertas](#alertas)
8. [Dicas e Truques](#dicas-e-truques)

---

## Visão Geral

### O que você vai criar

Ao final deste guia, você terá dashboards profissionais como este:

```
┌────────────────────────────────────────────────────────────┐
│  Chat API - Performance Dashboard                   ⚙️ 🔄 │
├────────────────────────────────────────────────────────────┤
│  ┌──────────────────────┬──────────────────────┐          │
│  │  Messages/sec        │  Latência p95        │          │
│  │  ✅ 1,234 msg/s      │  ⚠️ 95ms             │          │
│  │  [Time series ~~~~]  │  [Time series ~~~~]  │          │
│  └──────────────────────┴──────────────────────┘          │
│  ┌──────────────────────┬──────────────────────┐          │
│  │  Kafka Lag           │  MongoDB Pool        │          │
│  │   [Gauge ◉ 2.5K]     │   [Gauge ◉ 65%]     │          │
│  │   ⚠️ Warning         │   ✅ OK              │          │
│  └──────────────────────┴──────────────────────┘          │
│  ┌──────────────────────────────────────────────┐         │
│  │  Latência por Percentil                      │         │
│  │  [3 linhas: p50, p95, p99]                   │         │
│  └──────────────────────────────────────────────┘         │
└────────────────────────────────────────────────────────────┘
```

### Pré-requisitos

- ✅ Docker e Docker Compose instalados
- ✅ Sistema Chat API rodando
- ✅ Prometheus e Grafana configurados (ver seção Setup)

---

## Setup Inicial

### 1. Iniciar Stack Completo

```powershell
# Terminal PowerShell

# 1. Subir aplicação principal
cd c:\Users\marcos.pereira\Desktop\programacao\java\chat\chat
docker-compose up -d

# 2. Aguardar MongoDB replica set (30 segundos)
Start-Sleep -Seconds 30

# 3. Subir Prometheus e Grafana
docker-compose -f docker-compose.monitoring.yml up -d

# 4. Verificar tudo está rodando
docker ps
```

**Saída esperada**:
```
chat-api       Up    0.0.0.0:8081->8081/tcp, 0.0.0.0:9090->9090/tcp
prometheus     Up    0.0.0.0:9091->9090/tcp
grafana        Up    0.0.0.0:3000->3000/tcp
mongodb        Up    ...
kafka          Up    ...
minio          Up    ...
```

### 2. Verificar Métricas Estão Sendo Coletadas

```powershell
# Testar endpoint de métricas da API
curl http://localhost:8081/actuator/prometheus | Select-Object -First 10

# ✅ Saída esperada:
# # HELP jvm_memory_used_bytes The amount of used memory
# jvm_memory_used_bytes{area="heap",id="G1 Eden Space",} 1.234567E7
# ...
```

Se retornar **HTTP 404**, ver seção [Troubleshooting - Endpoint 404](#troubleshooting).

### 3. Verificar Prometheus Está Coletando

1. Acesse: http://localhost:9091
2. Clique em **Status** → **Targets**
3. Verifique: `chat-api (1/1 up)` está **UP** (verde)

Se aparecer **DOWN** (vermelho), ver [Troubleshooting - Target Down](#troubleshooting).

### 4. Configurar Grafana (Primeira Vez)

#### Login

1. Acesse: http://localhost:3000
2. **Username**: `admin`
3. **Password**: `admin`
4. Sistema pedirá nova senha → Escolha uma ou clique **"Skip"**

#### Adicionar Data Source Prometheus

1. **Menu lateral esquerdo** → ⚙️ **Configuration** → **Data sources**
2. Clique em **"Add data source"**
3. Selecione **"Prometheus"**
4. Configure:
   - **Name**: `Prometheus`
   - **URL**: `http://prometheus:9090` ⚠️ **Importante**: Use o nome do container
   - **Access**: `Server (default)`
5. Clique em **"Save & Test"**
6. ✅ Deve aparecer: **"Data source is working"**

---

## Criando seu Primeiro Dashboard

### Passo 1: Criar Dashboard Vazio

1. **Menu lateral** → ➕ **Create** → **Dashboard**
2. Clique em **"Add visualization"**
3. Selecione Data Source: **Prometheus**

### Passo 2: Primeiro Painel - Taxa de Mensagens

Vamos criar um gráfico mostrando quantas mensagens são enviadas por segundo.

#### Query PromQL

No campo **"Metric"**, insira:

```promql
rate(messages_sent_total[1m])
```

**Explicação**:
- `messages_sent_total`: Métrica counter que conta mensagens enviadas
- `rate(...[1m])`: Calcula taxa por segundo nos últimos 1 minuto
- Resultado: Mensagens/segundo

#### Configurar Painel

**1. Panel Options** (aba da direita):

- **Title**: `Messages per Second`
- **Description**: `Taxa de mensagens enviadas nos últimos 60 segundos`

**2. Visualization**:

- Tipo: **Time series** (gráfico de linha)

**3. Axes** (eixos):

- **Left Y-Axis**:
  - **Unit**: `short` (número inteiro)
  - **Min**: `0` (começa do zero)
  - **Decimals**: `2` (duas casas decimais)
  - **Label**: `Messages/sec`

**4. Legend** (legenda):

- **Mode**: `List`
- **Placement**: `Bottom`
- **Values**: Marque as caixas:
  - ✅ **Last** (valor mais recente)
  - ✅ **Max** (valor máximo)
  - ✅ **Mean** (média)

**5. Thresholds** (linhas de referência - opcional):

- Clique em **"Thresholds"** → **"Add threshold"**
- **Base** (verde): `0`
- **Warning** (amarelo): `100` (se cair abaixo de 100 msg/s)
- **Critical** (vermelho): `10` (se cair abaixo de 10 msg/s)

**6. Graph Styles** (aparência do gráfico):

- **Style**: `Line`
- **Line width**: `2` (linha mais grossa)
- **Fill opacity**: `10` (preenchimento sutil)
- **Point size**: `5` (pontos nos valores)

**7. Apply**:

Clique em **"Apply"** (canto superior direito) para salvar o painel.

---

### Passo 3: Segundo Painel - Latência p95

Agora vamos criar um painel mostrando a latência de processamento de mensagens.

#### Query PromQL

```promql
histogram_quantile(0.95, rate(message_latency_seconds_bucket[5m]))
```

**Explicação**:
- `message_latency_seconds_bucket`: Histogram que armazena latências
- `rate(...[5m])`: Taxa de mudança nos últimos 5 minutos
- `histogram_quantile(0.95, ...)`: Calcula percentil 95 (p95)
- **p95 significa**: 95% das mensagens têm latência abaixo deste valor

#### Configurar Painel

**1. Panel Options**:

- **Title**: `Message Latency p95 (SLO: <100ms)`
- **Description**: `Latência de processamento - 95% das mensagens processadas abaixo deste valor. SLO = 100ms.`

**2. Visualization**: **Time series**

**3. Axes**:

- **Left Y-Axis**:
  - **Unit**: `seconds (s)` (procure em "Time")
  - **Min**: `0`
  - **Decimals**: `3` (mostra até milissegundos: 0.095s = 95ms)
  - **Label**: `Latency (seconds)`

**4. Thresholds** (SLO - Service Level Objective):

- **Base** (verde): `0.1` (100ms - OK ✅)
- **Warning** (amarelo): `0.2` (200ms - Atenção ⚠️)
- **Critical** (vermelho): `0.3` (300ms+ - Problema ❌)

**5. Override Options** (colorir linha baseado em thresholds):

- Clique em **"Overrides"** → **"Add field override"**
- **Select field**: `Query A`
- **Add override property** → **"Standard options"** → **"Color scheme"**
- Selecione: **"By value"** (usa cores dos thresholds)

**6. Apply**: Clique em **"Apply"**

---

### Passo 4: Terceiro Painel - Kafka Lag (Gauge)

Vamos criar um medidor (gauge) para monitorar lag do consumidor Kafka.

#### Query PromQL

```promql
kafka_consumer_lag
```

**Explicação**:
- Métrica tipo Gauge que mostra quantas mensagens estão pendentes de processamento
- Valor ideal: **0** (sem backlog)
- Valor alto: Sistema não está conseguindo processar na velocidade que recebe

#### Configurar Painel

**1. Visualization**: **Gauge** (medidor circular)

**2. Panel Options**:

- **Title**: `Kafka Consumer Lag`
- **Description**: `Mensagens pendentes de processamento no Kafka. Ideal: < 1000`

**3. Value Options**:

- **Show**: `Calculate` → **Last** (valor mais recente)
- **Unit**: `short` (número inteiro)
- **Decimals**: `0` (sem casas decimais)

**4. Gauge**:

- **Min**: `0`
- **Max**: `10000` (escala máxima do medidor)
- **Thresholds**:
  - Verde: `0 - 1000` (OK ✅)
  - Amarelo: `1000 - 5000` (Warning ⚠️)
  - Vermelho: `> 5000` (Critical ❌)

**5. Apply**: Clique em **"Apply"**

---

### Passo 5: Quarto Painel - MongoDB Connection Pool

Medidor mostrando uso do pool de conexões do MongoDB.

#### Query PromQL

```promql
mongodb_connection_pool_usage
```

#### Configurar Painel

**1. Visualization**: **Gauge**

**2. Panel Options**:

- **Title**: `MongoDB Connection Pool Usage`
- **Description**: `Uso do pool de conexões do MongoDB. Ideal: < 60%`

**3. Value Options**:

- **Unit**: `percent (0-100)`
- **Min**: `0`
- **Max**: `100`

**4. Thresholds**:

- Verde: `< 60%` (OK - uso normal)
- Amarelo: `60 - 80%` (Alta utilização)
- Vermelho: `> 80%` (Pool saturando)

**5. Apply**: Clique em **"Apply"**

---

### Passo 6: Organizar Layout (Grid)

Agora vamos organizar os 4 painéis de forma profissional:

#### Layout Recomendado (2x2)

```
┌─────────────────────┬─────────────────────┐
│  Messages/sec       │  Latência p95       │
│  (Time series)      │  (Time series)      │
├─────────────────────┼─────────────────────┤
│  Kafka Lag          │  MongoDB Pool       │
│  (Gauge)            │  (Gauge)            │
└─────────────────────┴─────────────────────┘
```

#### Como Organizar

1. **Modo de Edição**: Clique no ícone ✏️ (Edit) no canto superior direito

2. **Redimensionar Painéis**:
   - Clique no título do painel
   - Arraste o **canto inferior direito** para ajustar tamanho
   - **Grid do Grafana**: 24 colunas
   - **Metade da tela**: width = 12 colunas

3. **Mover Painéis**:
   - Clique e arraste o **título do painel**
   - Posicione conforme layout acima

4. **Altura Recomendada**:
   - **Time series**: 8-10 unidades de altura
   - **Gauge**: 6-8 unidades de altura

---

### Passo 7: Salvar Dashboard

1. Clique no ícone 💾 **"Save dashboard"** (canto superior direito)
2. Configure:
   - **Dashboard name**: `Chat API - Performance`
   - **Folder**: `General` (ou crie pasta "Monitoring")
   - **Description**: `Dashboard de performance da Chat API - Throughput, Latência, Kafka, MongoDB`
3. Clique em **"Save"**

✅ **Parabéns!** Você criou seu primeiro dashboard profissional!

---

## Queries PromQL por Categoria

Aqui estão queries prontas organizadas por categoria. Copie e cole no Grafana.

### 1. Métricas de Throughput (Taxa de Mensagens)

```promql
# Taxa de mensagens enviadas (últimos 1 minuto)
rate(messages_sent_total[1m])

# Taxa de mensagens processadas com sucesso
rate(messages_processed_total{status="DELIVERED"}[1m])

# Taxa de mensagens enviadas vs processadas (diferença = backlog)
rate(messages_sent_total[1m]) - rate(messages_processed_total[1m])

# Total acumulado de mensagens (contador)
messages_sent_total

# Mensagens por tipo (text vs file)
sum by (type) (rate(messages_sent_total[5m]))

# Throughput total do sistema (todas as mensagens)
sum(rate(messages_sent_total[1m]))
```

**Tipo de Gráfico**: Time series ou Stat (número único)

---

### 2. Métricas de Latência (Performance)

```promql
# Latência p50 (mediana) - metade das requisições abaixo deste valor
histogram_quantile(0.50, rate(message_latency_seconds_bucket[5m]))

# Latência p95 - 95% das requisições abaixo deste valor (SLO típico)
histogram_quantile(0.95, rate(message_latency_seconds_bucket[5m]))

# Latência p99 - 99% abaixo (casos extremos)
histogram_quantile(0.99, rate(message_latency_seconds_bucket[5m]))

# Latência média
rate(message_latency_seconds_sum[5m]) / rate(message_latency_seconds_count[5m])

# Latência de validação p95
histogram_quantile(0.95, rate(message_validation_latency_seconds_bucket[5m]))

# Latência de upload de arquivo p95
histogram_quantile(0.95, rate(file_upload_latency_seconds_bucket[5m]))

# Latência de persistência MongoDB p95
histogram_quantile(0.95, rate(mongodb_persist_latency_seconds_bucket[5m]))

# Painel com múltiplos percentis (adicione 3 queries no mesmo painel)
# Query A: histogram_quantile(0.50, ...)  // p50 - linha verde
# Query B: histogram_quantile(0.95, ...)  // p95 - linha amarela
# Query C: histogram_quantile(0.99, ...)  // p99 - linha vermelha
```

**Tipo de Gráfico**: Time series (3 linhas coloridas)

**Dica de Cores**:
- p50: Verde (#73BF69)
- p95: Amarelo (#FADE2A)
- p99: Vermelho (#F2495C)

---

### 3. Métricas de Kafka

```promql
# Lag do consumidor (mensagens não processadas)
kafka_consumer_lag

# Taxa de processamento Kafka (mensagens/segundo)
rate(messages_processed_total[1m])

# Latência de processamento Kafka p95
histogram_quantile(0.95, rate(kafka_message_processing_latency_seconds_bucket[5m]))

# Backlog (mensagens esperando processamento)
rate(messages_sent_total[1m]) - rate(messages_processed_total[1m])

# Taxa de mensagens por status
sum by (status) (rate(messages_processed_total[1m]))
```

**Tipo de Gráfico**:
- Lag: **Gauge** (0-10000)
- Taxa de processamento: **Time series**
- Latência: **Time series**

---

### 4. Métricas de MongoDB

```promql
# Uso do pool de conexões (%)
mongodb_connection_pool_usage

# Latência de persistência p95 (tempo para salvar no MongoDB)
histogram_quantile(0.95, rate(mongodb_persist_latency_seconds_bucket[5m]))

# Taxa de operações MongoDB (writes/segundo)
rate(mongodb_operations_total[1m])

# Total de mensagens persistidas
messages_persisted_total
```

**Tipo de Gráfico**:
- Pool usage: **Gauge** (0-100%)
- Latência: **Time series**
- Operações: **Time series**

---

### 5. Métricas de Erro

```promql
# Taxa de erros total (erros/segundo)
rate(errors_total[1m])

# Taxa de erro por tipo (gráfico empilhado)
sum by (error_type) (rate(errors_total[5m]))

# Percentual de erro (%)
(rate(errors_total[1m]) / rate(messages_sent_total[1m])) * 100

# HTTP 5xx errors (erros de servidor)
rate(http_server_requests_seconds_count{status=~"5.."}[1m])

# HTTP 4xx errors (erros de cliente)
rate(http_server_requests_seconds_count{status=~"4.."}[1m])

# Erros por endpoint
sum by (endpoint) (rate(errors_total[5m]))
```

**Tipo de Gráfico**:
- Taxa de erros: **Time series**
- Erros por tipo: **Bar chart** (gráfico de barras)
- Percentual: **Stat** (número único com threshold)

---

### 6. Métricas de Sistema (JVM)

```promql
# Uso de memória heap (%)
(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100

# Memória heap usada (MB)
jvm_memory_used_bytes{area="heap"} / 1024 / 1024

# Threads ativas
jvm_threads_live_threads

# Taxa de Garbage Collection (GC/segundo)
rate(jvm_gc_pause_seconds_count[1m])

# Tempo gasto em GC (%)
rate(jvm_gc_pause_seconds_sum[1m]) * 100

# CPU usage (%)
process_cpu_usage * 100

# Classes carregadas
jvm_classes_loaded_classes
```

**Tipo de Gráfico**:
- Memória, CPU: **Gauge** ou **Time series**
- Threads: **Time series**
- GC: **Time series**

---

### 7. Métricas de Resilience4j (Circuit Breaker)

```promql
# Estado do Circuit Breaker (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
resilience4j_circuitbreaker_state{name="minio"}
resilience4j_circuitbreaker_state{name="whatsapp"}
resilience4j_circuitbreaker_state{name="instagram"}

# Taxa de falha
resilience4j_circuitbreaker_failure_rate{name="minio"}

# Chamadas buffered
resilience4j_circuitbreaker_buffered_calls{name="minio",kind="successful"}
resilience4j_circuitbreaker_buffered_calls{name="minio",kind="failed"}

# Slow calls
resilience4j_circuitbreaker_slow_calls_total{name="minio"}
```

**Tipo de Gráfico**:
- Estado: **Stat** com value mapping (0→Closed, 1→Open, 2→Half-Open)
- Taxa de falha: **Time series**

---

## Tipos de Painéis e Quando Usar

### 1. Time Series (Gráfico de Linha)

**Quando usar**:
- Métricas que mudam ao longo do tempo
- Comparar múltiplas séries (p50, p95, p99)
- Identificar tendências e padrões

**Exemplos**:
- Taxa de mensagens/segundo
- Latência ao longo do tempo
- Uso de CPU/memória

**Configurações Recomendadas**:
```yaml
Visualization: Time series
Line width: 2
Fill opacity: 10-20
Point size: 0 (sem pontos) ou 5 (com pontos)
Legend: Bottom, com Last/Max/Mean
```

---

### 2. Gauge (Medidor)

**Quando usar**:
- Métricas com valor atual (snapshot)
- Valores com limites conhecidos (0-100%)
- Indicadores de saúde

**Exemplos**:
- Kafka consumer lag (0-10000)
- MongoDB pool usage (0-100%)
- CPU usage (0-100%)

**Configurações Recomendadas**:
```yaml
Visualization: Gauge
Show: Last value
Min: 0
Max: 100 (ou limite conhecido)
Thresholds: Verde/Amarelo/Vermelho
```

---

### 3. Stat (Número Único)

**Quando usar**:
- Métricas simples de contagem
- KPIs importantes (destaque)
- Totais acumulados

**Exemplos**:
- Total de mensagens hoje
- Taxa de sucesso (%)
- Throughput atual (msg/s)

**Configurações Recomendadas**:
```yaml
Visualization: Stat
Show: Last ou Sum
Graph mode: None ou Area (mini gráfico)
Color mode: Value (colorir número)
Text size: Large (destaque)
```

---

### 4. Bar Chart (Gráfico de Barras)

**Quando usar**:
- Comparar valores entre categorias
- Erros por tipo
- Mensagens por plataforma

**Exemplos**:
- Erros por endpoint
- Mensagens por tipo (text vs file)
- Requests por status code (200, 404, 500)

**Configurações Recomendadas**:
```yaml
Visualization: Bar chart
Orientation: Horizontal (barras horizontais)
Show values: Right (valores à direita)
```

---

### 5. Heatmap (Mapa de Calor)

**Quando usar**:
- Distribuição de latências
- Padrões temporais

**Exemplos**:
- Latência por hora do dia
- Distribuição de tempos de resposta

**Configurações Recomendadas**:
```yaml
Visualization: Heatmap
Calculate: Count
Color scheme: Interpolated (gradiente)
```

---

### 6. Table (Tabela)

**Quando usar**:
- Listar valores detalhados
- Comparar múltiplas métricas
- Debug e análise

**Exemplos**:
- Top 10 conversações com mais mensagens
- Endpoints com maior latência

---

## Dashboards Prontos

### Dashboard 1: Performance Geral (Overview)

**Layout**:
```
┌─────────────────────┬─────────────────────┬─────────────────────┐
│  Throughput         │  Taxa de Sucesso    │  Latência p95       │
│  (Stat)             │  (Stat)             │  (Stat)             │
├─────────────────────┴─────────────────────┴─────────────────────┤
│  Messages/sec ao longo do tempo                                 │
│  (Time series)                                                  │
├─────────────────────────────────────────────────────────────────┤
│  Latência - p50, p95, p99                                       │
│  (Time series - 3 linhas)                                       │
├─────────────────────┬─────────────────────┬─────────────────────┤
│  Kafka Lag          │  MongoDB Pool       │  Heap Memory        │
│  (Gauge)            │  (Gauge)            │  (Gauge)            │
└─────────────────────┴─────────────────────┴─────────────────────┘
```

**Painéis**:

| Painel | Query | Tipo | Threshold |
|--------|-------|------|-----------|
| Throughput | `sum(rate(messages_sent_total[1m]))` | Stat | >100 verde |
| Taxa de Sucesso | `(sum(rate(messages_processed_total{status="DELIVERED"}[1m])) / sum(rate(messages_sent_total[1m]))) * 100` | Stat | >99% verde |
| Latência p95 | `histogram_quantile(0.95, ...)` | Stat | <0.1s verde |
| Messages/sec | `rate(messages_sent_total[1m])` | Time series | - |
| Latência Multi | 3 queries (p50, p95, p99) | Time series | - |
| Kafka Lag | `kafka_consumer_lag` | Gauge | <1000 verde |
| MongoDB Pool | `mongodb_connection_pool_usage` | Gauge | <60% verde |
| Heap Memory | `(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100` | Gauge | <70% verde |

---

### Dashboard 2: Debugging e Erros

**Painéis**:

| Painel | Query | Tipo |
|--------|-------|------|
| Taxa de Erro | `rate(errors_total[1m])` | Time series |
| Erros por Tipo | `sum by (error_type) (rate(errors_total[5m]))` | Bar chart |
| Percentual de Erro | `(rate(errors_total[1m]) / rate(messages_sent_total[1m])) * 100` | Stat |
| HTTP 5xx | `rate(http_server_requests_seconds_count{status=~"5.."}[1m])` | Time series |
| Idempotent Requests | `rate(idempotent_requests_total[5m])` | Time series |

---

### Dashboard 3: Infraestrutura (JVM + System)

**Painéis**:

| Painel | Query | Tipo |
|--------|-------|------|
| Heap Usage % | `(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100` | Gauge |
| Heap Used (MB) | `jvm_memory_used_bytes{area="heap"} / 1024 / 1024` | Time series |
| Threads | `jvm_threads_live_threads` | Time series |
| GC Rate | `rate(jvm_gc_pause_seconds_count[1m])` | Time series |
| CPU % | `process_cpu_usage * 100` | Gauge |

---

## Alertas

### Como Criar Alerta no Grafana

1. Edite um painel (ex: Latência p95)
2. Clique na aba **"Alert"** (ícone de sino 🔔)
3. Clique em **"Create alert rule from this panel"**
4. Configure conforme abaixo

### Alerta 1: Latência Alta (SLO Breach)

**Configuração**:
```yaml
Alert rule name: High Message Latency
Evaluate every: 1m
For: 5m

Condition:
  WHEN max() OF query(A, 1m, now) IS ABOVE 0.1

Labels:
  severity: warning
  component: messaging
  slo: latency

Annotations:
  summary: "Message latency p95 above SLO"
  description: "p95 latency is {{ $value }}s (SLO: 100ms)"
  runbook_url: https://wiki.example.com/runbooks/high-latency
```

**Notification**: Selecione canal (Email, Slack, etc.)

---

### Alerta 2: Taxa de Erro Alta

```yaml
Alert rule name: High Error Rate
Evaluate every: 1m
For: 2m

Condition:
  WHEN max() OF query(A, 1m, now) IS ABOVE 1

Query A: (rate(errors_total[1m]) / rate(messages_sent_total[1m])) * 100

Labels:
  severity: critical
  component: messaging

Annotations:
  summary: "Error rate above 1%"
  description: "Current error rate: {{ $value }}%"
```

---

### Alerta 3: Kafka Lag Alto

```yaml
Alert rule name: High Kafka Consumer Lag
Evaluate every: 1m
For: 5m

Condition:
  WHEN max() OF kafka_consumer_lag IS ABOVE 5000

Labels:
  severity: warning
  component: kafka

Annotations:
  summary: "Kafka consumer lag is high"
  description: "Current lag: {{ $value }} messages (threshold: 5000)"
```

---

### Alerta 4: MongoDB Pool Saturado

```yaml
Alert rule name: MongoDB Pool Saturation
Evaluate every: 1m
For: 3m

Condition:
  WHEN max() OF mongodb_connection_pool_usage IS ABOVE 80

Labels:
  severity: critical
  component: mongodb

Annotations:
  summary: "MongoDB connection pool above 80%"
  description: "Current usage: {{ $value }}%"
```

---

## Dicas e Truques

### 1. Usar Variáveis para Dashboards Dinâmicos

**Criar Variável**:

1. Dashboard → ⚙️ Settings → **Variables** → **Add variable**
2. Configure:
   - **Name**: `interval`
   - **Type**: `Interval`
   - **Values**: `1m, 5m, 15m, 1h`
3. Use na query: `rate(messages_sent_total[$interval])`

**Benefício**: Permite alterar intervalo dinamicamente via dropdown

---

### 2. Templates de Repetição

Para criar painéis dinâmicos que se repetem para cada valor:

1. **Variables** → **Add variable**
2. Configure:
   - **Name**: `status`
   - **Type**: `Query`
   - **Data source**: Prometheus
   - **Query**: `label_values(messages_processed_total, status)`
3. No painel, use: `rate(messages_processed_total{status="$status"}[1m])`
4. **Repeat options** → **Repeat by variable**: `$status`

**Resultado**: Um painel para cada status (SENT, DELIVERED, READ)

---

### 3. Usar Transformations

Para manipular dados antes de exibir:

1. Edite painel
2. Aba **"Transform"** → **"Add transformation"**
3. Opções úteis:
   - **Reduce**: Calcular min/max/média
   - **Filter by value**: Mostrar apenas valores > X
   - **Organize fields**: Renomear/reordenar colunas

---

### 4. Atalhos de Teclado no Grafana

| Atalho | Ação |
|--------|------|
| `?` | Mostrar todos os atalhos |
| `e` | Edit mode |
| `d v` | Ver modo (sair de edit) |
| `d s` | Salvar dashboard |
| `d k` | Modo kiosk (tela cheia) |
| `t z` | Zoom out (time range) |

---

### 5. Compartilhar Dashboard

**Exportar JSON**:

1. Dashboard → ⚙️ Settings → **JSON Model**
2. Copiar JSON
3. Salvar em arquivo: `dashboard-performance.json`

**Importar em Outro Grafana**:

1. ➕ **Dashboards** → **Import**
2. **Upload JSON file** ou colar JSON
3. **Load** → **Import**

---

### 6. Snapshot para Compartilhar

**Criar Snapshot**:

1. Dashboard → 📷 **Share** → **Snapshot**
2. **Expire**: 1 hour, 1 day, 1 week
3. **Publish to snapshots.raintank.io**: Se quiser link público
4. **Local snapshot**: Apenas local
5. **Copy Link**

**Uso**: Compartilhar estado atual do dashboard (não atualiza)

---

## Troubleshooting

### Problema 1: "No Data" em Painéis

**Sintoma**: Painéis vazios, sem gráficos

**Diagnóstico**:

```powershell
# 1. Verificar se métricas estão sendo expostas
curl http://localhost:8081/actuator/prometheus | Select-String "messages_sent_total"

# ✅ Se encontrar: Métricas OK
# ❌ Se vazio: Ver Problema 3

# 2. Verificar Prometheus coletando
# http://localhost:9091/targets → chat-api deve estar UP

# 3. Testar query no Prometheus
# http://localhost:9091/graph
# Digite: messages_sent_total
# Se vazio → nenhuma métrica coletada ainda
```

**Solução**:

```powershell
# 1. Gerar dados de teste
grpcurl -plaintext `
    -d '{"conversation_id":"test","sender_id":"alice","message_text":"Test"}' `
    localhost:9090 chat_api.v1.ChatService/SendMessage

# 2. Aguardar scrape (10-15 segundos)
Start-Sleep -Seconds 15

# 3. Recarregar dashboard (🔄 ícone de refresh)

# 4. Ajustar time range
# Grafana → Time range → "Last 15 minutes"
```

---

### Problema 2: Grafana não Conecta Prometheus

**Sintoma**: "Bad Gateway" ou "Connection refused" ao testar Data Source

**Solução**:

1. **Verificar URL**: Deve ser `http://prometheus:9090` (nome do container, NÃO localhost)
2. **Access**: Deve ser **"Server"** (NÃO "Browser")
3. **Testar conectividade**:
   ```powershell
   docker exec grafana ping prometheus
   # ✅ Deve responder: PING prometheus (172.x.x.x)
   ```

4. Se não funcionar:
   ```powershell
   # Recriar containers na mesma network
   docker-compose -f docker-compose.monitoring.yml down
   docker-compose -f docker-compose.monitoring.yml up -d
   ```

---

### Problema 3: Endpoint /actuator/prometheus Retorna 404

**Sintoma**: `curl http://localhost:8081/actuator/prometheus` → HTTP 404

**Causa**: Bug do Spring Boot 3.2.5 com `@ConditionalOnAvailableEndpoint`

**Solução**: Criar `PrometheusController.java`

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

**Recompilar**:

```powershell
mvn clean package -DskipTests
docker-compose build chat-api
docker-compose restart chat-api

# Aguardar 15 segundos
Start-Sleep -Seconds 15

# Verificar
curl http://localhost:8081/actuator/prometheus | Select-Object -First 5
# ✅ Deve retornar métricas
```

---

## Recursos Adicionais

### Documentação Oficial

- **PromQL**: https://prometheus.io/docs/prometheus/latest/querying/basics/
- **Grafana Dashboards**: https://grafana.com/docs/grafana/latest/dashboards/
- **Grafana Alerting**: https://grafana.com/docs/grafana/latest/alerting/

### Dashboards Prontos da Comunidade

- **Grafana Dashboard Library**: https://grafana.com/grafana/dashboards/
- **Spring Boot Dashboard**: https://grafana.com/grafana/dashboards/12900
- **JVM Micrometer**: https://grafana.com/grafana/dashboards/4701

### Exemplos de Queries

- **Prometheus Examples**: https://prometheus.io/docs/prometheus/latest/querying/examples/
- **Rate vs Increase**: https://www.robustperception.io/rate-then-sum-never-sum-then-rate

---

## Próximos Passos

✅ **Você aprendeu**:
- Criar dashboards do zero no Grafana
- Configurar painéis (Time series, Gauge, Stat, Bar chart)
- Escrever queries PromQL para diferentes métricas
- Organizar layout profissional
- Configurar alertas com thresholds
- Troubleshoot problemas comuns

🚀 **Continue Aprendendo**:
1. Criar dashboards personalizados para seu caso de uso
2. Configurar notificações (Slack, Email)
3. Explorar dashboards da comunidade
4. Implementar distributed tracing (Jaeger)

---

**Criado**: 01/12/2025  
**Autor**: GitHub Copilot + Marcos Pereira  
**Versão**: 1.0
