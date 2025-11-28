# Setup: Criar usuários para testes k6
# Este script deve ser executado antes dos testes de carga

$ErrorActionPreference = "Continue"
$BASE_URL = "http://localhost:8081"

$users = @(
    @{ username = "alice"; password = "password123"; email = "alice@test.com" },
    @{ username = "bob"; password = "password123"; email = "bob@test.com" },
    @{ username = "charlie"; password = "password123"; email = "charlie@test.com" }
)

Write-Host "`n=== SETUP DE USUÁRIOS PARA TESTES K6 ===" -ForegroundColor Cyan
Write-Host "Base URL: $BASE_URL" -ForegroundColor White
Write-Host "`n1. Criando usuários..." -ForegroundColor Yellow

$createdUsers = @()
$existingUsers = @()

foreach ($user in $users) {
    $body = @{
        username = $user.username
        password = $user.password
        email = $user.email
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "$BASE_URL/api/auth/register" `
            -Method POST `
            -ContentType "application/json" `
            -Body $body `
            -ErrorAction Stop
        
        Write-Host "   ✅ $($user.username) criado (ID: $($response.user_id))" -ForegroundColor Green
        $createdUsers += $user.username
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 409) {
            Write-Host "   ⚠️  $($user.username) já existe" -ForegroundColor Yellow
            $existingUsers += $user.username
        } else {
            Write-Host "   ❌ Erro ao criar $($user.username): $_" -ForegroundColor Red
        }
    }
}

Write-Host "`n2. Verificando login..." -ForegroundColor Yellow

foreach ($user in $users) {
    $body = @{
        username = $user.username
        password = $user.password
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "$BASE_URL/api/auth/login" `
            -Method POST `
            -ContentType "application/json" `
            -Body $body `
            -ErrorAction Stop
        
        Write-Host "   ✅ $($user.username) login OK (ID: $($response.user_id))" -ForegroundColor Green
    } catch {
        Write-Host "   ❌ $($user.username) falha no login: $_" -ForegroundColor Red
    }
}

Write-Host "`n=== RESUMO ===" -ForegroundColor Cyan
Write-Host "Usuários criados: $($createdUsers.Count)" -ForegroundColor White
Write-Host "Usuários já existentes: $($existingUsers.Count)" -ForegroundColor White
Write-Host "Total disponível para testes: $($users.Count)" -ForegroundColor Green

Write-Host "`n✅ Setup concluído! Pronto para executar testes k6." -ForegroundColor Green
Write-Host "   Execute: k6 run scripts/load-test/k6-send-messages.js" -ForegroundColor Gray
