# Teste Contínuo de Webhooks - Simula tráfego real
# Uso: .\continuous-webhook-test.ps1 -Url "http://localhost:8081" -IntervalSeconds 5 -Count 20

param(
    [string]$Url = "http://localhost:8081",
    [int]$IntervalSeconds = 10,
    [int]$Count = 100,
    [switch]$Random = $false
)

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "   🔄 TESTE CONTÍNUO DE WEBHOOKS - MOCK SIMULATOR" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "  URL Target:      $Url" -ForegroundColor White
Write-Host "  Intervalo:       ${IntervalSeconds}s" -ForegroundColor White
Write-Host "  Total Webhooks:  $Count" -ForegroundColor White
Write-Host "  Modo:            $(if ($Random) { 'Aleatório' } else { 'Sequencial' })" -ForegroundColor White
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Verificar se Node.js está instalado
$nodeVersion = node --version 2>$null
if (-not $nodeVersion) {
    Write-Host "❌ Erro: Node.js não está instalado" -ForegroundColor Red
    Write-Host "Instale em: https://nodejs.org" -ForegroundColor Yellow
    exit 1
}

Write-Host "✓ Node.js detectado: $nodeVersion" -ForegroundColor Green
Write-Host ""

# Definir tipos de webhook disponíveis
$platforms = @("whatsapp", "telegram")
$whatsappTypes = @("textMessage", "messageDelivered", "messageRead")
$telegramTypes = @("textMessage", "photoMessage", "callbackQuery")

# Estatísticas
$stats = @{
    Success = 0
    Failed = 0
    StartTime = Get-Date
}

# Função para obter tipo de payload
function Get-PayloadType {
    param(
        [string]$Platform,
        [int]$Index
    )
    
    if ($Random) {
        if ($Platform -eq "whatsapp") {
            return $whatsappTypes | Get-Random
        } else {
            return $telegramTypes | Get-Random
        }
    } else {
        if ($Platform -eq "whatsapp") {
            return $whatsappTypes[$Index % $whatsappTypes.Length]
        } else {
            return $telegramTypes[$Index % $telegramTypes.Length]
        }
    }
}

# Função para exibir progresso
function Show-Progress {
    param(
        [int]$Current,
        [int]$Total,
        [string]$Status
    )
    
    $percent = [math]::Round(($Current / $Total) * 100, 1)
    $barLength = 40
    $filledLength = [math]::Round(($percent / 100) * $barLength)
    $filled = [char]0x2588 * $filledLength
    $empty = [char]0x2591 * ($barLength - $filledLength)
    $bar = $filled + $empty
    
    Write-Host -NoNewline "`r  [$bar] $percent% - $Status"
}

# Loop principal
Write-Host "🚀 Iniciando envio de webhooks..." -ForegroundColor Yellow
Write-Host ""

for ($i = 1; $i -le $Count; $i++) {
    # Selecionar plataforma
    if ($Random) {
        $platform = $platforms | Get-Random
    } else {
        $platform = $platforms[$i % 2]
    }
    
    # Selecionar tipo
    $type = Get-PayloadType -Platform $platform -Index $i
    
    # Exibir progresso
    $status = "[$i/$Count] ${platform}:${type}"
    Show-Progress -Current $i -Total $Count -Status $status
    
    # Enviar webhook
    $result = node scripts\setup\mock-webhook-sender.js `
        --url $Url `
        --platform $platform `
        --type $type 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        $stats.Success++
    } else {
        $stats.Failed++
        Write-Host ""
        Write-Host "  ⚠️  Falha no webhook #$i ($platform - $type)" -ForegroundColor Red
        Write-Host ""
    }
    
    # Aguardar intervalo (exceto na última iteração)
    if ($i -lt $Count) {
        Start-Sleep -Seconds $IntervalSeconds
    }
}

Write-Host ""
Write-Host ""

# Calcular estatísticas finais
$endTime = Get-Date
$duration = $endTime - $stats.StartTime
$avgTime = $duration.TotalSeconds / $Count

Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "   📊 ESTATÍSTICAS FINAIS" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Webhooks Enviados:    $Count" -ForegroundColor White
Write-Host "  Sucesso:              $($stats.Success)" -ForegroundColor Green
Write-Host "  Falhas:               $($stats.Failed)" -ForegroundColor $(if ($stats.Failed -gt 0) { "Red" } else { "Green" })
$successRateStr = ($stats.Success / $Count * 100).ToString("F1")
Write-Host "  Taxa de Sucesso:      ${successRateStr}%" -ForegroundColor $(if ($stats.Failed -eq 0) { "Green" } else { "Yellow" })
Write-Host ""
Write-Host "  Tempo Total:          $($duration.ToString("mm\:ss"))" -ForegroundColor White
$avgTimeStr = $avgTime.ToString("F2")
Write-Host "  Média por Webhook:    ${avgTimeStr}s" -ForegroundColor White
$throughputStr = ($Count / $duration.TotalSeconds).ToString("F2")
Write-Host "  Throughput:           ${throughputStr} webhooks/s" -ForegroundColor White
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

if ($stats.Failed -eq 0) {
    Write-Host "✅ Teste finalizado com sucesso!" -ForegroundColor Green
} else {
    Write-Host "⚠️  Teste finalizado com $($stats.Failed) falha(s)" -ForegroundColor Yellow
    Write-Host "   Verifique os logs do servidor para mais detalhes" -ForegroundColor Gray
}

Write-Host ""
