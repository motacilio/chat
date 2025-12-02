# Deployment Runbook

**Version**: 1.0  
**Last Updated**: 2025-11-29  
**Audience**: DevOps Engineers, System Administrators

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Environment Setup](#environment-setup)
3. [Docker Compose Deployment](#docker-compose-deployment)
4. [Kubernetes Deployment](#kubernetes-deployment)
5. [Post-Deployment Verification](#post-deployment-verification)
6. [Rollback Procedures](#rollback-procedures)
7. [Monitoring Setup](#monitoring-setup)

---

## Prerequisites

### System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| CPU | 4 cores | 8 cores |
| RAM | 8 GB | 16 GB |
| Disk | 50 GB | 100 GB (SSD) |
| Network | 100 Mbps | 1 Gbps |

### Software Dependencies

```bash
# Docker & Docker Compose
docker --version  # ≥ 24.0
docker compose version  # ≥ 2.20

# Optional: k6 for load testing
k6 version  # ≥ 0.47

# Optional: Kubernetes
kubectl version  # ≥ 1.28
```

### Port Requirements

Ensure the following ports are available:

| Service | Port | Protocol | Purpose |
|---------|------|----------|---------|
| Chat API (gRPC) | 9090 | TCP | gRPC client connections |
| Chat API (HTTP) | 8081 | TCP | REST endpoints, webhooks |
| MongoDB | 27017-27019 | TCP | Database (replica set) |
| Kafka | 9092 | TCP | Message broker |
| Zookeeper | 2181 | TCP | Kafka coordination |
| MinIO | 9000 | TCP | Object storage |
| MinIO Console | 9001 | TCP | Admin UI |
| Prometheus | 9091 | TCP | Metrics scraping |
| Grafana | 3000 | TCP | Dashboards |

**Check port availability**:
```powershell
# Windows (PowerShell)
Test-NetConnection -ComputerName localhost -Port 9090
```

---

## Environment Setup

### 1. Clone Repository

```bash
git clone https://github.com/your-org/chat-api.git
cd chat-api
```

### 2. Environment Variables

Create `.env` file in repository root:

```bash
# Application
APP_ENV=production
LOG_LEVEL=INFO

# MongoDB
MONGO_INITDB_ROOT_USERNAME=admin
MONGO_INITDB_ROOT_PASSWORD=<STRONG_PASSWORD>
MONGO_DATABASE=chat_db

# Kafka
KAFKA_BROKER_ID=1
KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181
KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092

# MinIO
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=<STRONG_PASSWORD>
MINIO_BUCKET_NAME=chat-files

# Telegram Bot (optional)
TELEGRAM_BOT_TOKEN=<YOUR_BOT_TOKEN>
TELEGRAM_BOT_USERNAME=<YOUR_BOT_USERNAME>
TELEGRAM_WEBHOOK_SECRET=<RANDOM_SECRET>

# Webhook Base URL (for production)
WEBHOOK_BASE_URL=https://your-domain.com

# Grafana
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=<STRONG_PASSWORD>
```

**Security Notes**:
- Replace all `<STRONG_PASSWORD>` with secure values (min 16 characters)
- Use `openssl rand -base64 32` to generate random secrets
- Never commit `.env` to version control (already in `.gitignore`)

### 3. Validate Configuration

```powershell
# Verify .env file exists
if (Test-Path .env) { 
    Write-Host "✅ .env file found" 
} else { 
    Write-Host "❌ .env file missing" 
}

# Verify Docker is running
docker ps
```

---

## Docker Compose Deployment

### Development Environment

**Start all services**:
```powershell
docker compose -f docker-compose.dev.yml up -d
```

**Services included**:
- MongoDB (single instance)
- Kafka + Zookeeper
- MinIO
- Chat API (auto-rebuild on code changes)

**Access URLs**:
- Chat API (gRPC): `localhost:9090`
- Chat API (HTTP): `http://localhost:8081`
- MinIO Console: `http://localhost:9001`

### Production Environment

**Step 1: Build application JAR**
```powershell
mvn clean package -DskipTests
```

**Step 2: Build Docker image**
```powershell
docker build -t chat-api:latest .
```

**Step 3: Start infrastructure services first**
```powershell
# Start MongoDB replica set
docker compose up -d mongo1 mongo2 mongo3

# Wait for MongoDB to be ready
Start-Sleep -Seconds 10

# Initialize replica set
docker exec -it mongo1 mongosh --eval "
rs.initiate({
  _id: 'rs0',
  members: [
    { _id: 0, host: 'mongo1:27017' },
    { _id: 1, host: 'mongo2:27017' },
    { _id: 2, host: 'mongo3:27017' }
  ]
})
"

# Verify replica set status
docker exec -it mongo1 mongosh --eval "rs.status()"
```

**Step 4: Start Kafka**
```powershell
docker compose up -d zookeeper kafka

# Wait for Kafka to be ready
Start-Sleep -Seconds 15

# Create topics
docker exec -it kafka kafka-topics --create `
  --bootstrap-server localhost:9092 `
  --topic message-events `
  --partitions 3 `
  --replication-factor 1

docker exec -it kafka kafka-topics --create `
  --bootstrap-server localhost:9092 `
  --topic state-update-events `
  --partitions 3 `
  --replication-factor 1
```

**Step 5: Start MinIO**
```powershell
docker compose up -d minio

# Create bucket (MinIO automatically creates on first upload)
```

**Step 6: Start Chat API**
```powershell
docker compose up -d chat-api

# Check logs
docker compose logs -f chat-api
```

**Step 7: Start monitoring (optional)**
```powershell
docker compose -f docker-compose.monitoring.yml up -d

# Access Grafana: http://localhost:3000
# Access Prometheus: http://localhost:9091
```

### Full Stack (One Command)

```powershell
# Start everything (dev environment)
docker compose -f docker-compose.dev.yml up -d

# OR production environment
docker compose up -d
```

---

## Kubernetes Deployment

### Prerequisites

- Kubernetes cluster (v1.28+)
- kubectl configured
- Helm 3 installed

### 1. Create Namespace

```bash
kubectl create namespace chat-api
kubectl config set-context --current --namespace=chat-api
```

### 2. Deploy MongoDB (StatefulSet)

```bash
# Create persistent volumes
kubectl apply -f k8s/mongodb-pv.yaml

# Deploy MongoDB replica set
kubectl apply -f k8s/mongodb-statefulset.yaml

# Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app=mongodb --timeout=300s

# Initialize replica set
kubectl exec -it mongodb-0 -- mongosh --eval "
rs.initiate({
  _id: 'rs0',
  members: [
    { _id: 0, host: 'mongodb-0.mongodb-service:27017' },
    { _id: 1, host: 'mongodb-1.mongodb-service:27017' },
    { _id: 2, host: 'mongodb-2.mongodb-service:27017' }
  ]
})
"
```

### 3. Deploy Kafka (Helm Chart)

```bash
# Add Bitnami Helm repository
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# Install Kafka
helm install kafka bitnami/kafka \
  --set replicaCount=3 \
  --set persistence.size=10Gi \
  --set zookeeper.persistence.size=5Gi \
  --namespace chat-api

# Wait for Kafka to be ready
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=kafka --timeout=300s
```

### 4. Deploy Chat API (Deployment)

```bash
# Create ConfigMap for application.yml
kubectl create configmap chat-api-config \
  --from-file=application.yml=src/main/resources/application.yml

# Create Secret for sensitive data
kubectl create secret generic chat-api-secrets \
  --from-literal=mongo-password=<PASSWORD> \
  --from-literal=telegram-bot-token=<TOKEN>

# Deploy Chat API
kubectl apply -f k8s/chat-api-deployment.yaml

# Expose as LoadBalancer
kubectl apply -f k8s/chat-api-service.yaml

# Get external IP
kubectl get svc chat-api-service
```

### 5. Deploy MinIO (Helm Chart)

```bash
helm install minio bitnami/minio \
  --set auth.rootUser=minioadmin \
  --set auth.rootPassword=<PASSWORD> \
  --set persistence.size=50Gi \
  --namespace chat-api
```

### 6. Deploy Monitoring Stack

```bash
# Install Prometheus + Grafana
helm install prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace

# Port-forward Grafana
kubectl port-forward -n monitoring svc/prometheus-stack-grafana 3000:80
```

---

## Post-Deployment Verification

### Health Checks

**1. Check all services are running**
```powershell
docker compose ps
# Expected: All services show "Up" status
```

**2. Chat API Health**
```powershell
curl http://localhost:8081/actuator/health
# Expected: {"status":"UP"}
```

**3. MongoDB Replica Set**
```powershell
docker exec -it mongo1 mongosh --eval "rs.status().ok"
# Expected: 1
```

**4. Kafka Topics**
```powershell
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
# Expected: message-events, state-update-events
```

**5. MinIO Bucket**
```powershell
docker exec -it minio mc ls local/
# Expected: chat-files/ (may not exist until first upload)
```

### Functional Tests

**1. Send Test Message (gRPC)**
```bash
# Requires grpcurl (https://github.com/fullstorydev/grpcurl)
grpcurl -plaintext -d '{
  "message_id": "550e8400-e29b-41d4-a716-446655440000",
  "conversation_id": "660e8400-e29b-41d4-a716-446655440001",
  "sender_id": "user123",
  "recipient_id": "user456",
  "message_text": "Hello, deployment test!"
}' localhost:9090 chat.v1.ChatService/SendMessage
```

**2. Check Actuator Endpoints**
```powershell
# Health
curl http://localhost:8081/actuator/health

# Info
curl http://localhost:8081/actuator/info

# Metrics
curl http://localhost:8081/actuator/prometheus
```

**3. Verify Kafka Message Delivery**
```powershell
# Consume from topic
docker exec -it kafka kafka-console-consumer `
  --bootstrap-server localhost:9092 `
  --topic message-events `
  --from-beginning `
  --max-messages 1
```

### Performance Baseline

**Run k6 benchmark** (optional):
```powershell
cd scripts/load-test
.\run-benchmark.ps1 -Quick -ApiUrl http://localhost:8081
```

**Expected Results**:
- p95 latency: <100ms
- Error rate: <1%
- Throughput: >500 req/s (1k users)

---

## Rollback Procedures

### Docker Compose Rollback

**1. Stop current deployment**
```powershell
docker compose down
```

**2. Revert to previous image**
```powershell
# Pull previous version from registry
docker pull your-registry/chat-api:v1.0.0

# Tag as latest
docker tag your-registry/chat-api:v1.0.0 chat-api:latest

# Restart services
docker compose up -d
```

**3. Verify rollback**
```powershell
docker compose logs -f chat-api
curl http://localhost:8081/actuator/health
```

### Kubernetes Rollback

**1. Check rollout history**
```bash
kubectl rollout history deployment/chat-api
```

**2. Rollback to previous version**
```bash
kubectl rollout undo deployment/chat-api

# OR rollback to specific revision
kubectl rollout undo deployment/chat-api --to-revision=2
```

**3. Monitor rollback**
```bash
kubectl rollout status deployment/chat-api
```

### Database Rollback (Emergency)

**MongoDB Snapshot Restore**:
```bash
# Stop Chat API to prevent writes
docker compose stop chat-api

# Restore from backup (example with mongodump)
mongorestore --host localhost:27017 \
  --username admin \
  --password <PASSWORD> \
  --authenticationDatabase admin \
  --db chat_db \
  --dir ./backups/chat_db_2025-11-29

# Restart Chat API
docker compose start chat-api
```

---

## Monitoring Setup

### Grafana Dashboards

**1. Access Grafana**
```
URL: http://localhost:3000
Username: admin
Password: <GRAFANA_ADMIN_PASSWORD from .env>
```

**2. Import dashboards**
```
Settings → Data Sources → Add Prometheus
URL: http://prometheus:9091

Dashboards → Import → Upload JSON file
File: docs/observabilidade/grafana-dashboard-basic.json
```

**3. Verify metrics**
- Navigate to "Chat API - Overview" dashboard
- Check metrics: messages_sent_total, message_validation_latency_seconds
- Verify data is flowing (refresh every 5s)

### Alerting Rules

**Example: High Error Rate Alert**

Create `prometheus/alerts.yml`:
```yaml
groups:
  - name: chat_api_alerts
    interval: 30s
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value }} errors/second"
```

**Configure Alertmanager** (optional):
```yaml
# alertmanager.yml
global:
  smtp_from: 'alerts@your-domain.com'
  smtp_smarthost: 'smtp.gmail.com:587'
  smtp_auth_username: 'alerts@your-domain.com'
  smtp_auth_password: '<APP_PASSWORD>'

route:
  receiver: 'email'

receivers:
  - name: 'email'
    email_configs:
      - to: 'devops@your-domain.com'
```

---

## Troubleshooting

For common deployment issues, see [Troubleshooting Guide](./troubleshooting.md).

**Quick checks**:
- Logs: `docker compose logs -f <service>`
- Resource usage: `docker stats`
- Network: `docker network inspect chat_default`
- Volumes: `docker volume ls`

---

## References

- [System Architecture](../architecture/system-overview.md)
- [Troubleshooting Guide](./troubleshooting.md)
- [Docker Compose Files](../../docker-compose.yml)
- [Monitoring Guide](../../DOC_REVISADA/09-OBSERVABILIDADE-E-MONITORAMENTO.md)
