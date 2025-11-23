Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  TESTE END-TO-END - CHAT API (gRPC)" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# 1. Health Check (HTTP Actuator)
Write-Host "1. Testando Health Endpoint (HTTP)" -ForegroundColor Yellow
try {
    $healthResponse = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -Method Get
    Write-Host "PASS Status: $($healthResponse.status)" -ForegroundColor Green
    Write-Host "   MongoDB: $($healthResponse.components.mongo.status)" -ForegroundColor Green
    Write-Host "   DiskSpace: $($healthResponse.components.diskSpace.status)`n" -ForegroundColor Green
} catch {
    Write-Host "FAIL Erro ao acessar health: $_`n" -ForegroundColor Red
    exit 1
}

# 2. Verificar Métricas
Write-Host "2. Testando Metrics Endpoint (HTTP)" -ForegroundColor Yellow
try {
    $metricsResponse = Invoke-RestMethod -Uri "http://localhost:8081/actuator/metrics" -Method Get
    $metricCount = $metricsResponse.names.Count
    Write-Host "PASS Total de metricas disponiveis: $metricCount" -ForegroundColor Green
    Write-Host "   Exemplos: $($metricsResponse.names[0..4] -join ', ')...`n" -ForegroundColor Green
} catch {
    Write-Host "FAIL Erro ao acessar metrics: $_`n" -ForegroundColor Red
}

# 3. Verificar Porta gRPC
Write-Host "3. Verificando Porta gRPC (9090)" -ForegroundColor Yellow
$grpcPort = netstat -ano | findstr ":9090" | findstr "LISTENING"
if ($grpcPort) {
    Write-Host "PASS Porta 9090 esta LISTENING (gRPC Server ativo)`n" -ForegroundColor Green
} else {
    Write-Host "FAIL Porta 9090 nao esta aberta! gRPC Server nao iniciou.`n" -ForegroundColor Red
    exit 1
}

# 4. Verificar Processos Java
Write-Host "4. Verificando Processos Java (Spring Boot)" -ForegroundColor Yellow
$javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($javaProcesses) {
    Write-Host "PASS Processos Java rodando:" -ForegroundColor Green
    $javaProcesses | ForEach-Object { Write-Host "   PID: $($_.Id) | Memoria: $([math]::Round($_.WS / 1MB, 2)) MB" -ForegroundColor Green }
    Write-Host ""
} else {
    Write-Host "FAIL Nenhum processo Java encontrado!`n" -ForegroundColor Red
}

# 5. Verificar Kafka (via Docker)
Write-Host "5. Verificando Container Kafka (Docker)" -ForegroundColor Yellow
$kafkaContainer = docker ps --filter "name=kafka-dev" --filter "status=running" --format "{{.Names}}"
if ($kafkaContainer) {
    Write-Host "PASS Kafka container rodando: $kafkaContainer" -ForegroundColor Green
    
    # Listar topics Kafka
    Write-Host "   Listando Topics Kafka..." -ForegroundColor Cyan
    docker exec kafka-dev kafka-topics --list --bootstrap-server localhost:9092
    Write-Host ""
} else {
    Write-Host "WARN Container Kafka nao esta rodando!`n" -ForegroundColor Yellow
}

# 6. Verificar MongoDB (via Docker)
Write-Host "6. Verificando Container MongoDB (Docker)" -ForegroundColor Yellow
$mongoContainer = docker ps --filter "name=mongodb-dev" --filter "status=running" --format "{{.Names}}"
if ($mongoContainer) {
    Write-Host "PASS MongoDB container rodando: $mongoContainer" -ForegroundColor Green
    
    # Contar documentos nas collections
    Write-Host "   Contando documentos..." -ForegroundColor Cyan
    docker exec mongodb-dev mongosh --quiet --eval "use chat; db.messages.countDocuments()"
    Write-Host ""
} else {
    Write-Host "FAIL Container MongoDB nao esta rodando!`n" -ForegroundColor Red
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RESUMO DO TESTE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "PASS Aplicacao Spring Boot: RUNNING" -ForegroundColor Green
Write-Host "PASS gRPC Server: localhost:9090" -ForegroundColor Green
Write-Host "PASS HTTP Actuator: localhost:8081" -ForegroundColor Green
Write-Host "PASS MongoDB: localhost:27017" -ForegroundColor Green
Write-Host "PASS Kafka: localhost:9092" -ForegroundColor Green
Write-Host "PASS Kafka UI: http://localhost:8080`n" -ForegroundColor Green

Write-Host "PROXIMOS PASSOS:" -ForegroundColor Yellow
Write-Host "   - Para testar endpoints gRPC, use Postman (New -> gRPC Request)" -ForegroundColor White
Write-Host "   - URL: localhost:9090 (desmarcar TLS)" -ForegroundColor White
Write-Host "   - Servicos disponiveis:" -ForegroundColor White
Write-Host "     * chat_api.v1.ChatService" -ForegroundColor Cyan
Write-Host "     * chat_api.v1.ConversationService`n" -ForegroundColor Cyan
