# Script de Análise de Resultados k6
# Processa JSON gerado pelo k6 e cria relatórios HTML/Markdown

param(
    [Parameter(Mandatory=$false)]
    [string]$ResultsFile = "results\summary.json",
    
    [Parameter(Mandatory=$false)]
    [string]$OutputFormat = "markdown", # markdown, html, json
    
    [Parameter(Mandatory=$false)]
    [string]$OutputFile = "results\report.md"
)

$ErrorActionPreference = "Stop"

Write-Host "=== K6 RESULTS ANALYZER ===" -ForegroundColor Cyan
Write-Host "Arquivo de entrada: $ResultsFile" -ForegroundColor White
Write-Host "Formato de saída: $OutputFormat" -ForegroundColor White
Write-Host "Arquivo de saída: $OutputFile" -ForegroundColor White

# Verificar se arquivo existe
if (-not (Test-Path $ResultsFile)) {
    Write-Host "❌ Arquivo não encontrado: $ResultsFile" -ForegroundColor Red
    Write-Host "Execute o teste k6 primeiro:" -ForegroundColor Yellow
    Write-Host "  k6 run --out json=results/summary.json scripts/load-test/k6-send-messages.js" -ForegroundColor Gray
    exit 1
}

# Carregar JSON
Write-Host "`nCarregando resultados..." -ForegroundColor Yellow
$data = Get-Content $ResultsFile -Raw | ConvertFrom-Json

# Extrair métricas
$metrics = $data.metrics
$thresholds = $data.thresholds

# Funções auxiliares
function Format-Duration {
    param([double]$milliseconds)
    if ($milliseconds -lt 1000) {
        return "$([Math]::Round($milliseconds, 2))ms"
    } elseif ($milliseconds -lt 60000) {
        return "$([Math]::Round($milliseconds / 1000, 2))s"
    } else {
        return "$([Math]::Round($milliseconds / 60000, 2))min"
    }
}

function Format-Bytes {
    param([long]$bytes)
    if ($bytes -lt 1024) {
        return "$bytes B"
    } elseif ($bytes -lt 1048576) {
        return "$([Math]::Round($bytes / 1024, 2)) KB"
    } elseif ($bytes -lt 1073741824) {
        return "$([Math]::Round($bytes / 1048576, 2)) MB"
    } else {
        return "$([Math]::Round($bytes / 1073741824, 2)) GB"
    }
}

function Get-MetricValue {
    param($metric, $stat)
    if ($null -eq $metric) { return 0 }
    if ($null -eq $metric.values) { return 0 }
    if ($null -eq $metric.values.$stat) { return 0 }
    return $metric.values.$stat
}

# Extrair dados principais
$httpReqs = Get-MetricValue $metrics.http_reqs "count"
$httpReqsFailed = Get-MetricValue $metrics.http_req_failed "rate"
$httpReqDurationAvg = Get-MetricValue $metrics.http_req_duration "avg"
$httpReqDurationP95 = Get-MetricValue $metrics.http_req_duration "p(95)"
$httpReqDurationP99 = Get-MetricValue $metrics.http_req_duration "p(99)"

$messagesCreated = Get-MetricValue $metrics.messages_created "count"
$errorRate = Get-MetricValue $metrics.errors "rate"
$messageLatencyAvg = Get-MetricValue $metrics.message_latency "avg"
$messageLatencyP95 = Get-MetricValue $metrics.message_latency "p(95)"

$idempotentRequests = Get-MetricValue $metrics.idempotent_requests "count"

# Gerar relatório Markdown
if ($OutputFormat -eq "markdown") {
    Write-Host "`nGerando relatório Markdown..." -ForegroundColor Yellow
    
    $markdown = @"
# Relatório de Teste de Carga k6

**Data de Execução**: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")  
**Arquivo de Resultados**: $ResultsFile  
**Duração Total**: $(Format-Duration (Get-MetricValue $metrics.iteration_duration "max"))

---

## 📊 Resumo Executivo

| Métrica | Valor |
|---------|-------|
| **Requisições HTTP Totais** | $httpReqs |
| **Taxa de Falha HTTP** | $([Math]::Round($httpReqsFailed * 100, 2))% |
| **Mensagens Criadas** | $messagesCreated |
| **Taxa de Erro de Mensagens** | $([Math]::Round($errorRate * 100, 2))% |
| **Requisições Idempotentes** | $idempotentRequests |

---

## ⏱️ Latência de Requisições HTTP

| Percentil | Latência |
|-----------|----------|
| **Média** | $(Format-Duration $httpReqDurationAvg) |
| **p50 (Mediana)** | $(Format-Duration (Get-MetricValue $metrics.http_req_duration "p(50)")) |
| **p90** | $(Format-Duration (Get-MetricValue $metrics.http_req_duration "p(90)")) |
| **p95** | $(Format-Duration $httpReqDurationP95) |
| **p99** | $(Format-Duration $httpReqDurationP99) |
| **Máximo** | $(Format-Duration (Get-MetricValue $metrics.http_req_duration "max")) |

"@

    if ($null -ne $metrics.message_latency) {
        $markdown += @"

---

## 📨 Latência de Mensagens

| Percentil | Latência |
|-----------|----------|
| **Média** | $(Format-Duration $messageLatencyAvg) |
| **p95** | $(Format-Duration $messageLatencyP95) |
| **p99** | $(Format-Duration (Get-MetricValue $metrics.message_latency "p(99)")) |

"@
    }

    if ($null -ne $metrics.files_uploaded) {
        $filesUploaded = Get-MetricValue $metrics.files_uploaded "count"
        $bytesUploaded = Get-MetricValue $metrics.bytes_uploaded "count"
        $uploadThroughputAvg = Get-MetricValue $metrics.upload_throughput_mbps "avg"
        
        $markdown += @"

---

## 📁 Upload de Arquivos

| Métrica | Valor |
|---------|-------|
| **Arquivos Enviados** | $filesUploaded |
| **Total de Dados** | $(Format-Bytes $bytesUploaded) |
| **Throughput Médio** | $([Math]::Round($uploadThroughputAvg, 2)) MB/s |
| **Throughput Máximo** | $([Math]::Round((Get-MetricValue $metrics.upload_throughput_mbps "max"), 2)) MB/s |

"@
    }

    # Thresholds
    $markdown += @"

---

## ✅ Critérios de Aceitação (Thresholds)

| Threshold | Status | Valor |
|-----------|--------|-------|
"@

    foreach ($threshold in $thresholds.PSObject.Properties) {
        $name = $threshold.Name
        $ok = $threshold.Value.ok
        $status = if ($ok) { "✅ PASS" } else { "❌ FAIL" }
        $markdown += "`n| $name | $status | - |"
    }

    $markdown += @"


---

## 📈 Gráficos de Desempenho

### Distribuição de Latência HTTP

``````
Média: $(Format-Duration $httpReqDurationAvg)
p95:   $(Format-Duration $httpReqDurationP95) $(if ($httpReqDurationP95 -lt 100) { "✅" } else { "⚠️" })
p99:   $(Format-Duration $httpReqDurationP99) $(if ($httpReqDurationP99 -lt 200) { "✅" } else { "⚠️" })
``````

### Taxa de Erro

``````
HTTP:     $([Math]::Round($httpReqsFailed * 100, 2))% $(if ($httpReqsFailed -lt 0.01) { "✅" } else { "❌" })
Mensagens: $([Math]::Round($errorRate * 100, 2))% $(if ($errorRate -lt 0.01) { "✅" } else { "❌" })
``````

---

## 🎯 Recomendações

"@

    # Recomendações baseadas em métricas
    if ($httpReqDurationP95 -gt 100) {
        $markdown += "- ⚠️ **Latência p95 acima do threshold (100ms)**: Considere otimizar queries de banco ou adicionar cache`n"
    }
    
    if ($errorRate -gt 0.01) {
        $markdown += "- ❌ **Taxa de erro acima de 1%**: Investigar logs de erro e aumentar resiliência`n"
    }
    
    if ($messagesCreated -lt ($httpReqs * 0.5)) {
        $markdown += "- ⚠️ **Baixa taxa de criação de mensagens**: Verificar se há problemas de validação ou autenticação`n"
    }
    
    if ($httpReqsFailed -lt 0.01 -and $errorRate -lt 0.01) {
        $markdown += "- ✅ **Sistema estável**: Todas as métricas dentro dos thresholds esperados`n"
        $markdown += "- ✅ **Pronto para scaling**: Considere testar com múltiplas instâncias`n"
    }

    $markdown += @"

---

**Gerado automaticamente por**: analyze-results.ps1  
**Versão**: 1.0
"@

    # Salvar arquivo
    $markdown | Out-File -FilePath $OutputFile -Encoding UTF8
    Write-Host "✅ Relatório gerado: $OutputFile" -ForegroundColor Green
}

# Exibir resumo no console
Write-Host "`n=== RESUMO ===" -ForegroundColor Cyan
Write-Host "Requisições HTTP: $httpReqs" -ForegroundColor White
Write-Host "Latência p95: $(Format-Duration $httpReqDurationP95)" -ForegroundColor White
Write-Host "Taxa de erro: $([Math]::Round($errorRate * 100, 2))%" -ForegroundColor White
Write-Host "Mensagens criadas: $messagesCreated" -ForegroundColor White

if ($null -ne $metrics.files_uploaded) {
    Write-Host "Arquivos enviados: $(Get-MetricValue $metrics.files_uploaded 'count')" -ForegroundColor White
}

Write-Host "`n✅ Análise concluída!" -ForegroundColor Green
