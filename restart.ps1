# =============================================================================
# Script de Restart - Chat API  
# =============================================================================
# Descrição: Reinicia infraestrutura Docker e aplicação com estado limpo
# Autor: Marcos Pereira
# Data: 05/12/2025
# =============================================================================

# Cores
$GREEN = "Green"
$YELLOW = "Yellow"
$RED = "Red"
$CYAN = "Cyan"
$WHITE = "White"

function Write-Step {
    param([string]$Message)
    Write-Host "`n=== $Message ===" -ForegroundColor $GREEN
}

function Write-Info {
    param([string]$Message)
    Write-Host "  ✓ $Message" -ForegroundColor $CYAN
}

function Write-Warning {
    param([string]$Message)
    Write-Host "  ⚠ $Message" -ForegroundColor $YELLOW
}

function Write-Error {
    param([string]$Message)
    Write-Host "  ✗ $Message" -ForegroundColor $RED
}

function Test-DockerRunning {
    try {
        $null = docker ps 2>&1
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Restart-DockerDesktop {
    Write-Info "Tentando reiniciar Docker Desktop..."
    
    try {
        $dockerProcess = Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue
        if ($dockerProcess) {
            Write-Info "Encerrando Docker Desktop..."
            Stop-Process -Name "Docker Desktop" -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 5
        }
        
        Write-Info "Iniciando Docker Desktop..."
        Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe" -ErrorAction Stop
        
        Write-Info "Aguardando Docker Desktop inicializar (pode levar até 60 segundos)..."
        Start-Sleep -Seconds 60
        
        for ($i = 1; $i -le 10; $i++) {
            if (Test-DockerRunning) {
                Write-Info "Docker Desktop reiniciado com sucesso!"
                return $true
            }
            Write-Host "    Tentativa $i/10..." -ForegroundColor $YELLOW
            Start-Sleep -Seconds 5
        }
        
        Write-Error "Docker Desktop não respondeu após reinicialização"
        return $false
    } catch {
        Write-Error "Erro ao reiniciar Docker Desktop: $($_.Exception.Message)"
        return $false
    }
}

function Test-ContainersRunning {
    param(
        [string[]]$ExpectedContainers = @("zookeeper-dev", "kafka-dev", "mongodb-dev", "kafka-ui-dev", "minio-dev")
    )
    
    try {
        $runningContainers = docker ps --format "{{.Names}}" 2>$null
        $containerStatus = docker ps --format "table {{.Names}}\t{{.Status}}" 2>$null
        
        foreach ($container in $ExpectedContainers) {
            if ($runningContainers -notcontains $container) {
                Write-Error "Container $container não está rodando"
                return $false
            }
        }
        
        $healthOutput = docker ps --filter "health=healthy" --format "{{.Names}}" 2>$null
        if ($healthOutput -match "mongodb-dev") {
            Write-Info "MongoDB está healthy"
        }
        
        if (($healthOutput -match "kafka-dev") -or ($containerStatus -match "kafka-dev.*health: starting")) {
            Write-Info "Kafka está inicializando/healthy"
        }
        
        return $true
    } catch {
        Write-Error "Erro ao verificar containers: $($_.Exception.Message)"
        return $false
    }
}

function Test-Port {
    param([int]$Port)
    try {
        $connection = Test-NetConnection -ComputerName localhost -Port $Port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        return $connection.TcpTestSucceeded
    } catch {
        return $false
    }
}

function Wait-ForService {
    param(
        [string]$ServiceName,
        [int]$Port,
        [int]$MaxAttempts = 30
    )
    
    Write-Info "Aguardando $ServiceName (porta $Port)..."
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        if (Test-Port -Port $Port) {
            Write-Info "$ServiceName disponível!"
            return $true
        }
        Start-Sleep -Seconds 2
    }
    
    Write-Error "$ServiceName não iniciou após $MaxAttempts tentativas"
    return $false
}

# =============================================================================
# Banner
# =============================================================================

Write-Host "`n" -NoNewline
Write-Host "========================================" -ForegroundColor $CYAN
Write-Host "      CHAT API - RESTART COMPLETO       " -ForegroundColor $YELLOW
Write-Host "========================================" -ForegroundColor $CYAN
Write-Host "`n"

# =============================================================================
# Validação Docker
# =============================================================================

Write-Step "Validando Docker Desktop"

if (-not (Test-DockerRunning)) {
    Write-Error "Docker Desktop não está acessível!"
    Write-Warning "Possíveis causas:"
    Write-Warning "  1. Docker Desktop não está rodando"
    Write-Warning "  2. Docker Engine travou (erro 500)"
    Write-Warning "  3. WSL2 precisa ser reiniciado"
    
    $response = Read-Host "`nDeseja tentar reiniciar o Docker Desktop automaticamente? (S/N)"
    if ($response -eq 'S' -or $response -eq 's') {
        if (Restart-DockerDesktop) {
            Write-Info "Docker Desktop reiniciado com sucesso!"
        } else {
            Write-Error "Falha ao reiniciar Docker Desktop automaticamente"
            Write-Warning "Por favor, reinicie manualmente e execute o script novamente"
            Read-Host "`nPressione ENTER para sair"
            exit 1
        }
    } else {
        Write-Warning "Por favor:"
        Write-Warning "  1. Inicie/Reinicie o Docker Desktop manualmente"
        Write-Warning "  2. Aguarde até ele estar completamente inicializado"
        Write-Warning "  3. Execute este script novamente"
        Read-Host "`nPressione ENTER para sair"
        exit 1
    }
}
Write-Info "Docker Desktop está rodando"

# =============================================================================
# Passo 1: Parar Aplicação Java
# =============================================================================

Write-Step "Passo 1/6: Parando Aplicação Java"

$javaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue
if ($javaProcesses) {
    Write-Info "Encontrados $($javaProcesses.Count) processo(s) Java"
    $javaProcesses | ForEach-Object {
        Write-Info "  - PID: $($_.Id) | Memória: $([math]::Round($_.WorkingSet64/1MB, 2)) MB"
    }
    
    Write-Info "Encerrando processos..."
    $javaProcesses | Stop-Process -Force
    Start-Sleep -Seconds 3
    Write-Info "Processos Java encerrados"
} else {
    Write-Info "Nenhum processo Java em execução"
}

# =============================================================================
# Passo 2: Reiniciar Docker Compose (com volumes)
# =============================================================================

Write-Step "Passo 2/6: Reiniciando Infraestrutura Docker"

Write-Info "Parando containers e removendo volumes..."
docker-compose -f docker-compose.dev.yml down -v --remove-orphans

if ($LASTEXITCODE -ne 0) {
    Write-Warning "Aviso ao parar containers (pode ser normal se não estavam rodando)"
}

Start-Sleep -Seconds 3

Write-Info "Iniciando containers (Kafka, Zookeeper, MongoDB)..."
docker-compose -f docker-compose.dev.yml up -d

if ($LASTEXITCODE -ne 0) {
    Write-Error "Falha ao iniciar Docker Compose"
    Write-Info "Verifique os logs: docker-compose -f docker-compose.dev.yml logs"
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

Write-Info "Containers iniciados"
Start-Sleep -Seconds 5

# Validar containers
Write-Info "Validando se containers iniciaram..."
$expectedContainers = @("kafka-dev", "zookeeper-dev", "mongodb-dev", "kafka-ui-dev", "minio-dev")
$containerCheckAttempts = 0
$maxContainerCheckAttempts = 10

while ($containerCheckAttempts -lt $maxContainerCheckAttempts) {
    $containerCheckAttempts++
    
    $runningContainers = @(docker ps --format "{{.Names}}" 2>$null)
    $allRunning = $true
    
    foreach ($container in $expectedContainers) {
        if ($runningContainers -notcontains $container) {
            $allRunning = $false
            Write-Host "    Container $container não encontrado (tentativa $containerCheckAttempts/$maxContainerCheckAttempts)" -ForegroundColor $YELLOW
            break
        }
    }
    
    if ($allRunning) {
        Write-Info "Todos os containers esperados estão rodando!"
        break
    }
    
    if ($containerCheckAttempts -eq $maxContainerCheckAttempts) {
        Write-Error "Containers não iniciaram corretamente após $maxContainerCheckAttempts tentativas"
        Write-Info "Containers em execução:"
        docker ps --format "table {{.Names}}\t{{.Status}}"
        Write-Info "`nLogs recentes:"
        docker-compose -f docker-compose.dev.yml logs --tail 20
        Read-Host "`nPressione ENTER para sair"
        exit 1
    }
    
    Start-Sleep -Seconds 3
}

# Aguardar serviços
Write-Info "Aguardando Zookeeper..."
if (-not (Wait-ForService -ServiceName "Zookeeper" -Port 2181 -MaxAttempts 20)) {
    Write-Error "Zookeeper falhou ao iniciar"
    docker-compose -f docker-compose.dev.yml logs zookeeper --tail 30
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

Write-Info "Aguardando Kafka..."
if (-not (Wait-ForService -ServiceName "Kafka" -Port 9092 -MaxAttempts 40)) {
    Write-Error "Kafka falhou ao iniciar"
    docker-compose -f docker-compose.dev.yml logs kafka --tail 50
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

Write-Info "Aguardando MongoDB..."
if (-not (Wait-ForService -ServiceName "MongoDB" -Port 27017 -MaxAttempts 20)) {
    Write-Error "MongoDB falhou ao iniciar"
    docker-compose -f docker-compose.dev.yml logs mongodb --tail 30
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

# Aguardar MinIO
Write-Info "Aguardando MinIO..."
if (-not (Wait-ForService -ServiceName "MinIO" -Port 9000)) {
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

Write-Info "Infraestrutura pronta!"
Start-Sleep -Seconds 5

# =============================================================================
# Passo 3: Limpar Logs Antigos
# =============================================================================

Write-Step "Passo 3/6: Limpando Logs da Aplicação"

if (Test-Path "app.log") { 
    Remove-Item "app.log" -Force 
    Write-Info "app.log removido"
}
if (Test-Path "app-error.log") { 
    Remove-Item "app-error.log" -Force 
    Write-Info "app-error.log removido"
}

# =============================================================================
# Passo 4: Recompilar e Empacotar
# =============================================================================

Write-Step "Passo 4/6: Recompilando e Empacotando Aplicação"

Write-Info "Executando: mvn clean package -DskipTests"
mvn clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Error "Falha na compilação/empacotamento"
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

Write-Info "Compilação e empacotamento concluídos"

# =============================================================================
# Passo 5: Iniciar Aplicação
# =============================================================================

Write-Step "Passo 5/5: Iniciando Aplicação"

# Encontrar JAR
$jarPath = Get-ChildItem -Path ".\target" -Filter "*.jar" | 
    Where-Object { $_.Name -notlike "*-javadoc.jar" -and $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*.original" } |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $jarPath) {
    Write-Error "JAR não encontrado em .\target\"
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

Write-Info "JAR encontrado: $(Split-Path -Leaf $jarPath)"

# Iniciar aplicação com redirecionamento de logs
Write-Info "Iniciando aplicação com perfil 'dev'..."
Write-Info "  - Logs stdout: app.log"
Write-Info "  - Logs stderr: app-error.log"

$process = Start-Process -FilePath "java" `
    -ArgumentList "-jar", $jarPath, "--spring.profiles.active=dev" `
    -PassThru `
    -NoNewWindow `
    -RedirectStandardOutput "app.log" `
    -RedirectStandardError "app-error.log"

if ($process) {
    Write-Info "Aplicação iniciada com PID: $($process.Id)"
} else {
    Write-Error "Falha ao iniciar aplicação"
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

# Aguardar inicialização
Write-Info "Aguardando aplicação inicializar..."

# Validar gRPC Server
if (-not (Wait-ForService -ServiceName "gRPC Server" -Port 9090 -MaxAttempts 30)) {
    Write-Error "Aplicação não iniciou corretamente"
    Write-Info "`nÚltimas 50 linhas do log:"
    if (Test-Path "app.log") {
        Get-Content "app.log" -Tail 50
    }
    if (Test-Path "app-error.log") {
        Write-Info "`nErros:"
        Get-Content "app-error.log" -Tail 50
    }
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

# =============================================================================
# Validação
# =============================================================================

Write-Step "Validando Serviços"

$allOk = $true

# Verificar gRPC
if (Test-Port -Port 9090) {
    Write-Info "✓ gRPC Server rodando (porta 9090)"
} else {
    Write-Warning "✗ gRPC Server não respondendo (porta 9090)"
    $allOk = $false
}

# Verificar HTTP/Actuator
if (Test-Port -Port 8081) {
    Write-Info "✓ HTTP/Actuator rodando (porta 8081)"
    
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -TimeoutSec 5
        Write-Info "  Health Status: $($health.status)"
    } catch {
        Write-Warning "  Health endpoint não respondeu"
    }
} else {
    Write-Warning "✗ HTTP/Actuator não respondendo (porta 8081)"
    $allOk = $false
}

# Verificar containers
Write-Info "Status dos containers:"
docker ps --filter "name=kafka" --filter "name=zookeeper" --filter "name=mongo" --format "table {{.Names}}\t{{.Status}}"

# =============================================================================
# Resumo
# =============================================================================

Write-Host "`n" -NoNewline
if ($allOk) {
    Write-Host "========================================" -ForegroundColor $GREEN
    Write-Host "        RESTART CONCLUÍDO COM SUCESSO!   " -ForegroundColor $YELLOW
    Write-Host "========================================" -ForegroundColor $GREEN
} else {
    Write-Host "========================================" -ForegroundColor $YELLOW
    Write-Host "     RESTART CONCLUÍDO COM AVISOS       " -ForegroundColor $YELLOW
    Write-Host "========================================" -ForegroundColor $YELLOW
    Write-Host "`nVerifique os logs:" -ForegroundColor $YELLOW
    Write-Host "  - Get-Content app.log -Tail 50" -ForegroundColor $WHITE
    Write-Host "  - Get-Content app-error.log -Tail 50" -ForegroundColor $WHITE
}

Write-Host "`n"
Write-Host "Serviços:" -ForegroundColor $CYAN
Write-Host "  • gRPC:      http://localhost:9090" -ForegroundColor $WHITE
Write-Host "  • Actuator:  http://localhost:8081" -ForegroundColor $WHITE
Write-Host "  • Kafka:     localhost:9092" -ForegroundColor $WHITE
Write-Host "  • MongoDB:   localhost:27017" -ForegroundColor $WHITE
Write-Host "`n"

Write-Host "Comandos úteis:" -ForegroundColor $CYAN
Write-Host "  • Ver logs app:       Get-Content app.log -Tail 50 -Wait" -ForegroundColor $WHITE
Write-Host "  • Ver logs Docker:    docker-compose -f docker-compose.dev.yml logs -f" -ForegroundColor $WHITE
Write-Host "  • Health check:       curl http://localhost:8081/actuator/health" -ForegroundColor $WHITE
Write-Host "  • Parar tudo:         .\stop.ps1" -ForegroundColor $WHITE
Write-Host "`n"
