# Performance Benchmark Script - 10,000 Concurrent Users
# Target: NFR-003 - p95 latency <100ms, error rate <1%

param(
    [string]$ApiUrl = "http://localhost:8081",
    [switch]$Quick,  # Run quick version (1k users, 5min total)
    [switch]$SkipHealthCheck,
    [switch]$OpenReport
)

$ErrorActionPreference = "Stop"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  PERFORMANCE BENCHMARK - CHAT API" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Check k6 installation
Write-Host "Checking k6 installation..." -ForegroundColor Yellow
try {
    $k6Version = k6 version 2>&1
    Write-Host " k6 installed: $k6Version" -ForegroundColor Green
} catch {
    Write-Host " ERROR: k6 not installed" -ForegroundColor Red
    Write-Host " Install from: https://k6.io/docs/getting-started/installation/" -ForegroundColor Yellow
    Write-Host " Or run: winget install k6" -ForegroundColor Yellow
    exit 1
}

# Verify API is running
if (-not $SkipHealthCheck) {
    Write-Host "`nVerifying Chat API health..." -ForegroundColor Yellow
    try {
        $healthResponse = Invoke-RestMethod -Uri "$ApiUrl/actuator/health" -TimeoutSec 5
        if ($healthResponse.status -eq "UP") {
            Write-Host " API is healthy" -ForegroundColor Green
        } else {
            Write-Host " API health check returned: $($healthResponse.status)" -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host " ERROR: Cannot reach API at $ApiUrl" -ForegroundColor Red
        Write-Host " Make sure Chat API is running (docker-compose up -d)" -ForegroundColor Yellow
        exit 1
    }
}

# Create results directory
$resultsDir = "results"
if (-not (Test-Path $resultsDir)) {
    New-Item -ItemType Directory -Path $resultsDir | Out-Null
}

# Choose test script
if ($Quick) {
    Write-Host "`n Running QUICK benchmark (1,000 users, 5min total)..." -ForegroundColor Cyan
    $scriptPath = "scripts/load-test/k6-send-messages.js"
    $outputFile = "$resultsDir/benchmark-quick.json"
} else {
    Write-Host "`n Running FULL benchmark (10,000 users, 17min total)..." -ForegroundColor Cyan
    Write-Host " WARNING: This will generate HEAVY load on the system" -ForegroundColor Yellow
    Write-Host " Ensure production-grade infrastructure is running`n" -ForegroundColor Yellow
    
    Write-Host " Press Enter to continue or Ctrl+C to cancel..." -ForegroundColor Yellow
    Read-Host
    
    $scriptPath = "scripts/load-test/k6-benchmark-10k-users.js"
    $outputFile = "$resultsDir/benchmark-10k.json"
}

# Run k6 benchmark
Write-Host "`n Starting k6 benchmark..." -ForegroundColor Green
Write-Host " Target: p95 <100ms, error rate <1%`n" -ForegroundColor White

$env:API_URL = $ApiUrl

try {
    k6 run `
        --out "json=$outputFile" `
        --summary-export="$resultsDir/benchmark-summary.json" `
        $scriptPath
    
    $exitCode = $LASTEXITCODE
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "  BENCHMARK COMPLETE" -ForegroundColor Cyan
    Write-Host "========================================`n" -ForegroundColor Cyan
    
    if ($exitCode -eq 0) {
        Write-Host " RESULT: PASSED" -ForegroundColor Green
        Write-Host " All thresholds met`n" -ForegroundColor Green
    } else {
        Write-Host " RESULT: FAILED" -ForegroundColor Red
        Write-Host " Some thresholds were not met`n" -ForegroundColor Red
    }
    
    # Show results location
    Write-Host "Results saved to:" -ForegroundColor Yellow
    Write-Host "  JSON: $outputFile" -ForegroundColor White
    Write-Host "  Summary: $resultsDir/benchmark-summary.json" -ForegroundColor White
    if (Test-Path "$resultsDir/benchmark-10k-report.html") {
        Write-Host "  HTML Report: $resultsDir/benchmark-10k-report.html" -ForegroundColor White
        
        if ($OpenReport) {
            Write-Host "`n Opening HTML report in browser..." -ForegroundColor Cyan
            Start-Process "$resultsDir/benchmark-10k-report.html"
        }
    }
    
    # Parse summary for key metrics
    Write-Host "`n Key Metrics:" -ForegroundColor Cyan
    if (Test-Path "$resultsDir/benchmark-summary.json") {
        $summary = Get-Content "$resultsDir/benchmark-summary.json" | ConvertFrom-Json
        
        if ($summary.metrics.http_req_duration) {
            $p95 = [math]::Round($summary.metrics.http_req_duration.values.'p(95)', 2)
            $p99 = [math]::Round($summary.metrics.http_req_duration.values.'p(99)', 2)
            Write-Host "  Latency p95: $p95 ms" -ForegroundColor White
            Write-Host "  Latency p99: $p99 ms" -ForegroundColor White
        }
        
        if ($summary.metrics.errors) {
            $errorRate = [math]::Round($summary.metrics.errors.values.rate * 100, 2)
            Write-Host "  Error Rate: $errorRate%" -ForegroundColor White
        }
        
        if ($summary.metrics.http_reqs) {
            $throughput = [math]::Round($summary.metrics.http_reqs.values.rate, 2)
            Write-Host "  Throughput: $throughput req/s" -ForegroundColor White
        }
    }
    
    Write-Host ""
    
    exit $exitCode
    
} catch {
    Write-Host "`n ERROR running k6 benchmark: $_" -ForegroundColor Red
    exit 1
}
