# ghz Load Test - Send Messages
# Teste de carga gRPC para envio de mensagens

$ErrorActionPreference = "Stop"

Write-Host "`n=== GHZ LOAD TEST - SEND MESSAGES ===" -ForegroundColor Cyan

# Verificar se ghz está instalado
try {
    $ghzVersion = ghz --version 2>&1
    Write-Host "✅ ghz instalado: $ghzVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ ghz não encontrado. Execute: .\scripts\load-test\install-ghz.ps1" -ForegroundColor Red
    exit 1
}

# Verificar se API está rodando
Write-Host "`nVerificando conectividade..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -TimeoutSec 5
    if ($health.status -eq "UP") {
        Write-Host "✅ API está UP" -ForegroundColor Green
    }
} catch {
    Write-Host "❌ API não está respondendo em localhost:8081" -ForegroundColor Red
    Write-Host "Execute: mvn spring-boot:run" -ForegroundColor Yellow
    exit 1
}

# Criar diretório de resultados
if (-not (Test-Path results)) {
    mkdir results | Out-Null
}

# Parâmetros do teste
$protoFile = "src/main/proto/chat_service.proto"
$importPath = "src/main/proto"
$service = "chat_api.v1.ChatService/SendMessage"
$host = "localhost:9090"

# Gerar message_id único usando GUID
$messageId = [guid]::NewGuid().ToString()
$conversationId = "conv-load-test-001"
$senderId = "a1a1a1a1-1111-1111-1111-111111111111"

$data = @"
{
  "message_id": "$messageId",
  "conversation_id": "$conversationId",
  "sender_id": "$senderId",
  "message_text": "Load test message {{.RequestNumber}}"
}
"@

Write-Host "`n=== CONFIGURAÇÃO DO TESTE ===" -ForegroundColor Cyan
Write-Host "Proto: $protoFile" -ForegroundColor White
Write-Host "Service: $service" -ForegroundColor White
Write-Host "Host: $host" -ForegroundColor White
Write-Host "Conversation ID: $conversationId" -ForegroundColor White
Write-Host "Sender ID: $senderId" -ForegroundColor White

# Menu de teste
Write-Host "`n=== ESCOLHA O TIPO DE TESTE ===" -ForegroundColor Cyan
Write-Host "1. Teste rápido (10 requisições)" -ForegroundColor White
Write-Host "2. Teste médio (100 requisições, 10 RPS)" -ForegroundColor White
Write-Host "3. Teste de carga (1000 requisições, 100 RPS)" -ForegroundColor White
Write-Host "4. Teste intenso (5000 requisições, 500 RPS)" -ForegroundColor White
Write-Host "5. Teste customizado" -ForegroundColor White

$choice = Read-Host "`nOpção"

switch ($choice) {
    "1" {
        Write-Host "`n=== TESTE RÁPIDO ===" -ForegroundColor Yellow
        $params = @(
            "-n", "10",
            "-c", "1"
        )
    }
    "2" {
        Write-Host "`n=== TESTE MÉDIO ===" -ForegroundColor Yellow
        $params = @(
            "-n", "100",
            "--rps", "10",
            "-c", "5"
        )
    }
    "3" {
        Write-Host "`n=== TESTE DE CARGA ===" -ForegroundColor Yellow
        $params = @(
            "-n", "1000",
            "--rps", "100",
            "-c", "50"
        )
    }
    "4" {
        Write-Host "`n=== TESTE INTENSO ===" -ForegroundColor Yellow
        $params = @(
            "-n", "5000",
            "--rps", "500",
            "-c", "100"
        )
    }
    "5" {
        Write-Host "`n=== TESTE CUSTOMIZADO ===" -ForegroundColor Yellow
        $n = Read-Host "Total de requisições"
        $rps = Read-Host "Requests per second (RPS)"
        $c = Read-Host "Concorrência (workers)"
        $params = @(
            "-n", $n,
            "--rps", $rps,
            "-c", $c
        )
    }
    default {
        Write-Host "Opção inválida!" -ForegroundColor Red
        exit 1
    }
}

# Executar teste
Write-Host "`nIniciando teste..." -ForegroundColor Yellow
Write-Host "Parâmetros: $($params -join ' ')" -ForegroundColor Gray

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$outputFile = "results/ghz-messages-$timestamp.json"

$ghzArgs = @(
    "--insecure",
    "--proto", $protoFile,
    "--import-paths", $importPath,
    "--call", $service,
    "-d", $data,
    "-o", $outputFile
) + $params + @($host)

Write-Host "Comando: ghz $($ghzArgs -join ' ')" -ForegroundColor Gray
Write-Host ""

& ghz @ghzArgs

# Exibir resumo
if (Test-Path $outputFile) {
    Write-Host "`n=== ANÁLISE DE RESULTADOS ===" -ForegroundColor Cyan
    
    $results = Get-Content $outputFile | ConvertFrom-Json
    
    Write-Host "`n📊 Estatísticas:" -ForegroundColor White
    Write-Host "  Total de requisições: $($results.count)" -ForegroundColor Gray
    Write-Host "  Duração total: $($results.total)" -ForegroundColor Gray
    Write-Host "  Requests/sec: $([Math]::Round($results.rps, 2))" -ForegroundColor Gray
    
    Write-Host "`n⏱️  Latência:" -ForegroundColor White
    Write-Host "  Média: $($results.average)" -ForegroundColor Gray
    Write-Host "  Mais rápida: $($results.fastest)" -ForegroundColor Gray
    Write-Host "  Mais lenta: $($results.slowest)" -ForegroundColor Gray
    
    if ($results.latencyDistribution) {
        Write-Host "`n📈 Percentis:" -ForegroundColor White
        $results.latencyDistribution | ForEach-Object {
            Write-Host "  p$($_.percentage): $($_.latency)" -ForegroundColor Gray
        }
    }
    
    Write-Host "`n✅ Status Codes:" -ForegroundColor White
    if ($results.statusCodeDistribution) {
        $results.statusCodeDistribution | ForEach-Object {
            $color = if ($_.code -eq "OK") { "Green" } else { "Red" }
            Write-Host "  [$($_.code)] $($_.count) responses" -ForegroundColor $color
        }
    }
    
    if ($results.errorDistribution -and $results.errorDistribution.Count -gt 0) {
        Write-Host "`n❌ Erros:" -ForegroundColor Red
        $results.errorDistribution | ForEach-Object {
            Write-Host "  [$($_.count)] $($_.error)" -ForegroundColor Yellow
        }
    }
    
    Write-Host "`nResultados salvos em: $outputFile" -ForegroundColor Cyan
}

Write-Host "`nTeste concluido!" -ForegroundColor Green
