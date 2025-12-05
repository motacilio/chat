# Script de Parada - Chat API
# Para toda a infraestrutura e aplicacao

param(
    [switch]$RemoveData
)

# Cores
$GREEN = "Green"
$YELLOW = "Yellow"
$RED = "Red"
$CYAN = "Cyan"
$WHITE = "White"

function Write-Info {
    param([string]$Message)
    Write-Host "  [OK] $Message" -ForegroundColor $CYAN
}

function Write-Warning {
    param([string]$Message)
    Write-Host "  [AVISO] $Message" -ForegroundColor $YELLOW
}

function Write-Error {
    param([string]$Message)
    Write-Host "  [ERRO] $Message" -ForegroundColor $RED
}

function Test-DockerRunning {
    try {
        $null = docker ps 2>&1
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor $CYAN
Write-Host "      CHAT API - PARADA COMPLETA        " -ForegroundColor $YELLOW
Write-Host "========================================" -ForegroundColor $CYAN
Write-Host ""

# Parar Spring Boot
Write-Host ""
Write-Host "=== Passo 1/3: Parando Aplicacao Spring Boot ===" -ForegroundColor $GREEN

$javaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue

if ($javaProcesses) {
    Write-Info "Encontrados $($javaProcesses.Count) processo(s) Java"
    foreach ($proc in $javaProcesses) {
        Write-Info "  - PID: $($proc.Id) | Memoria: $([math]::Round($proc.WorkingSet64/1MB, 2)) MB"
    }
    
    Write-Info "Encerrando processos..."
    foreach ($proc in $javaProcesses) {
        try {
            Stop-Process -Id $proc.Id -Force -ErrorAction Stop
        } catch {
            Write-Warning "Nao foi possivel parar PID $($proc.Id): $($_.Exception.Message)"
        }
    }
    Start-Sleep -Seconds 3
    
    $remainingProcesses = Get-Process -Name java -ErrorAction SilentlyContinue
    if ($remainingProcesses) {
        Write-Warning "Alguns processos Java ainda estao rodando:"
        foreach ($p in $remainingProcesses) {
            Write-Host "    - PID $($p.Id)" -ForegroundColor $YELLOW
        }
    } else {
        Write-Info "Processos Java encerrados com sucesso"
    }
} else {
    Write-Info "Nenhum processo Java em execucao"
}

# Parar Docker Compose
Write-Host ""
Write-Host "=== Passo 2/3: Parando Infraestrutura Docker ===" -ForegroundColor $GREEN

if (-not (Test-DockerRunning)) {
    Write-Warning "Docker Desktop nao esta rodando (containers ja podem estar parados)"
} else {
    if ($RemoveData) {
        Write-Warning "Removendo containers E volumes (DADOS SERAO PERDIDOS)..."
        docker-compose -f docker-compose.dev.yml down -v --remove-orphans
    } else {
        Write-Info "Removendo containers (preservando dados)..."
        docker-compose -f docker-compose.dev.yml down --remove-orphans
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Info "Containers Docker parados com sucesso"
    } else {
        Write-Error "Erro ao parar containers (codigo: $LASTEXITCODE)"
    }
}

# Verificar Portas
Write-Host ""
Write-Host "=== Passo 3/3: Verificando Portas ===" -ForegroundColor $GREEN

$ports = @(
    @{Port=9090; Name="gRPC Server"},
    @{Port=8081; Name="HTTP/Actuator"},
    @{Port=27017; Name="MongoDB"},
    @{Port=9092; Name="Kafka"},
    @{Port=2181; Name="Zookeeper"},
    @{Port=8082; Name="Kafka-UI"}
)
$occupiedPorts = @()

foreach ($item in $ports) {
    try {
        $connection = Test-NetConnection -ComputerName localhost -Port $item.Port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        if ($connection.TcpTestSucceeded) {
            Write-Warning "Porta $($item.Port) ($($item.Name)) ainda ocupada"
            $occupiedPorts += $item
        }
    } catch {
        # Ignora erros de conexao
    }
}

if ($occupiedPorts.Count -eq 0) {
    Write-Info "Todas as portas liberadas"
} else {
    Write-Warning "$($occupiedPorts.Count) porta(s) ainda ocupada(s)"
    Write-Host ""
    Write-Host "Processos que podem estar usando as portas:" -ForegroundColor $YELLOW
    Write-Host "  netstat -ano | findstr `"9090 8081 27017 9092 2181 8082`"" -ForegroundColor $WHITE
}

# Resumo
Write-Host ""
Write-Host "========================================" -ForegroundColor $GREEN
Write-Host "        PARADA CONCLUIDA COM SUCESSO!   " -ForegroundColor $YELLOW
Write-Host "========================================" -ForegroundColor $GREEN
Write-Host ""

if ($RemoveData) {
    Write-Host "Status:" -ForegroundColor $CYAN
    Write-Host "  [AVISO] Containers removidos" -ForegroundColor $YELLOW
    Write-Host "  [AVISO] Volumes removidos (DADOS PERDIDOS)" -ForegroundColor $RED
    Write-Host ""
    Write-Host "Dados do MongoDB e Kafka foram PERMANENTEMENTE REMOVIDOS" -ForegroundColor $RED
} else {
    Write-Host "Status:" -ForegroundColor $CYAN
    Write-Host "  [OK] Containers removidos" -ForegroundColor $GREEN
    Write-Host "  [OK] Volumes preservados (dados seguros)" -ForegroundColor $GREEN
    Write-Host ""
    Write-Host "Dados do MongoDB e Kafka estao preservados" -ForegroundColor $GREEN
    Write-Host "Para remover dados: stop.ps1 -RemoveData" -ForegroundColor $YELLOW
}

Write-Host ""
Write-Host "Proximos passos:" -ForegroundColor $CYAN
Write-Host "  - Iniciar:   start.ps1" -ForegroundColor $WHITE
Write-Host "  - Reiniciar: restart.ps1" -ForegroundColor $WHITE
Write-Host ""
