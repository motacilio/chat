# 11 - Scripts e Deployment

**Versão**: 1.0  
**Status**: ✅ Implementado

---

## Docker Compose

### Arquivo Principal

**Arquivo**: `docker-compose.yml`

```yaml
version: '3.8'

services:
  mongodb:
    image: mongo:7.0
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db
    environment:
      MONGO_INITDB_DATABASE: chatdb

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin

  chat-api:
    build: .
    ports:
      - "8081:8081"  # HTTP/REST
      - "9090:9090"  # gRPC
    depends_on:
      - mongodb
      - kafka
      - minio
    environment:
      SPRING_DATA_MONGODB_URI: mongodb://mongodb:27017/chatdb
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      MINIO_URL: http://minio:9000

volumes:
  mongodb_data:
  minio_data:
```

---

## Scripts PowerShell

### start.ps1

**Arquivo**: `start.ps1`

```powershell
Write-Host "Starting Chat Application..." -ForegroundColor Green

# Build JAR
Write-Host "Building application..." -ForegroundColor Yellow
mvn clean package -DskipTests

# Start Docker Compose
Write-Host "Starting Docker containers..." -ForegroundColor Yellow
docker-compose up -d

# Wait for services
Write-Host "Waiting for services to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Show logs
Write-Host "Application started! Logs:" -ForegroundColor Green
docker-compose logs -f chat-api
```

**Executar**:
```powershell
.\start.ps1
```

### stop.ps1

**Arquivo**: `stop.ps1`

```powershell
Write-Host "Stopping Chat Application..." -ForegroundColor Red

docker-compose down

Write-Host "All containers stopped." -ForegroundColor Green
```

---

## Dockerfile

**Arquivo**: `Dockerfile`

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/meu-projeto-chat-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8081 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Inicialização

### Sequência de Startup

```
1. start.ps1
   ├─ mvn clean package (build JAR)
   ├─ docker-compose up -d (inicia containers)
   │   ├─ Zookeeper (2181)
   │   ├─ Kafka (9092)
   │   ├─ MongoDB (27017)
   │   ├─ MinIO (9000)
   │   └─ chat-api (8081, 9090)
   └─ docker-compose logs -f (mostra logs)

2. Verificar Health
   - GET http://localhost:8081/actuator/health

3. Seed Data (opcional)
   - .\scripts\setup\seed-test-data.ps1
```

---

## Seed Scripts

### seed-test-data.ps1

**Arquivo**: `scripts/setup/seed-test-data.ps1`

```powershell
# Cria conversas de teste
$baseUrl = "http://localhost:8081"

# Login
$token = (Invoke-RestMethod -Method POST -Uri "$baseUrl/api/auth/login" `
    -Body (@{username="alice";password="password123"} | ConvertTo-Json) `
    -ContentType "application/json").token

# Criar conversa
Invoke-RestMethod -Method POST -Uri "$baseUrl/api/conversations" `
    -Headers @{Authorization="Bearer $token"} `
    -Body (@{participant_ids=@("user-456","user-789")} | ConvertTo-Json) `
    -ContentType "application/json"

Write-Host "Test data seeded successfully!" -ForegroundColor Green
```

---

## Monitoramento

### docker-compose.monitoring.yml

**Arquivo**: `docker-compose.monitoring.yml`

```yaml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9091:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana_data:/var/lib/grafana

volumes:
  grafana_data:
```

**Iniciar monitoramento**:
```powershell
docker-compose -f docker-compose.monitoring.yml up -d
```

---

## Troubleshooting

### Kafka não conecta

```powershell
# Verificar logs
docker-compose logs kafka

# Recriar containers
docker-compose down -v
docker-compose up -d
```

### MongoDB connection refused

```powershell
# Verificar se MongoDB está rodando
docker-compose ps mongodb

# Conectar manualmente
docker exec -it chat_mongodb_1 mongosh chatdb
```

---

## Comandos Úteis

```powershell
# Build sem testes
mvn clean package -DskipTests

# Logs de um serviço específico
docker-compose logs -f chat-api

# Restart de um serviço
docker-compose restart chat-api

# Limpar volumes (CUIDADO: perde dados)
docker-compose down -v

# Ver métricas
curl http://localhost:8081/actuator/metrics

# Verificar tópicos Kafka
docker exec -it chat_kafka_1 kafka-topics --list --bootstrap-server localhost:9092
```

---

## Referências

- **Docker Compose**: `docker-compose.yml`, `docker-compose.monitoring.yml`
- **Scripts**: `start.ps1`, `stop.ps1`, `scripts/setup/`, `scripts/load-test/`
- **Dockerfile**: `Dockerfile`
- **Configurações**: `application.yml`, `prometheus.yml`

---

**Fim da Documentação** - Índice completo: [00-INDICE.md](00-INDICE.md)
