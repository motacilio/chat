#!/usr/bin/env pwsh
# Teste E2E Simplificado - Apenas Mensagens gRPC

param(
    [string]$ApiUrl = "http://localhost:8081",
    [string]$GrpcUrl = "localhost:9090"
)

$ErrorActionPreference = "Continue"
$WorkspaceRoot = Split-Path -Parent $PSScriptRoot
$ProtoPath = Join-Path $WorkspaceRoot "src\main\proto"
$LogFile = Join-Path $PSScriptRoot "test-simple-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "[$timestamp] [$Level] $Message"
    Add-Content -Path $LogFile -Value $logMessage
    
    switch ($Level) {
        "SUCCESS" { Write-Host $logMessage -ForegroundColor Green }
        "ERROR"   { Write-Host $logMessage -ForegroundColor Red }
        "WARN"    { Write-Host $logMessage -ForegroundColor Yellow }
        default   { Write-Host $logMessage -ForegroundColor Cyan }
    }
}

Write-Log "=== TESTE E2E SIMPLES - APENAS MENSAGENS ===" "INFO"
Write-Log "API URL: $ApiUrl" "INFO"
Write-Log "gRPC URL: $GrpcUrl" "INFO"
Write-Log "Proto Path: $ProtoPath" "INFO"

# Health check HTTP
try {
    $health = Invoke-RestMethod -Uri "$ApiUrl/actuator/health" -Method Get -TimeoutSec 5
    Write-Log "API Health: $($health.status)" "SUCCESS"
} catch {
    Write-Log "ERRO no health check: $($_.Exception.Message)" "ERROR"
    exit 1
}

# Gerar IDs válidos (UUID format)
$conversationId = [guid]::NewGuid().ToString()
$userId1 = [guid]::NewGuid().ToString()  # UUID válido ao invés de "user-alice"
$userId2 = [guid]::NewGuid().ToString()  # UUID válido ao invés de "user-bob"
$messageId = [guid]::NewGuid().ToString()

Write-Log "Conversation ID: $conversationId" "INFO"
Write-Log "User 1 (Alice): $userId1" "INFO"
Write-Log "User 2 (Bob): $userId2" "INFO"
Write-Log "Message ID: $messageId" "INFO"

# Teste 1: Enviar mensagem (SendMessage)
Write-Log "=== TESTE 1: Enviar Mensagem ===" "INFO"

# Criar arquivo temporário JSON (evitar problemas com quotes no -d)
$msgRequest = @{
    message_id = $messageId
    conversation_id = $conversationId
    sender_id = $userId1
    recipient_id = $userId2
    message_text = "Teste E2E - Mensagem simples via gRPC"
} | ConvertTo-Json -Compress

$requestFile = Join-Path $PSScriptRoot "temp-request.json"
$msgRequest | Out-File -FilePath $requestFile -Encoding utf8 -Force

Write-Log "Enviando mensagem..." "INFO"
$msgResponse = Get-Content $requestFile | grpcurl -plaintext -import-path $ProtoPath -proto chat_service.proto -d '@' $GrpcUrl chat_api.v1.ChatService/SendMessage 2>&1

Remove-Item $requestFile -Force -ErrorAction SilentlyContinue

if ($LASTEXITCODE -eq 0) {
    Write-Log "Mensagem enviada com sucesso!" "SUCCESS"
    Write-Log "Resposta: $msgResponse" "INFO"
} else {
    Write-Log "ERRO ao enviar mensagem: $msgResponse" "ERROR"
    exit 1
}

Start-Sleep -Seconds 3

# Teste 2: Consultar status (GetMessageStatus)
Write-Log "=== TESTE 2: Consultar Status ===" "INFO"

$statusRequest = @{
    message_id = $messageId
} | ConvertTo-Json -Compress

$statusFile = Join-Path $PSScriptRoot "temp-status.json"
$statusRequest | Out-File -FilePath $statusFile -Encoding utf8 -Force

$statusResponse = Get-Content $statusFile | grpcurl -plaintext -import-path $ProtoPath -proto chat_service.proto -d '@' $GrpcUrl chat_api.v1.ChatService/GetMessageStatus 2>&1

Remove-Item $statusFile -Force -ErrorAction SilentlyContinue

if ($LASTEXITCODE -eq 0) {
    Write-Log "Status consultado com sucesso!" "SUCCESS"
    Write-Log "Resposta: $statusResponse" "INFO"
} else {
    Write-Log "ERRO ao consultar status: $statusResponse" "ERROR"
}

# Teste 3: Marcar como lida (MarkMessageAsRead)
Write-Log "=== TESTE 3: Marcar como Lida ===" "INFO"

$readRequest = @{
    message_id = $messageId
    user_id = $userId2
} | ConvertTo-Json -Compress

$readFile = Join-Path $PSScriptRoot "temp-read.json"
$readRequest | Out-File -FilePath $readFile -Encoding utf8 -Force

$readResponse = Get-Content $readFile | grpcurl -plaintext -import-path $ProtoPath -proto chat_service.proto -d '@' $GrpcUrl chat_api.v1.ChatService/MarkMessageAsRead 2>&1

Remove-Item $readFile -Force -ErrorAction SilentlyContinue

if ($LASTEXITCODE -eq 0) {
    Write-Log "Mensagem marcada como lida!" "SUCCESS"
    Write-Log "Resposta: $readResponse" "INFO"
} else {
    Write-Log "ERRO ao marcar como lida: $readResponse" "ERROR"
}

# Resumo final
Write-Log "=== RESUMO ===" "SUCCESS"
Write-Log "Log salvo em: $LogFile" "INFO"

Write-Log "=== TESTE CONCLUÍDO ===" "SUCCESS"
exit 0
