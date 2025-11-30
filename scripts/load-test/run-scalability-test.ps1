#!/usr/bin/env pwsh
# Run Scalability Test
# 
# Este script executa testes de escalabilidade com 1000+ usuários concorrentes
# e valida se o sistema atende aos SLOs de latência e throughput.
#
# Pré-requisitos:
# 1. Docker Compose com todos os serviços rodando
# 2. k6 instalado (https://k6.io/docs/getting-started/installation/)
# 3. MongoDB replica set inicializado
#
# Uso:
# .\run-scalability-test.ps1 [-Instances <int>] [-Duration <string>]
#
# Exemplos:
# .\run-scalability-test.ps1                      # 1 instância, teste padrão (13 min)
# .\run-scalability-test.ps1 -Instances 3         # 3 instâncias da API
# .\run-scalability-test.ps1 -Duration "5m"       # Teste curto de 5 minutos

[CmdletBinding()]
param(
    [Parameter(HelpMessage="Número de instâncias da API para rodar (default: 1)")]
    [int]$Instances = 1,
    
    [Parameter(HelpMessage="Duração do teste (default: usar stages do script)")]
    [string]$Duration = "",
    
    [Parameter(HelpMessage="Salvar resultados JSON para análise")]
    [switch]$SaveResults
)

$ErrorActionPreference = 'Stop'

# Colors for output
function Write-Success { Write-Host $args -ForegroundColor Green }
function Write-Info { Write-Host $args -ForegroundColor Cyan }
function Write-Warning { Write-Host $args -ForegroundColor Yellow }
function Write-Error { Write-Host $args -ForegroundColor Red }

Write-Info "=== Scalability Test Runner ==="
Write-Info "Target: 1000 concurrent users"
Write-Info "SLOs: p95 < 100ms, success rate > 99%, throughput > 1000 msg/s"
Write-Info ""

# Step 1: Check prerequisites
Write-Info "[1/5] Verificando pré-requisitos..."

# Check k6 installation
if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
    Write-Error "❌ k6 não encontrado. Instale: https://k6.io/docs/getting-started/installation/"
    exit 1
}
Write-Success "✓ k6 instalado"

# Check Docker Compose
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "❌ Docker não encontrado"
    exit 1
}

# Check if containers are running
$runningContainers = docker ps --format "{{.Names}}" | Where-Object { $_ -match "chat-api|mongodb|kafka" }
if ($runningContainers.Count -lt 3) {
    Write-Warning "⚠️  Alguns containers não estão rodando. Execute: docker-compose up -d"
    Write-Info "Containers rodando: $($runningContainers -join ', ')"
    $continue = Read-Host "Continuar mesmo assim? (y/n)"
    if ($continue -ne 'y') {
        exit 1
    }
}
Write-Success "✓ Containers rodando"

# Step 2: Scale API instances if requested
if ($Instances -gt 1) {
    Write-Info "[2/5] Escalando API para $Instances instâncias..."
    docker-compose up -d --scale chat-api=$Instances
    Write-Success "✓ API escalada para $Instances instâncias"
    
    # Wait for instances to be ready
    Write-Info "Aguardando instâncias ficarem prontas (30s)..."
    Start-Sleep -Seconds 30
} else {
    Write-Info "[2/5] Usando 1 instância da API (padrão)"
}

# Step 3: Check health endpoints
Write-Info "[3/5] Verificando health checks..."
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -TimeoutSec 5
    if ($health.status -eq "UP") {
        Write-Success "✓ API health: UP"
    } else {
        Write-Warning "⚠️  API health: $($health.status)"
    }
} catch {
    Write-Error "❌ API health check falhou: $_"
    exit 1
}

# Step 4: Run k6 scalability test
Write-Info "[4/5] Executando teste de escalabilidade..."
Write-Info "Isso pode levar ~13 minutos (ou $Duration se especificado)"
Write-Info ""

$k6Args = @("run", "scripts/load-test/k6-scalability-test.js")

if ($Duration) {
    $k6Args += "--duration", $Duration
}

if ($SaveResults) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $resultFile = "results/scalability-test-$timestamp.json"
    $k6Args += "--out", "json=$resultFile"
    Write-Info "Resultados serão salvos em: $resultFile"
}

# Run k6
try {
    & k6 $k6Args
    $testExitCode = $LASTEXITCODE
} catch {
    Write-Error "❌ Erro ao executar k6: $_"
    exit 1
}

Write-Info ""

# Step 5: Analyze results
Write-Info "[5/5] Analisando resultados..."

if ($testExitCode -eq 0) {
    Write-Success "✅ TESTE PASSOU - Todos os SLOs foram atendidos!"
    Write-Success ""
    Write-Success "SLOs Atendidos:"
    Write-Success "  ✓ Latência p95 < 100ms"
    Write-Success "  ✓ Latência p99 < 200ms"
    Write-Success "  ✓ Taxa de sucesso > 99%"
    Write-Success "  ✓ Throughput > 1000 msg/s"
} else {
    Write-Error "❌ TESTE FALHOU - Alguns SLOs não foram atendidos"
    Write-Error ""
    Write-Error "Verifique os logs acima para detalhes sobre as falhas."
    Write-Error "Possíveis causas:"
    Write-Error "  - Latência p95 > 100ms (sistema sobrecarregado)"
    Write-Error "  - Taxa de erro > 1% (problemas de conectividade ou timeout)"
    Write-Error "  - Throughput < 1000 msg/s (bottleneck no Kafka ou MongoDB)"
}

# Print follow-up actions
Write-Info ""
Write-Info "=== Próximos Passos ==="
Write-Info "1. Verificar métricas no Grafana: http://localhost:3000"
Write-Info "2. Verificar consumer lag no Kafka UI: http://localhost:8080"
Write-Info "3. Verificar logs da API: docker logs chat-api"
Write-Info "4. Se teste falhou, considere:"
Write-Info "   - Aumentar instâncias da API (docker-compose up -d --scale chat-api=3)"
Write-Info "   - Aumentar workers Kafka (ajustar concurrency no KafkaConsumerConfig)"
Write-Info "   - Verificar connection pooling (MongoDB e Kafka)"

if ($SaveResults) {
    Write-Info "5. Analisar resultados detalhados: .\analyze-results.ps1 $resultFile"
}

exit $testExitCode
