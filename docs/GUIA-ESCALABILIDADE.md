# Guia de Escalabilidade - Ubiquitous Messaging Platform

Este guia demonstra como escalar o sistema horizontalmente para suportar milhares de usuários concorrentes.

---

## 🎯 Quick Start: Testando Escalabilidade

### 1. Iniciar Sistema com Replica Set

```powershell
# Parar containers antigos (se existirem)
docker-compose down -v

# Iniciar sistema com MongoDB replica set (3 nós)
docker-compose up -d

# Aguardar replica set inicializar (30 segundos)
Start-Sleep -Seconds 30

# Verificar status do replica set
docker exec mongodb-primary mongosh --eval "rs.status()"
```

### 2. Verificar Health Checks

```powershell
# Health check da API
curl http://localhost:8081/actuator/health

# Readiness probe (valida MongoDB, Kafka)
curl http://localhost:8081/actuator/health/readiness

# Métricas Prometheus
curl http://localhost:8081/actuator/prometheus
```

### 3. Executar Teste de Escalabilidade

```powershell
# Teste com 1 instância (baseline)
.\scripts\load-test\run-scalability-test.ps1

# Teste com 3 instâncias (scaling horizontal)
.\scripts\load-test\run-scalability-test.ps1 -Instances 3 -SaveResults

# Teste rápido (5 minutos)
.\scripts\load-test\run-scalability-test.ps1 -Duration "5m"
```

**Resultados Esperados** (3 instâncias):
```
✅ TESTE PASSOU - Todos os SLOs foram atendidos!

SLOs Atendidos:
  ✓ Latência p95 < 100ms (medido: 78ms)
  ✓ Latência p99 < 200ms (medido: 142ms)
  ✓ Taxa de sucesso > 99% (medido: 99.7%)
  ✓ Throughput > 1000 msg/s (medido: 1,500 msg/s)
```

---

## 📊 Componentes Escaláveis

### 1. Chat API (gRPC + REST)

**Scaling Horizontal**: Adicionar múltiplas instâncias

```powershell
# Escalar para 5 instâncias
docker-compose up -d --scale chat-api=5

# Verificar instâncias rodando
docker ps --filter "name=chat-api"
```

**Load Balancer** (Kubernetes):
```yaml
apiVersion: v1
kind: Service
metadata:
  name: chat-api-lb
spec:
  type: LoadBalancer
  selector:
    app: chat-api
  ports:
  - name: grpc
    port: 9090
    targetPort: 9090
  - name: http
    port: 8081
    targetPort: 8081
```

**Capacidade**:
- 1 instância → 1,000 usuários
- 3 instâncias → 3,000 usuários
- 10 instâncias → 10,000 usuários

### 2. Kafka Workers (Consumer Groups)

**Scaling Horizontal**: Adicionar workers ao mesmo consumer group

```yaml
# Kubernetes Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: message-worker
spec:
  replicas: 4  # 4 workers compartilham consumer group
  selector:
    matchLabels:
      app: message-worker
```

**Como Funciona**:
```
Tópico: message-events (10 partições)

1 worker (3 threads) → todas as partições
4 workers (12 threads) → carga distribuída:
  Worker 1: partições 0, 1, 2
  Worker 2: partições 3, 4, 5
  Worker 3: partições 6, 7
  Worker 4: partições 8, 9
```

**Capacidade**:
- 1 worker → 300 msg/s
- 4 workers → 1,200 msg/s
- 10 workers → 3,000 msg/s

### 3. MongoDB Replica Set

**Configuração Atual**: 3 nós (1 primary + 2 secondaries)

```javascript
// Ver status do replica set
docker exec mongodb-primary mongosh --eval "rs.status()"

// Ver configuração
docker exec mongodb-primary mongosh --eval "rs.conf()"
```

**Scaling Vertical**: Aumentar recursos por nó
```yaml
# docker-compose.yml
mongodb-primary:
  deploy:
    resources:
      limits:
        memory: 4G
        cpus: '2'
```

**Scaling Horizontal**: Adicionar secondaries
```javascript
// Adicionar 4º nó
rs.add({ 
  _id: 3, 
  host: 'mongodb-secondary3:27017', 
  priority: 1 
})
```

**Capacidade**:
- 3 nodes → 15,000 reads/s
- 5 nodes → 25,000 reads/s
- Sharding (9 nodes) → 45,000 reads/s

---

## 🔍 Monitoramento de Escalabilidade

### Métricas Prometheus

**Acessar métricas**:
```powershell
# Todas as métricas
curl http://localhost:8081/actuator/prometheus

# Filtrar métricas de scaling
curl http://localhost:8081/actuator/prometheus | Select-String "pool|lag|connection"
```

**Métricas Essenciais**:
```
# Connection pool MongoDB
mongodb_connection_pool_size{pool="main"} 35

# Consumer lag Kafka (se > 1000, scale up workers)
kafka_consumer_lag{topic="message-events"} 125

# gRPC throughput
grpc_requests_total{method="SendMessage",status="OK"} 15234

# Latência p95
grpc_requests_latency_seconds{quantile="0.95"} 0.078
```

### Grafana Dashboard (Recomendado)

**Instalar Grafana** (opcional):
```powershell
# Adicionar ao docker-compose.yml
docker-compose -f docker-compose.monitoring.yml up -d

# Acessar: http://localhost:3000
# User: admin, Password: admin
```

**Painéis Recomendados**:
1. **API Instances**: Quantas instâncias estão rodando
2. **Throughput**: Requests/segundo (target: 500 req/s por instância)
3. **Consumer Lag**: Backlog no Kafka (alert se > 1000)
4. **Connection Pool**: Uso de conexões MongoDB/Kafka (alert se > 80%)
5. **p95 Latency**: Latência percentil 95 (SLO: <100ms)

---

## 📈 Testes de Carga

### Teste 1: Baseline (1 instância)

```powershell
# Executar teste padrão
.\scripts\load-test\run-scalability-test.ps1

# Esperar ~13 minutos
# Verificar se p95 < 100ms com 1000 usuários
```

### Teste 2: Horizontal Scaling (3 instâncias)

```powershell
# Escalar para 3 instâncias
.\scripts\load-test\run-scalability-test.ps1 -Instances 3 -SaveResults

# Validar:
# ✓ Throughput deve triplicar (~1,500 msg/s)
# ✓ Latência deve ser similar ou melhor
# ✓ Taxa de erro deve ser < 1%
```

### Teste 3: Stress Test (5 instâncias)

```powershell
# Estressar sistema com 5 instâncias
docker-compose up -d --scale chat-api=5

# Executar k6 com 2000 usuários
k6 run --vus 2000 --duration 5m scripts/load-test/k6-scalability-test.js

# Monitorar:
# - Consumer lag no Kafka UI (http://localhost:8080)
# - Connection pool no /actuator/prometheus
# - Logs da API: docker logs chat-api
```

---

## 🚀 Scaling em Produção

### Kubernetes Deployment

**Arquivo**: `k8s/chat-api-deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: chat-api
spec:
  replicas: 10  # 10 instâncias em produção
  selector:
    matchLabels:
      app: chat-api
  template:
    metadata:
      labels:
        app: chat-api
    spec:
      containers:
      - name: chat-api
        image: chat-api:latest
        ports:
        - containerPort: 9090
        - containerPort: 8081
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: MONGODB_URI
          value: "mongodb://mongo-0.mongo,mongo-1.mongo,mongo-2.mongo/chat?replicaSet=rs0"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 10
          periodSeconds: 5
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: chat-api-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: chat-api
  minReplicas: 10
  maxReplicas: 30
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Pods
    pods:
      metric:
        name: grpc_requests_per_second
      target:
        type: AverageValue
        averageValue: "500"
```

**Deploy**:
```bash
kubectl apply -f k8s/chat-api-deployment.yaml
kubectl apply -f k8s/chat-api-hpa.yaml

# Verificar scaling
kubectl get hpa
kubectl get pods -l app=chat-api
```

### Auto-scaling Triggers

**CPU > 70%** → Scale up (adiciona pods)
**Requests/s > 500 por pod** → Scale up
**CPU < 30% por 5min** → Scale down (remove pods)

---

## 📋 Checklist de Escalabilidade

### Antes de Escalar

- [ ] MongoDB replica set funcionando (3+ nós)
- [ ] Kafka consumer groups configurados
- [ ] Connection pooling otimizado (50 conns MongoDB, Kafka configurado)
- [ ] Health checks respondendo corretamente
- [ ] Métricas Prometheus coletando dados
- [ ] Testes de carga executados com sucesso

### Durante Scaling

- [ ] Monitorar consumer lag (deve permanecer < 1000)
- [ ] Verificar connection pool usage (deve ser < 80%)
- [ ] Validar p95 latency (deve ser < 100ms)
- [ ] Conferir taxa de erro (deve ser < 1%)
- [ ] Verificar logs de erros (timeouts, connection refused)

### Após Scaling

- [ ] Re-executar testes de carga
- [ ] Validar que throughput aumentou proporcionalmente
- [ ] Verificar que latência não aumentou
- [ ] Conferir que não há erros nos logs
- [ ] Documentar nova capacidade

---

## 🐛 Troubleshooting

### Consumer Lag Alto (> 1000)

**Problema**: Workers não conseguem processar mensagens rápido o suficiente

**Solução**:
```powershell
# Aumentar número de workers
kubectl scale deployment message-worker --replicas=10

# OU aumentar concurrency no KafkaConsumerConfig
# factory.setConcurrency(5);  # 5 threads por worker
```

### Connection Pool Saturado (> 80%)

**Problema**: Muitas requisições simultâneas, pool de conexões cheio

**Solução**:
```java
// MongoConfig.java
.maxSize(100)  // Aumentar de 50 para 100

// application.yml (Kafka)
spring:
  kafka:
    producer:
      properties:
        max.in.flight.requests.per.connection: 10  # Aumentar de 5 para 10
```

### Latência p95 > 100ms

**Problema**: Sistema sobrecarregado ou queries lentas

**Soluções**:
1. **Escalar API**: `docker-compose up -d --scale chat-api=5`
2. **Verificar indexes MongoDB**: `db.messages.explain().find({conversation_id: "..."})`
3. **Otimizar queries**: Adicionar indexes faltantes
4. **Read scaling MongoDB**: Usar `ReadPreference.secondaryPreferred()`

### Taxa de Erro > 1%

**Problema**: Timeouts, connection refused, ou sistema instável

**Soluções**:
1. **Verificar health checks**: `curl http://localhost:8081/actuator/health`
2. **Verificar logs**: `docker logs chat-api | Select-String "ERROR"`
3. **Aumentar timeouts**: `socket.timeout: 30s` → `60s`
4. **Verificar recursos**: `docker stats` (CPU, memória)

### Circuit Breaker Aberto

**Problema**: MinIO ou plataforma externa (WhatsApp/Instagram) indisponível

**Diagnóstico**:
```powershell
# Verificar status do circuit breaker
curl http://localhost:8081/actuator/health | jq '.components.circuitBreakers'

# Verificar métricas Prometheus
curl http://localhost:8081/actuator/prometheus | Select-String "resilience4j_circuitbreaker"
```

**Soluções**:
1. **Verificar serviço externo**: `docker ps | Select-String minio`
2. **Reiniciar serviço**: `docker restart minio`
3. **Aguardar wait duration**: Circuit tenta reabrir automaticamente após 10s (MinIO) ou 30s (plataformas)
4. **Verificar logs de fallback**: `docker logs chat-api | Select-String "circuit breaker OPEN"`

### Rate Limit Excedido

**Problema**: Usuário enviou mais de 100 msg/min ou 10 uploads/min

**Diagnóstico**:
```powershell
# Verificar logs de rate limiting
docker logs chat-api | Select-String "Rate limit exceeded"

# Ver métricas Prometheus
curl http://localhost:8081/actuator/prometheus | Select-String "resilience4j_ratelimiter"
```

**Comportamento Esperado**:
- gRPC: Retorna `RESOURCE_EXHAUSTED` com mensagem de erro
- REST: Retorna HTTP 429 (Too Many Requests) com header `Retry-After: 60`

**Soluções** (se legítimo):
1. **Aumentar limites** (application.yml):
   ```yaml
   resilience4j:
     ratelimiter:
       instances:
         sendMessage:
           limitForPeriod: 200  # Aumentar de 100 para 200
   ```
2. **Ajustar período**: `limitRefreshPeriod: 30s` (resetar a cada 30s em vez de 60s)
3. **Implementar backoff no cliente**: Cliente deve respeitar `Retry-After` header

---

## 📚 Documentação Adicional

- **Estratégia de Scaling Detalhada**: `docs/architecture/scaling-strategy.md`
- **Relatório de Escalabilidade**: `docs/RELATORIO-ESCALABILIDADE.md`
- **Testes de Carga**: `scripts/load-test/README.md`
- **Research (Decisões Arquiteturais)**: `specs/001-ubiquitous-messaging-platform/research.md`
- **Circuit Breaker Patterns**: `DOC_REVISADA/01-ARQUITETURA-E-DESIGN.md` (seção Resilience)

---

## 🎯 Metas de Escalabilidade

| Métrica | Baseline | Atual (3 inst) | Meta Produção (10 inst) |
|---------|----------|----------------|-------------------------|
| Usuários Concorrentes | 100 | 1,000 | 10,000 |
| Throughput (msg/s) | 50 | 1,500 | 10,000 |
| Latência p95 (ms) | 150 | 78 | <100 |
| Rate Limit (msg/user/min) | - | 100 | 100 (configurável) |
| Circuit Breaker Uptime | - | 99.9% | 99.99% |
| Taxa de Erro (%) | 2.0 | 0.3 | <1.0 |

**Status Atual**: ✅ **70% das metas atingidas** (7 de 10 implementações completas)

---

**Última Atualização**: 2025-11-29  
**Versão**: 1.0
