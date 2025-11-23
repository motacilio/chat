# =============================================================================
# Script de Parada - Chat API
# =============================================================================
# Descrição: Para toda a infraestrutura e aplicação
# Autor: Marcos Pereira
# Data: 23/11/2025
# =============================================================================

param(
    [switch]$RemoveData
)

$GREEN = "Green"
$YELLOW = "Yellow"
$RED = "Red"

Write-Host "`n=== Parando Chat API ===" -ForegroundColor $GREEN

# =============================================================================
# Parar Spring Boot
# =============================================================================

Write-Host "`n1. Parando Spring Boot..." -ForegroundColor $YELLOW

# Buscar processos Java na porta 9090
$javaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
    $id = $_.Id
    (netstat -ano | Select-String ":9090" | Select-String $id) -ne $null
}

if ($javaProcesses) {
    foreach ($proc in $javaProcesses) {
        Write-Host "  ✓ Finalizando Java PID $($proc.Id)..." -ForegroundColor $YELLOW
        Stop-Process -Id $proc.Id -Force
    }
    Start-Sleep -Seconds 2
    Write-Host "  ✓ Spring Boot parado" -ForegroundColor $GREEN
} else {
    Write-Host "  • Nenhum processo Spring Boot encontrado" -ForegroundColor $YELLOW
}

# =============================================================================
# Parar Docker Compose
# =============================================================================

Write-Host "`n2. Parando Docker Compose..." -ForegroundColor $YELLOW

if ($RemoveData) {
    Write-Host "  ⚠ Removendo containers E dados (volumes)..." -ForegroundColor $RED
    docker-compose -f docker-compose.dev.yml down -v
} else {
    Write-Host "  • Removendo apenas containers (preservando dados)..." -ForegroundColor $YELLOW
    docker-compose -f docker-compose.dev.yml down
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✓ Containers Docker parados" -ForegroundColor $GREEN
} else {
    Write-Host "  ✗ Erro ao parar containers" -ForegroundColor $RED
}

# =============================================================================
# Verificar Portas
# =============================================================================

Write-Host "`n3. Verificando portas..." -ForegroundColor $YELLOW

$ports = @(9090, 8081, 27017, 9092, 2181, 8080)
$allClear = $true

foreach ($port in $ports) {
    $connection = Test-NetConnection -ComputerName localhost -Port $port -WarningAction SilentlyContinue
    if ($connection.TcpTestSucceeded) {
        Write-Host "  ⚠ Porta $port ainda ocupada" -ForegroundColor $YELLOW
        $allClear = $false
    }
}

if ($allClear) {
    Write-Host "  ✓ Todas as portas liberadas" -ForegroundColor $GREEN
}

# =============================================================================
# Resumo
# =============================================================================

Write-Host "`n========================================" -ForegroundColor $GREEN
Write-Host "   PARADA CONCLUÍDA" -ForegroundColor $GREEN
Write-Host "========================================" -ForegroundColor $GREEN

if ($RemoveData) {
    Write-Host "`n⚠ Dados do MongoDB e Kafka foram REMOVIDOS" -ForegroundColor $RED
} else {
    Write-Host "`n✓ Dados preservados em volumes Docker" -ForegroundColor $GREEN
    Write-Host "  Para remover dados: .\stop.ps1 -RemoveData" -ForegroundColor $YELLOW
}

Write-Host "`nPara reiniciar: .\start.ps1`n" -ForegroundColor $YELLOW
