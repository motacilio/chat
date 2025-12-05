# Script para iniciar Cloudflare Tunnel temporário
# Uso: .\scripts\start-tunnel.ps1

Write-Host "=== Cloudflare Tunnel - Teste Rápido ===" -ForegroundColor Cyan
Write-Host ""

# Recarregar PATH para garantir que cloudflared está disponível
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

# Verificar se cloudflared está instalado
$cloudflared = Get-Command cloudflared -ErrorAction SilentlyContinue
if (-not $cloudflared) {
    Write-Host "❌ Erro: cloudflared não encontrado" -ForegroundColor Red
    Write-Host "Instale com: winget install --id Cloudflare.cloudflared" -ForegroundColor Yellow
    exit 1
}

Write-Host "✓ Cloudflared encontrado: $($cloudflared.Source)" -ForegroundColor Green

# Verificar se servidor está rodando
Write-Host "Verificando servidor na porta 8081..." -ForegroundColor Yellow
$serverRunning = Test-NetConnection -ComputerName localhost -Port 8081 -InformationLevel Quiet -WarningAction SilentlyContinue

if (-not $serverRunning) {
    Write-Host "❌ Servidor não está rodando na porta 8081" -ForegroundColor Red
    Write-Host "Inicie o servidor primeiro com: .\restart.ps1" -ForegroundColor Yellow
    exit 1
}

Write-Host "✓ Servidor rodando na porta 8081" -ForegroundColor Green
Write-Host ""

# Iniciar tunnel
Write-Host "Iniciando Cloudflare Tunnel..." -ForegroundColor Cyan
Write-Host "Aguarde... o tunnel pode levar alguns segundos para iniciar" -ForegroundColor Yellow
Write-Host ""
Write-Host "A URL pública será exibida abaixo:" -ForegroundColor Cyan
Write-Host "Exemplo: https://random-name-1234.trycloudflare.com" -ForegroundColor Gray
Write-Host ""
Write-Host "Para TESTAR o webhook:" -ForegroundColor Yellow
Write-Host "  curl https://[URL_GERADA]/actuator/health" -ForegroundColor Gray
Write-Host ""
Write-Host "Para CONFIGURAR webhook do WhatsApp:" -ForegroundColor Yellow
Write-Host "  Use a URL: https://[URL_GERADA]/api/webhooks/whatsapp" -ForegroundColor Gray
Write-Host ""
Write-Host "Pressione Ctrl+C para encerrar o tunnel" -ForegroundColor Red
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host ""

# Executar cloudflared
& cloudflared tunnel --url http://localhost:8081
