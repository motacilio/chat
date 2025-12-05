# =============================================================================
# Script de Inicialização Automática - Chat API
# =============================================================================
# Descrição: Inicia toda a infraestrutura (Docker) e aplicação Spring Boot
# Autor: Marcos Pereira  
# Data: 05/12/2025
# =============================================================================

param(
    [switch]$SkipBuild,
    [switch]$Rebuild
)

# Cores para output
$GREEN = "Green"
$YELLOW = "Yellow"
$RED = "Red"
$CYAN = "Cyan"
$WHITE = "White"

# =============================================================================
# Funções Auxiliares
# =============================================================================

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
        Write-Host "    Tentativa $i/$MaxAttempts..." -NoNewline
        Start-Sleep -Seconds 2
        Write-Host " aguardando..."
    }
    
    Write-Error "$ServiceName não iniciou após $MaxAttempts tentativas"
    return $false
}

function Test-DockerRunning {
    try {
        $null = docker ps 2>&1
        if ($LASTEXITCODE -ne 0) {
            return $false
        }
        return $true
    } catch {
        return $false
    }
}

function Restart-DockerDesktop {
    Write-Warning "Docker Engine não está acessível. Tentando reiniciar Docker Desktop..."
    
    # Parar Docker Desktop
    Write-Info "Encerrando Docker Desktop..."
    Stop-Process -Name "Docker Desktop" -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 10
    
    # Iniciar Docker Desktop
    Write-Info "Iniciando Docker Desktop..."
    $dockerPath = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (-not (Test-Path $dockerPath)) {
        Write-Error "Docker Desktop não encontrado em: $dockerPath"
        return $false
    }
    
    Start-Process $dockerPath
    Write-Info "Aguardando Docker Desktop inicializar (60 segundos)..."
    Start-Sleep -Seconds 60
    
    # Validar inicialização
    $maxRetries = 10
    for ($i = 1; $i -le $maxRetries; $i++) {
        Write-Host "    Validando Docker Engine - Tentativa $i/$maxRetries..." -NoNewline
        
        if (Test-DockerRunning) {
            Write-Host " OK!" -ForegroundColor $GREEN
            return $true
        }
        
        Write-Host " aguardando..."
        Start-Sleep -Seconds 10
    }
    
    Write-Error "Docker Engine não respondeu após reinicialização"
    return $false
}

function Test-ContainersRunning {
    param([string[]]$ExpectedContainers)
    
    try {
        $runningContainers = docker ps --format "{{.Names}}" 2>$null
        if ($LASTEXITCODE -ne 0) {
            return $false
        }
        
        foreach ($container in $ExpectedContainers) {
            if ($runningContainers -notmatch $container) {
                Write-Host "    Container $container não encontrado" -ForegroundColor $YELLOW
                return $false
            }
        }
        
        # Validar que containers estão saudáveis (não apenas rodando)
        $healthyContainers = docker ps --filter "health=healthy" --format "{{.Names}}" 2>$null
        $startingContainers = docker ps --filter "health=starting" --format "{{.Names}}" 2>$null
        
        # MongoDB deve estar healthy
        if ($healthyContainers -notmatch "mongodb-dev") {
            Write-Host "    MongoDB ainda não está healthy" -ForegroundColor $YELLOW
            return $false
        }
        
        # Kafka pode estar starting (aceitável pois healthcheck demora)
        $kafkaHealthy = ($healthyContainers -match "kafka-dev")
        $kafkaStarting = ($startingContainers -match "kafka-dev")
        
        if (-not ($kafkaHealthy -or $kafkaStarting)) {
            Write-Host "    Kafka não está rodando ou com problemas" -ForegroundColor $YELLOW
            return $false
        }
        
        return $true
    } catch {
        return $false
    }
}

# =============================================================================
# Banner
# =============================================================================

Write-Host "`n" -NoNewline
Write-Host "========================================" -ForegroundColor $CYAN
Write-Host "   CHAT API - INICIALIZAÇÃO COMPLETA   " -ForegroundColor $YELLOW
Write-Host "========================================" -ForegroundColor $CYAN
Write-Host "`n"

# =============================================================================
# Validações Iniciais
# =============================================================================

Write-Step "Validando Pré-requisitos"

# Verificar Docker
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker não encontrado. Instale: https://www.docker.com/products/docker-desktop"
    Read-Host "`nPressione ENTER para sair"
    exit 1
}
Write-Info "Docker encontrado: $(docker --version)"

# Verificar se Docker está rodando
if (-not (Test-DockerRunning)) {
    Write-Warning "Docker Desktop não está rodando ou Docker Engine não está acessível!"
    
    $response = Read-Host "`nDeseja tentar reiniciar o Docker Desktop automaticamente? (S/N)"
    if ($response -eq "S" -or $response -eq "s") {
        if (-not (Restart-DockerDesktop)) {
            Write-Error "Falha ao reiniciar Docker Desktop"
            Write-Warning "Por favor:"
            Write-Warning "  1. Reinicie o Windows"
            Write-Warning "  2. Abra o Docker Desktop manualmente"
            Write-Warning "  3. Aguarde até ele estar completamente inicializado"
            Write-Warning "  4. Execute este script novamente"
            Read-Host "`nPressione ENTER para sair"
            exit 1
        }
    } else {
        Write-Warning "Por favor:"
        Write-Warning "  1. Inicie o Docker Desktop manualmente"
        Write-Warning "  2. Aguarde até ele estar completamente inicializado"
        Write-Warning "  3. Execute este script novamente"
        Read-Host "`nPressione ENTER para sair"
        exit 1
    }
}
Write-Info "Docker Engine está acessível"

# Verificar Docker Compose
if (-not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    Write-Error "Docker Compose não encontrado"
    Read-Host "`nPressione ENTER para sair"
    exit 1
}
Write-Info "Docker Compose encontrado: $(docker-compose --version)"

# Verificar Java
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java não encontrado. Instale JDK 17+"
    Read-Host "`nPressione ENTER para sair"
    exit 1
}
$javaVersion = java -version 2>&1 | Select-Object -First 1
Write-Info "Java encontrado: $javaVersion"

# Verificar Maven
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "Maven não encontrado. Instale: https://maven.apache.org/download.cgi"
    Read-Host "`nPressione ENTER para sair"
    exit 1
}
Write-Info "Maven encontrado: $(mvn --version | Select-Object -First 1)"

# =============================================================================
# Limpeza de Processos Antigos
# =============================================================================

Write-Step "Limpando Processos Antigos"

# Limpar logs antigos
Write-Info "Limpando logs antigos..."
Remove-Item -Path ".\app.log" -ErrorAction SilentlyContinue
Remove-Item -Path ".\app-error.log" -ErrorAction SilentlyContinue

# Parar processos Java que possam estar rodando
$javaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue
if ($javaProcesses) {
    Write-Info "Encerrando processos Java existentes..."
    $javaProcesses | Stop-Process -Force
    Start-Sleep -Seconds 3
    Write-Info "Processos Java encerrados"
} else {
    Write-Info "Nenhum processo Java em execução"
}

# =============================================================================
# Infraestrutura Docker
# =============================================================================

Write-Step "Gerenciando Infraestrutura Docker"

# Se Rebuild, derrubar tudo
if ($Rebuild) {
    Write-Info "Removendo containers e volumes existentes..."
    docker-compose -f docker-compose.dev.yml down -v --remove-orphans
    Start-Sleep -Seconds 3
}

# Verificar se containers já estão rodando
$runningContainers = docker ps --format "{{.Names}}" 2>$null
if ($runningContainers -match "kafka|zookeeper|mongo") {
    Write-Warning "Containers já em execução. Recriando para garantir estado limpo..."
    docker-compose -f docker-compose.dev.yml down --remove-orphans
    Start-Sleep -Seconds 3
}

# Iniciar Docker Compose
Write-Info "Iniciando containers (Kafka, Zookeeper, MongoDB)..."
docker-compose -f docker-compose.dev.yml up -d

if ($LASTEXITCODE -ne 0) {
    Write-Error "Falha ao iniciar Docker Compose"
    Write-Info "Verifique os logs com: docker-compose -f docker-compose.dev.yml logs"
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

Write-Info "Comando docker-compose executado com sucesso"
Start-Sleep -Seconds 5

# Validar se containers realmente subiram
Write-Info "Validando se containers iniciaram..."
$expectedContainers = @("kafka-dev", "zookeeper-dev", "mongodb-dev", "kafka-ui-dev", "minio-dev")
$containerCheckAttempts = 0
$maxContainerCheckAttempts = 10

while ($containerCheckAttempts -lt $maxContainerCheckAttempts) {
    $containerCheckAttempts++
    
    # Verificar se containers estão rodando (não precisa estar healthy ainda)
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

# =============================================================================
# Aguardar Serviços
# =============================================================================

Write-Step "Aguardando Serviços Essenciais"

# Aguardar Zookeeper
if (-not (Wait-ForService -ServiceName "Zookeeper" -Port 2181 -MaxAttempts 20)) {
    Write-Error "Zookeeper não iniciou corretamente"
    Write-Info "Logs do Zookeeper:"
    docker-compose -f docker-compose.dev.yml logs zookeeper --tail 30
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

# Aguardar Kafka (mais tempo pois depende do Zookeeper)
if (-not (Wait-ForService -ServiceName "Kafka" -Port 9092 -MaxAttempts 40)) {
    Write-Error "Kafka não iniciou corretamente"
    Write-Info "Logs do Kafka:"
    docker-compose -f docker-compose.dev.yml logs kafka --tail 50
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

# Aguardar MongoDB
if (-not (Wait-ForService -ServiceName "MongoDB" -Port 27017 -MaxAttempts 20)) {
    Write-Error "MongoDB não iniciou corretamente"
    Write-Info "Logs do MongoDB:"
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

Write-Info "Todos os serviços de infraestrutura estão prontos!"

# Aguardar mais 5 segundos para Kafka finalizar inicialização interna
Write-Info "Aguardando Kafka finalizar inicialização interna..."
Start-Sleep -Seconds 5

# =============================================================================
# Build da Aplicação
# =============================================================================

if (-not $SkipBuild) {
    Write-Step "Compilando Aplicação"

    if ($Rebuild) {
        Write-Info "Executando: mvn clean install -DskipTests"
        mvn clean install -DskipTests
    } else {
        Write-Info "Executando: mvn package -DskipTests"
        mvn package -DskipTests
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Falha na compilação Maven"
        Read-Host "`nPressione ENTER para sair"
        exit 1
    }

    Write-Info "Compilação concluída com sucesso"
} else {
    Write-Warning "Pulando build (--SkipBuild especificado)"
}

# =============================================================================
# Iniciar Aplicação
# =============================================================================

Write-Step "Iniciando Aplicação Spring Boot"

# Encontrar o JAR gerado
$jarPath = Get-ChildItem -Path ".\target" -Filter "*.jar" | 
    Where-Object { $_.Name -notlike "*-javadoc.jar" -and $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*.original" } |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $jarPath) {
    Write-Error "JAR não encontrado em .\target\"
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

Write-Info "JAR encontrado: $jarPath"

# Iniciar aplicação com logs capturados
Write-Info "Iniciando aplicação com perfil 'dev'..."
Write-Info "Logs serão salvos em: app.log e app-error.log"

$process = Start-Process -FilePath "java" `
    -ArgumentList "-jar", $jarPath, "--spring.profiles.active=dev" `
    -RedirectStandardOutput ".\app.log" `
    -RedirectStandardError ".\app-error.log" `
    -NoNewWindow `
    -PassThru

if ($process) {
    Write-Info "Aplicação iniciada com PID: $($process.Id)"
} else {
    Write-Error "Falha ao iniciar aplicação"
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

# Aguardar aplicação inicializar com validação de porta
Write-Info "Aguardando aplicação inicializar..."

if (-not (Wait-ForService -ServiceName "gRPC Server" -Port 9090 -MaxAttempts 30)) {
    Write-Error "Aplicação não iniciou corretamente na porta 9090"
    Write-Warning "Verificando logs de erro..."
    
    if (Test-Path ".\app-error.log") {
        Write-Host "`n=== Últimas 30 linhas do app-error.log ===" -ForegroundColor $RED
        Get-Content ".\app-error.log" -Tail 30
    }
    
    if (Test-Path ".\app.log") {
        Write-Host "`n=== Últimas 30 linhas do app.log ===" -ForegroundColor $YELLOW
        Get-Content ".\app.log" -Tail 30
    }
    
    Read-Host "`nPressione ENTER para sair"
    exit 1
}

Write-Info "Aplicação iniciou com sucesso!"

# =============================================================================
# Validação Final
# =============================================================================

Write-Step "Validando Serviços"

# Verificar gRPC (porta 9090)
if (Test-Port -Port 9090) {
    Write-Info "✓ gRPC Server rodando na porta 9090"
} else {
    Write-Warning "✗ gRPC Server não respondendo na porta 9090"
}

# Verificar Actuator/HTTP (porta 8081)
if (Test-Port -Port 8081) {
    Write-Info "✓ HTTP/Actuator rodando na porta 8081"
    
    # Tentar health check
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -TimeoutSec 5
        Write-Info "  Health Status: $($health.status)"
    } catch {
        Write-Warning "  Health endpoint não respondeu"
    }
} else {
    Write-Warning "✗ HTTP/Actuator não respondendo na porta 8081"
}

# Exibir status dos containers
Write-Info "Status dos containers Docker:"
docker ps --filter "name=kafka" --filter "name=zookeeper" --filter "name=mongo" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# =============================================================================
# Resumo Final
# =============================================================================

Write-Host "`n" -NoNewline
Write-Host "========================================" -ForegroundColor $GREEN
Write-Host "       INICIALIZAÇÃO CONCLUÍDA!         " -ForegroundColor $YELLOW
Write-Host "========================================" -ForegroundColor $GREEN
Write-Host "`n"

Write-Host "Serviços disponíveis:" -ForegroundColor $CYAN
Write-Host "  • gRPC Server:     http://localhost:9090" -ForegroundColor $WHITE
Write-Host "  • HTTP/Actuator:   http://localhost:8081" -ForegroundColor $WHITE
Write-Host "  • Kafka:           localhost:9092" -ForegroundColor $WHITE
Write-Host "  • MongoDB:         localhost:27017" -ForegroundColor $WHITE
Write-Host "  • Zookeeper:       localhost:2181" -ForegroundColor $WHITE
Write-Host "`n"

Write-Host "Próximos passos:" -ForegroundColor $CYAN
Write-Host "  1. Ver logs app:      Get-Content app.log -Tail 50 -Wait" -ForegroundColor $WHITE
Write-Host "  2. Teste o Health:    curl http://localhost:8081/actuator/health" -ForegroundColor $WHITE
Write-Host "  3. Teste gRPC:        Use Postman ou grpcurl" -ForegroundColor $WHITE
Write-Host "  4. Ver logs Docker:   docker-compose -f docker-compose.dev.yml logs -f" -ForegroundColor $WHITE
Write-Host "  5. Acessar MongoDB:   docker exec -it mongodb-dev mongosh" -ForegroundColor $WHITE
Write-Host "  6. Parar tudo:        .\stop.ps1" -ForegroundColor $WHITE
Write-Host "`n"

Write-Host "Logs da aplicação:" -ForegroundColor $CYAN
Write-Host "  • Standard Output:  .\app.log" -ForegroundColor $WHITE
Write-Host "  • Standard Error:   .\app-error.log" -ForegroundColor $WHITE
Write-Host "`n"

