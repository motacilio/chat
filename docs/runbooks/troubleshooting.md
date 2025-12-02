# Troubleshooting Guide

**Version**: 1.0  
**Last Updated**: 2025-11-29  
**Audience**: DevOps Engineers, Developers

---

## Table of Contents

1. [General Diagnostic Commands](#general-diagnostic-commands)
2. [Common Deployment Issues](#common-deployment-issues)
3. [MongoDB Issues](#mongodb-issues)
4. [Kafka Issues](#kafka-issues)
5. [MinIO Issues](#minio-issues)
6. [Chat API Issues](#chat-api-issues)
7. [Performance Issues](#performance-issues)
8. [Network & Connectivity](#network--connectivity)
9. [Data Consistency Issues](#data-consistency-issues)

---

## General Diagnostic Commands

### Check Service Health

```powershell
# All services status
docker compose ps

# Specific service logs
docker compose logs -f chat-api
docker compose logs -f kafka --tail 100

# Service resource usage
docker stats

# Container inspection
docker inspect mongo1
```

### Check Application Health

```powershell
# Health endpoint
curl http://localhost:8081/actuator/health

# Detailed health
curl http://localhost:8081/actuator/health | ConvertFrom-Json | ConvertTo-Json -Depth 10

# Metrics
curl http://localhost:8081/actuator/prometheus | Select-String "messages_sent"
```

### Check Logs by Level

```powershell
# ERROR logs only
docker compose logs chat-api | Select-String "ERROR"

# WARNING logs
docker compose logs chat-api | Select-String "WARN"

# Specific component
docker compose logs chat-api | Select-String "MessageService"
```

---

## Common Deployment Issues

### Issue: Port Already in Use

**Symptom**:
```
Error starting userland proxy: listen tcp 0.0.0.0:9090: bind: address already in use
```

**Diagnosis**:
```powershell
# Find process using port
Get-NetTCPConnection -LocalPort 9090 | Select-Object -Property LocalAddress,LocalPort,State,OwningProcess
Get-Process -Id <OwningProcess>
```

**Solution**:
```powershell
# Option 1: Stop conflicting process
Stop-Process -Id <ProcessId> -Force

# Option 2: Change port in docker-compose.yml
# Edit ports section: "9091:9090" instead of "9090:9090"

# Restart services
docker compose down
docker compose up -d
```

---

### Issue: Container Immediately Exits

**Symptom**:
```
chat-api exited with code 1
```

**Diagnosis**:
```powershell
# Check exit logs
docker compose logs chat-api

# Inspect container
docker inspect <container_id>
```

**Common Causes**:
1. **Missing environment variables** → Check `.env` file
2. **MongoDB not ready** → Add health check dependency
3. **Java heap memory** → Increase `-Xmx` in Dockerfile
4. **Port conflict** → See "Port Already in Use" above

**Solution**:
```powershell
# Add depends_on with health check in docker-compose.yml
depends_on:
  mongo1:
    condition: service_healthy
  kafka:
    condition: service_healthy

# Increase Java heap
ENV JAVA_OPTS="-Xmx2048m -Xms512m"
```

---

### Issue: Docker Compose Hangs on Startup

**Symptom**:
```
Waiting for mongo1 to be healthy... (timeout)
```

**Diagnosis**:
```powershell
# Check individual service
docker compose up mongo1

# Check health check
docker inspect mongo1 | Select-String "Health"
```

**Solution**:
```powershell
# Remove volumes and restart
docker compose down -v
docker compose up -d

# If still fails, recreate containers
docker compose down
docker compose up -d --force-recreate
```

---

## MongoDB Issues

### Issue: Replica Set Not Initialized

**Symptom**:
```
MongoServerError: not master and slaveOk=false
```

**Diagnosis**:
```powershell
# Check replica set status
docker exec -it mongo1 mongosh --eval "rs.status()"

# Check if initiated
docker exec -it mongo1 mongosh --eval "rs.conf()"
```

**Solution**:
```powershell
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

# Wait 10 seconds for election
Start-Sleep -Seconds 10

# Verify status
docker exec -it mongo1 mongosh --eval "rs.status().ok"
# Expected: 1
```

---

### Issue: Connection Pool Exhausted

**Symptom**:
```
MongoTimeoutException: Timed out after 30000 ms while waiting for a server
```

**Diagnosis**:
```powershell
# Check active connections
docker exec -it mongo1 mongosh --eval "db.serverStatus().connections"

# Check application logs
docker compose logs chat-api | Select-String "MongoTimeoutException"
```

**Solution**:
```yaml
# Increase connection pool in application.yml
spring:
  data:
    mongodb:
      uri: mongodb://mongo1:27017,mongo2:27017,mongo3:27017/chat_db?replicaSet=rs0&maxPoolSize=50
```

```powershell
# Restart Chat API
docker compose restart chat-api
```

---

### Issue: Slow Query Performance

**Symptom**:
```
Query took 5000ms (messages collection)
```

**Diagnosis**:
```powershell
# Connect to MongoDB
docker exec -it mongo1 mongosh

# Check slow queries
use chat_db
db.setProfilingLevel(1, { slowms: 100 })
db.system.profile.find().sort({ ts: -1 }).limit(5).pretty()

# Check indexes
db.messages.getIndexes()
```

**Solution**:
```javascript
// Create missing index
db.messages.createIndex({ conversation_id: 1, sequence_number: 1 })

// For text search
db.messages.createIndex({ message_text: "text" })

// Check index usage
db.messages.find({ conversation_id: "..." }).explain("executionStats")
```

---

## Kafka Issues

### Issue: Kafka Not Ready

**Symptom**:
```
org.apache.kafka.common.errors.TimeoutException: Topic message-events not present in metadata
```

**Diagnosis**:
```powershell
# Check Kafka broker status
docker compose logs kafka | Select-String "started"

# List topics
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
```

**Solution**:
```powershell
# Restart Kafka and Zookeeper
docker compose restart zookeeper kafka

# Wait for Kafka to be ready
Start-Sleep -Seconds 15

# Create topics manually
docker exec -it kafka kafka-topics --create `
  --bootstrap-server localhost:9092 `
  --topic message-events `
  --partitions 3 `
  --replication-factor 1
```

---

### Issue: Consumer Lag Growing

**Symptom**:
```
Consumer group 'message-delivery-group' lag: 10,000 messages
```

**Diagnosis**:
```powershell
# Check consumer group lag
docker exec -it kafka kafka-consumer-groups `
  --bootstrap-server localhost:9092 `
  --group message-delivery-group `
  --describe

# Check partition assignment
docker exec -it kafka kafka-consumer-groups `
  --bootstrap-server localhost:9092 `
  --group message-delivery-group `
  --members --verbose
```

**Solutions**:

**Option 1: Scale consumers**
```yaml
# docker-compose.yml
chat-api:
  deploy:
    replicas: 3  # 3 instances for 3 partitions
```

**Option 2: Increase poll records**
```yaml
# application.yml
spring:
  kafka:
    consumer:
      max-poll-records: 50  # Process 50 messages per poll
```

**Option 3: Optimize processing**
```java
// Batch processing in MessageDeliveryWorker
@KafkaListener(topics = "message-events", batch = "true")
public void consumeBatch(List<MessageEvent> events) {
    messageRepository.saveAll(events.stream()
        .map(this::toMessage)
        .collect(Collectors.toList()));
}
```

---

### Issue: Message Ordering Violation

**Symptom**:
```
sequence_number out of order: expected 5, got 7
```

**Diagnosis**:
```powershell
# Check partition assignment
docker exec -it kafka kafka-consumer-groups `
  --bootstrap-server localhost:9092 `
  --group message-delivery-group `
  --describe

# Check producer configuration
docker compose logs chat-api | Select-String "ProducerConfig"
```

**Root Cause**: Messages for same conversation_id going to different partitions

**Solution**:
```java
// Ensure partitioning by conversation_id (already implemented)
kafkaTemplate.send("message-events", event.getConversationId(), event);
```

```powershell
# Verify by consuming messages
docker exec -it kafka kafka-console-consumer `
  --bootstrap-server localhost:9092 `
  --topic message-events `
  --property print.key=true `
  --from-beginning `
  --max-messages 10
```

---

## MinIO Issues

### Issue: Cannot Upload Files

**Symptom**:
```
S3 error: Access Denied (403)
```

**Diagnosis**:
```powershell
# Check MinIO health
curl http://localhost:9000/minio/health/live

# Check bucket exists
docker exec -it minio mc ls local/
```

**Solution**:
```powershell
# Create bucket manually
docker exec -it minio mc mb local/chat-files

# Set public read policy (if needed)
docker exec -it minio mc anonymous set download local/chat-files
```

---

### Issue: Pre-Signed URL Expired

**Symptom**:
```
Request has expired (403)
```

**Diagnosis**:
```powershell
# Check current time
date

# Check MinIO server time
docker exec -it minio date
```

**Root Cause**: Clock skew between client and MinIO server

**Solution**:
```powershell
# Sync Docker container time with host
docker compose restart minio

# OR increase expiration time in application.yml
minio:
  download-url-expiration-seconds: 7200  # 2 hours
```

---

## Chat API Issues

### Issue: gRPC UNAVAILABLE Error

**Symptom**:
```
io.grpc.StatusRuntimeException: UNAVAILABLE: io exception
```

**Diagnosis**:
```powershell
# Check gRPC port
Test-NetConnection -ComputerName localhost -Port 9090

# Check application logs
docker compose logs chat-api | Select-String "gRPC"
```

**Solution**:
```powershell
# Restart Chat API
docker compose restart chat-api

# Check if service started successfully
docker compose logs chat-api | Select-String "Started ChatApplication"

# Test with grpcurl
grpcurl -plaintext localhost:9090 list
```

---

### Issue: Rate Limit Exceeded

**Symptom**:
```
RESOURCE_EXHAUSTED: Rate limit exceeded for user user123
```

**Diagnosis**:
```powershell
# Check rate limit configuration
docker compose logs chat-api | Select-String "RateLimitService"

# Check metrics
curl http://localhost:8081/actuator/prometheus | Select-String "rate_limit"
```

**Solution**:
```yaml
# Increase rate limit in application.yml
resilience4j:
  ratelimiter:
    instances:
      sendMessage:
        limitForPeriod: 200  # Increase from 100 to 200
        limitRefreshPeriod: 60s
```

```powershell
# Restart Chat API
docker compose restart chat-api
```

---

### Issue: Circuit Breaker Open

**Symptom**:
```
CircuitBreakerOpenException: Circuit breaker is OPEN for MinIO
```

**Diagnosis**:
```powershell
# Check circuit breaker state
curl http://localhost:8081/actuator/health | ConvertFrom-Json | Select -ExpandProperty components | Select -ExpandProperty circuitBreakers

# Check failure metrics
curl http://localhost:8081/actuator/prometheus | Select-String "resilience4j_circuitbreaker"
```

**Solution**:
```powershell
# Wait for circuit breaker to transition to HALF_OPEN (default: 60 seconds)
Start-Sleep -Seconds 60

# OR restart dependent service (e.g., MinIO)
docker compose restart minio

# Force circuit breaker reset (emergency only)
curl -X POST http://localhost:8081/actuator/circuitbreakers/minio/reset
```

---

## Performance Issues

### Issue: High Latency (p95 >100ms)

**Diagnosis**:
```powershell
# Run performance benchmark
cd scripts/load-test
.\run-benchmark.ps1 -Quick

# Check metrics
curl http://localhost:8081/actuator/prometheus | Select-String "message_validation_latency"

# Check resource usage
docker stats
```

**Common Causes & Solutions**:

**1. Database Slow Queries** → Add indexes (see MongoDB section)

**2. High GC Pauses**
```powershell
# Check GC metrics
curl http://localhost:8081/actuator/prometheus | Select-String "jvm_gc_pause"

# Increase heap size
# Edit Dockerfile: -Xmx2048m
docker compose up -d --build chat-api
```

**3. Kafka Consumer Lag** → Scale consumers (see Kafka section)

**4. Network Latency**
```powershell
# Test network latency
docker exec -it chat-api ping mongo1
docker exec -it chat-api ping kafka
```

---

### Issue: High Memory Usage

**Symptom**:
```
OutOfMemoryError: Java heap space
```

**Diagnosis**:
```powershell
# Check heap usage
curl http://localhost:8081/actuator/metrics/jvm.memory.used

# Heap dump (for analysis)
docker exec -it chat-api jmap -dump:live,format=b,file=/tmp/heap.hprof 1
docker cp chat-api:/tmp/heap.hprof ./heap.hprof
# Analyze with VisualVM or Eclipse MAT
```

**Solution**:
```dockerfile
# Increase heap in Dockerfile
ENV JAVA_OPTS="-Xmx4096m -Xms1024m"
```

```powershell
# Rebuild and restart
docker compose up -d --build chat-api
```

---

## Network & Connectivity

### Issue: Services Cannot Communicate

**Symptom**:
```
UnknownHostException: mongo1: Name or service not known
```

**Diagnosis**:
```powershell
# Check Docker network
docker network inspect chat_default

# Test DNS resolution
docker exec -it chat-api nslookup mongo1
docker exec -it chat-api ping mongo1
```

**Solution**:
```powershell
# Recreate network
docker compose down
docker network prune
docker compose up -d
```

---

### Issue: External Webhooks Not Received

**Symptom**:
```
Telegram webhook: Connection timed out
```

**Diagnosis**:
```powershell
# Check if Chat API is reachable externally
curl https://your-domain.com/api/webhooks/telegram

# Check firewall rules
# (Windows Firewall or cloud security groups)
```

**Solution**:
```powershell
# For development: Use ngrok
ngrok http 8081

# Update webhook URL
$ngrokUrl = "https://abc123.ngrok.io"
curl -X POST "https://api.telegram.org/bot<TOKEN>/setWebhook" `
  -d "url=$ngrokUrl/api/webhooks/telegram" `
  -d "secret_token=<SECRET>"
```

---

## Data Consistency Issues

### Issue: Message Sequence Number Gaps

**Symptom**:
```
sequence_number: 1, 2, 4, 5 (missing 3)
```

**Diagnosis**:
```powershell
# Query MongoDB for gaps
docker exec -it mongo1 mongosh

use chat_db
db.messages.find({ conversation_id: "<UUID>" }).sort({ sequence_number: 1 })
```

**Root Cause**: Race condition in sequence number generation (should never happen with atomic MongoDB findAndModify)

**Solution**:
```javascript
// Verify atomic increment is configured
db.messages.find({ conversation_id: "<UUID>" }).explain()

// Check for duplicate sequence numbers
db.messages.aggregate([
  { $match: { conversation_id: "<UUID>" } },
  { $group: { _id: "$sequence_number", count: { $sum: 1 } } },
  { $match: { count: { $gt: 1 } } }
])
```

---

### Issue: Message Status Not Updating

**Symptom**:
```
Message stuck in SENT status (should be DELIVERED)
```

**Diagnosis**:
```powershell
# Check state-update-events topic
docker exec -it kafka kafka-console-consumer `
  --bootstrap-server localhost:9092 `
  --topic state-update-events `
  --from-beginning `
  --max-messages 10

# Check MessageStateUpdateWorker logs
docker compose logs chat-api | Select-String "MessageStateUpdateWorker"
```

**Solution**:
```powershell
# Republish state update event manually
curl -X POST http://localhost:8081/api/messages/<message_id>/status `
  -H "Content-Type: application/json" `
  -d '{"status": "DELIVERED", "recipient_id": "user456"}'
```

---

## Emergency Procedures

### Complete System Reset (Development Only)

```powershell
# ⚠️ WARNING: This will delete ALL data

# Stop all services
docker compose down -v

# Remove all volumes
docker volume prune -f

# Remove all containers
docker container prune -f

# Restart fresh
docker compose up -d

# Reinitialize MongoDB replica set
Start-Sleep -Seconds 10
docker exec -it mongo1 mongosh --eval "rs.initiate(...)"
```

### Backup Before Troubleshooting

```powershell
# Backup MongoDB
docker exec -it mongo1 mongodump `
  --out /tmp/backup `
  --db chat_db

docker cp mongo1:/tmp/backup ./backups/mongo_$(Get-Date -Format 'yyyy-MM-dd_HH-mm-ss')

# Backup MinIO bucket
docker exec -it minio mc mirror local/chat-files /tmp/backup
docker cp minio:/tmp/backup ./backups/minio_$(Get-Date -Format 'yyyy-MM-dd_HH-mm-ss')
```

---

## Getting Help

If issues persist after trying these solutions:

1. **Check Documentation**: [System Architecture](../architecture/system-overview.md)
2. **Search Logs**: Use `Select-String` to search for error patterns
3. **Enable Debug Logging**: Set `LOG_LEVEL=DEBUG` in `.env`
4. **Collect Diagnostics**:
   ```powershell
   # Save all logs
   docker compose logs > debug_$(Get-Date -Format 'yyyy-MM-dd_HH-mm-ss').log
   
   # Save metrics snapshot
   curl http://localhost:8081/actuator/prometheus > metrics.txt
   ```

5. **Contact Support**: Provide logs, metrics, and reproduction steps

---

## References

- [Deployment Runbook](./deployment.md)
- [Monitoring Guide](../../DOC_REVISADA/09-OBSERVABILIDADE-E-MONITORAMENTO.md)
- [Kafka Documentation](../../DOC_REVISADA/04-KAFKA-E-PROCESSAMENTO-ASSINCRONO.md)
- [MongoDB Documentation](../../DOC_REVISADA/05-MONGODB-E-PERSISTENCIA.md)
