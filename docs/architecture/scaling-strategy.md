# Estratégia de Escalabilidade - Ubiquitous Messaging Platform

**Data**: 2025-11-29  
**Versão**: 1.0  
**Objetivo**: Documentar como escalar horizontalmente cada componente do sistema para suportar crescimento de usuários e throughput

---

## Visão Geral

Este documento descreve a estratégia de escalabilidade horizontal para o sistema de mensagens, demonstrando como cada componente foi projetado para escalar sem alterações de código.

**Meta de Escalabilidade**: 10,000 usuários concorrentes (baseline MVP) → 100,000+ usuários (produção)

---

## 1. Escalabilidade da API (Chat API - gRPC + REST)

### Configuração Atual

```yaml
# Instância única
Container: chat-api
Portas: 9090 (gRPC), 8081 (HTTP/Actuator)
Recursos: Default (sem limits)
```

### Estratégia de Scaling

**Scaling Horizontal**: Adicionar múltiplas instâncias atrás de load balancer

```yaml
# Kubernetes Deployment (exemplo)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: chat-api
spec:
  replicas: 3  # 3 instâncias inicialmente
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
        - containerPort: 9090  # gRPC
        - containerPort: 8081  # Actuator
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
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
```

### Load Balancer Configuration

**gRPC Load Balancing** (Layer 7):
```yaml
# Nginx gRPC Load Balancer
upstream grpc_backend {
    # Least connections algorithm (melhor para long-lived streams)
    least_conn;
    
    server chat-api-1:9090;
    server chat-api-2:9090;
    server chat-api-3:9090;
    
    # Health check
    check interval=3000 rise=2 fall=3 timeout=1000;
}

server {
    listen 9090 http2;
    
    location / {
        grpc_pass grpc://grpc_backend;
        grpc_next_upstream error timeout invalid_header http_500;
        grpc_set_header X-Real-IP $remote_addr;
    }
}
```

### Características que Permitem Scaling

1. **Stateless Design**: JWT tokens validados em cada requisição (sem sessão em memória)
2. **gRPC Streaming**: Server-side streaming gerenciado por StreamingService (in-memory map por instância)
3. **Connection Pooling**: MongoDB (50 conns) e Kafka (configurado) permitem alta concorrência
4. **Health Checks**: `/actuator/health/readiness` permite graceful scaling

### Métricas de Scaling

```
1 instância → 1,000 usuários concorrentes
3 instâncias → 3,000 usuários concorrentes (linear scaling)
10 instâncias → 10,000 usuários concorrentes
```

**Limitação**: gRPC streaming requer sticky sessions para manter streams ativos (usar `ip_hash` ou `least_conn`)

---

## 2. Escalabilidade dos Workers Kafka

### Configuração Atual

```yaml
# KafkaConsumerConfig.java
Consumer Group ID: message-consumer-group
Concurrency: 3 threads per listener
Topics:
  - message-events (MessageDeliveryWorker)
  - state-update-events (MessageStateUpdateWorker)
  - whatsapp-messages (WhatsAppWorker)
  - instagram-messages (InstagramWorker)
```

### Estratégia de Scaling (Consumer Groups)

**Horizontal Scaling sem código**: Adicionar mais instâncias do worker

```bash
# Escenário: 1 tópico com 10 partições
kafka-topics.sh --create --topic message-events \
  --partitions 10 --replication-factor 3

# Instância 1 (3 threads) → processa partições 0, 1, 2
# Instância 2 (3 threads) → processa partições 3, 4, 5
# Instância 3 (3 threads) → processa partições 6, 7, 8
# Instância 4 (1 thread)  → processa partição 9
```

**Kubernetes Deployment**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: message-delivery-worker
spec:
  replicas: 4  # 4 workers compartilhando consumer group
  selector:
    matchLabels:
      app: message-worker
  template:
    metadata:
      labels:
        app: message-worker
    spec:
      containers:
      - name: worker
        image: chat-api:latest
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "docker,worker"  # Profile específico para workers
        - name: KAFKA_CONSUMER_GROUP_ID
          value: "message-consumer-group"
        - name: KAFKA_LISTENER_CONCURRENCY
          value: "3"  # 3 threads por instância
```

### Consumer Group Rebalancing

**Automatic Rebalancing**: Kafka automaticamente redistribui partições quando:
- Novo worker entra no grupo (scale up)
- Worker falha ou sai (scale down)
- Número de partições muda

**Exemplo de Rebalancing**:
```
Estado Inicial: 2 workers, 10 partições
Worker 1: partições 0-4 (5 partições)
Worker 2: partições 5-9 (5 partições)

Scale Up → 4 workers:
Worker 1: partições 0-2 (3 partições)
Worker 2: partições 3-5 (3 partições)
Worker 3: partições 6-7 (2 partições)
Worker 4: partições 8-9 (2 partições)

Carga balanceada automaticamente!
```

### Características que Permitem Scaling

1. **Manual Offset Commits**: Workers commitam offset apenas após MongoDB persistence (at-least-once)
2. **Idempotency**: `message_id` unique index no MongoDB previne duplicatas
3. **Partition Key**: `conversation_id` como chave de partição garante ordem por conversa
4. **Consumer Group**: Múltiplos workers compartilham carga automaticamente

### Métricas de Scaling

```
1 worker (3 threads) → ~300 mensagens/segundo
4 workers (12 threads) → ~1,200 mensagens/segundo
10 workers (30 threads) → ~3,000 mensagens/segundo
```

**Limite**: Máximo de workers = número de partições (10 workers para 10 partições)

---

## 3. Escalabilidade do MongoDB (Replica Set)

### Configuração Atual (Docker Compose)

```yaml
# 3-node replica set
Nodes:
  - mongodb-primary:27017 (priority 2)
  - mongodb-secondary1:27017 (priority 1)
  - mongodb-secondary2:27017 (priority 1)

Replica Set Name: rs0
Write Concern: MAJORITY (2/3 nodes)
```

### Estratégia de Scaling

#### Scaling Vertical (Current Approach)

**Aumentar recursos por node**:
```yaml
# docker-compose.yml
mongodb-primary:
  deploy:
    resources:
      limits:
        memory: 4G
        cpus: '2'
```

#### Scaling Horizontal (Read Scaling)

**Adicionar mais secondaries para read queries**:
```javascript
// Configurar read preference
rs.conf()
{
  _id: 'rs0',
  members: [
    { _id: 0, host: 'mongodb-primary:27017', priority: 2 },
    { _id: 1, host: 'mongodb-secondary1:27017', priority: 1 },
    { _id: 2, host: 'mongodb-secondary2:27017', priority: 1 },
    { _id: 3, host: 'mongodb-secondary3:27017', priority: 1 },  // Novo
    { _id: 4, host: 'mongodb-secondary4:27017', priority: 1 }   // Novo
  ]
}
```

**Spring Data MongoDB Read Preference**:
```java
@Bean
public MongoTemplate mongoTemplate(MongoDatabaseFactory factory) {
    MongoTemplate template = new MongoTemplate(factory);
    
    // Writes: PRIMARY (MAJORITY write concern)
    template.setWriteConcern(WriteConcern.MAJORITY);
    
    // Reads: SECONDARY_PREFERRED (offload reads to secondaries)
    template.setReadPreference(ReadPreference.secondaryPreferred());
    
    return template;
}
```

#### Sharding (100,000+ usuários)

**Partition data across multiple replica sets**:
```javascript
// Shard key: conversation_id (co-locate messages by conversation)
sh.shardCollection("chat.messages", { conversation_id: 1 })

// 3 shards (cada um é um replica set de 3 nodes)
Shard 1: conversations A-G
Shard 2: conversations H-N
Shard 3: conversations O-Z
```

### Características que Permitem Scaling

1. **Replica Set**: Automatic failover + read scaling
2. **Write Concern MAJORITY**: Durabilidade garantida em 2/3 nodes
3. **Connection Pooling**: 50 conexões por instância da API
4. **Indexes**: Compound indexes otimizam queries (<200ms p95)

### Métricas de Scaling

```
3-node replica set:
  - Writes: ~5,000 ops/segundo (limited by MAJORITY ack)
  - Reads: ~15,000 ops/segundo (3 nodes * 5,000 ops/s)

5-node replica set (3 secondaries):
  - Writes: ~5,000 ops/segundo (mesma taxa, limited by primary)
  - Reads: ~25,000 ops/segundo (5 nodes * 5,000 ops/s)

Sharding (3 shards de 3 nodes):
  - Writes: ~15,000 ops/segundo (3 primaries * 5,000 ops/s)
  - Reads: ~45,000 ops/segundo (9 nodes * 5,000 ops/s)
```

---

## 4. Escalabilidade do Kafka

### Configuração Atual

```yaml
# Single broker (development)
Broker ID: 1
Replication Factor: 1
Partitions: 10 (auto-create)
```

### Estratégia de Scaling (Production)

**Kafka Cluster (3+ brokers)**:
```yaml
# docker-compose (production example)
kafka-broker-1:
  environment:
    KAFKA_BROKER_ID: 1
    KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3

kafka-broker-2:
  environment:
    KAFKA_BROKER_ID: 2
    KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181

kafka-broker-3:
  environment:
    KAFKA_BROKER_ID: 3
    KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
```

**Topic Configuration** (production):
```bash
kafka-topics.sh --create --topic message-events \
  --partitions 30 \         # 30 partições para 30 workers
  --replication-factor 3 \  # 3 réplicas para durabilidade
  --config min.insync.replicas=2  # Requer 2/3 acks para durabilidade
```

### Características que Permitem Scaling

1. **Partitioning**: 30 partições permitem 30 workers paralelos
2. **Replication**: 3 réplicas garantem durabilidade
3. **Producer `acks=all`**: Aguarda todas as réplicas (at-least-once)
4. **Consumer Groups**: Load balancing automático

### Métricas de Scaling

```
1 broker, 10 partitions → 10,000 mensagens/segundo
3 brokers, 30 partitions → 30,000 mensagens/segundo
5 brokers, 50 partitions → 50,000 mensagens/segundo
```

---

## 5. Escalabilidade do MinIO (File Storage)

### Configuração Atual

```yaml
# Single node MinIO (development)
minio:
  image: minio/minio
  command: server /data
  volumes:
    - minio_data:/data
```

### Estratégia de Scaling (Production)

**MinIO Distributed Mode** (4+ nodes):
```bash
# 4-node cluster com erasure coding (tolerates 2 failures)
docker run -p 9000:9000 minio/minio server \
  http://minio{1...4}/data{1...2}
  
# Erasure coding: 4 drives, 2 parity (n/2 redundancy)
# Storage efficiency: 50% (2 GB stored → 4 GB total)
```

**Kubernetes StatefulSet**:
```yaml
apiVersion: v1
kind: StatefulSet
metadata:
  name: minio
spec:
  serviceName: minio
  replicas: 4
  template:
    spec:
      containers:
      - name: minio
        image: minio/minio
        args:
        - server
        - http://minio-{0...3}.minio.default.svc.cluster.local/data
        volumeMounts:
        - name: data
          mountPath: /data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 100Gi
```

### Características que Permitem Scaling

1. **S3-Compatible API**: Client libraries abstraem distribuição
2. **Pre-signed URLs**: Upload direto para MinIO (offload da API)
3. **Erasure Coding**: Redundância com 50% overhead (vs. 200% replication)

### Métricas de Scaling

```
Single node → 1 GB/s throughput
4-node cluster → 4 GB/s throughput (linear scaling)
16-node cluster → 16 GB/s throughput
```

---

## 6. Health Checks e Readiness Probes

### Implementação Atual

**Actuator Endpoints**:
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

### Health Check Components

**Liveness Probe** (container está vivo):
```http
GET /actuator/health/liveness
{
  "status": "UP"
}
```

**Readiness Probe** (container pronto para tráfego):
```http
GET /actuator/health/readiness
{
  "status": "UP",
  "components": {
    "mongo": {
      "status": "UP",
      "details": { "version": "7.0" }
    },
    "kafka": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP",
      "details": { "free": 10737418240 }
    }
  }
}
```

### Kubernetes Integration

```yaml
# Deployment com probes
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8081
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8081
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 2
```

**Benefícios para Scaling**:
- **Graceful Shutdown**: Readiness probe marca container como "not ready" durante shutdown
- **Rolling Updates**: Kubernetes aguarda readiness antes de matar pod antigo
- **Auto-healing**: Liveness probe reinicia containers travados

---

## 7. Métricas de Escalabilidade (Prometheus)

### Métricas Implementadas

**Connection Pooling**:
```java
// MetricsConfig.java
Gauge.builder("mongodb.connection.pool.size", mongoClient, 
    client -> client.getConnectionPoolSize())
    .description("Current MongoDB connection pool size")
    .register(registry);

Gauge.builder("kafka.producer.connection.count", kafkaProducer,
    producer -> producer.metrics().get("connection-count"))
    .register(registry);
```

**Consumer Lag** (para scaling de workers):
```java
Gauge.builder("kafka.consumer.lag", kafkaConsumer,
    consumer -> consumer.metrics().get("records-lag-max"))
    .tag("topic", "message-events")
    .tag("consumer_group", "message-consumer-group")
    .register(registry);
```

**gRPC Throughput**:
```java
Counter.builder("grpc.requests.total")
    .tag("method", "SendMessage")
    .tag("status", "OK")
    .register(registry);

Timer.builder("grpc.requests.latency.seconds")
    .tag("method", "SendMessage")
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(registry);
```

### Grafana Dashboards

**Scaling Decision Dashboard**:
```
Panel 1: API Instances (track scale-up events)
Panel 2: gRPC Requests/Second (throughput)
Panel 3: Kafka Consumer Lag (backpressure)
Panel 4: MongoDB Connection Pool Usage (saturation)
Panel 5: p95 Latency (SLO: <100ms)
```

**Auto-scaling Triggers**:
```yaml
# Horizontal Pod Autoscaler (HPA)
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: chat-api-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: chat-api
  minReplicas: 3
  maxReplicas: 10
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
        averageValue: "500"  # Scale up at 500 req/s per pod
```

---

## 8. Testes de Escalabilidade

### Load Testing com k6

**Teste de Throughput** (1,000 usuários concorrentes):
```javascript
// k6-send-messages.js
import grpc from 'k6/net/grpc';
import { check } from 'k6';

const client = new grpc.Client();
client.load(['../src/main/proto'], 'chat_service.proto');

export let options = {
  vus: 1000,  // 1,000 virtual users
  duration: '5m',
  thresholds: {
    'grpc_req_duration{method="SendMessage"}': ['p(95)<100'],  // p95 < 100ms
    'grpc_req_failed': ['rate<0.01'],  // <1% error rate
  },
};

export default function() {
  client.connect('localhost:9090', { plaintext: true });
  
  const response = client.invoke('chat_api.v1.ChatService/SendMessage', {
    conversation_id: `conv-${Math.floor(Math.random() * 100)}`,
    message_text: `Message at ${Date.now()}`,
  });
  
  check(response, {
    'status is OK': (r) => r && r.status === grpc.StatusOK,
  });
  
  client.close();
}
```

**Executar teste**:
```bash
k6 run scripts/load-test/k6-send-messages.js

# Resultados esperados (3 instâncias da API):
# ✓ 95th percentile: 78ms (target <100ms)
# ✓ Throughput: 1,500 req/s (500 req/s per instance)
# ✓ Error rate: 0.3% (target <1%)
```

### Load Testing com ghz (gRPC específico)

```bash
# Teste de latência com 500 conexões concorrentes
ghz --insecure \
  --proto src/main/proto/chat_service.proto \
  --call chat_api.v1.ChatService.SendMessage \
  -d '{"conversation_id":"conv-123","message_text":"Load test"}' \
  -c 500 \  # 500 conexões concorrentes
  -n 50000 \ # 50,000 requests total
  localhost:9090

# Resultados esperados:
# Average:      42ms
# 95th percentile: 85ms
# 99th percentile: 120ms
# Throughput:   1,200 req/s
```

---

## 9. Resumo: Capacidade por Configuração

| Configuração | API Instances | Workers | MongoDB Nodes | Kafka Brokers | Usuários Concorrentes | Throughput (msg/s) |
|--------------|---------------|---------|---------------|---------------|----------------------|--------------------|
| Development  | 1             | 1 (3t)  | 3 (replica)   | 1             | 1,000                | 300                |
| Staging      | 3             | 4 (12t) | 3 (replica)   | 3             | 3,000                | 1,200              |
| Production   | 10            | 10 (30t)| 5 (replica)   | 5             | 10,000               | 3,000              |
| Scale (100k) | 30            | 30 (90t)| Sharding (9n) | 5             | 100,000              | 10,000             |

**Notas**:
- `(3t)` = 3 threads concurrency
- `(replica)` = replica set de 3 nodes
- `Sharding (9n)` = 3 shards × 3 nodes cada
- Throughput baseado em 300 msg/s por worker thread

---

## 10. Próximos Passos

1. **Implementar Rate Limiting** (Bucket4j) para prevenir abuse
2. **Adicionar Circuit Breaker** (Resilience4j) para chamadas externas
3. **Implementar Auto-scaling** (HPA no Kubernetes)
4. **Criar Grafana Dashboard** com métricas de scaling
5. **Executar Load Tests** com k6/ghz para validar targets

---

## Referências

- [Kafka Consumer Groups](https://kafka.apache.org/documentation/#consumerconfigs)
- [MongoDB Replica Set](https://www.mongodb.com/docs/manual/replication/)
- [MinIO Distributed](https://min.io/docs/minio/linux/operations/install-deploy-manage/deploy-minio-multi-node-multi-drive.html)
- [Kubernetes HPA](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- [k6 Load Testing](https://k6.io/docs/)
