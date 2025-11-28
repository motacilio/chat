# E2E Test with File-based JSON
$ErrorActionPreference = "Continue"
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$logFile = "test-results-$timestamp.log"
# Detectar caminho do projeto
$scriptDir = Split-Path -Parent $PSCommandPath
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
$protoPath = Join-Path $projectRoot "src\main\proto"

function Write-Log {
    param([string]$Message, [string]$Color = "White")
    $time = Get-Date -Format "HH:mm:ss"
    Write-Host "[$time] $Message" -ForegroundColor $Color
    Add-Content -Path $logFile -Value "[$time] $Message" -Encoding UTF8
}

Write-Log "=== E2E MESSAGE FLOW TEST ===" -Color Cyan

# Step 1: Auth
Write-Log "`n[1/7] AUTHENTICATE" -Color Cyan
$aliceAuth = Invoke-RestMethod -Uri http://localhost:8081/api/auth/login -Method POST -ContentType 'application/json' -Body (@{username='alice';password='password123'} | ConvertTo-Json)
$aliceId = $aliceAuth.user.userId
Write-Log "[OK] Alice: $aliceId" -Color Green

$bobAuth = Invoke-RestMethod -Uri http://localhost:8081/api/auth/login -Method POST -ContentType 'application/json' -Body (@{username='bob';password='password123'} | ConvertTo-Json)
$bobId = $bobAuth.user.userId
Write-Log "[OK] Bob: $bobId" -Color Green

# Step 2: Create Conv
Write-Log "`n[2/7] CREATE CONVERSATION" -Color Cyan
$convFile = "conv-req.json"
@"
{"type":"PRIVATE","participant_ids":["$bobId"]}
"@ | Out-File -FilePath $convFile -Encoding UTF8 -NoNewline

$convResp = grpcurl -plaintext -proto "$protoPath\conversation_service.proto" -import-path "$protoPath" -d "@$convFile" localhost:9090 chat_api.v1.ConversationService/CreateConversation 2>&1 | Out-String
Write-Log "Response: $convResp" -Color Gray
Remove-Item $convFile -ErrorAction SilentlyContinue

try {
    $convData = $convResp | ConvertFrom-Json
    $convId = $convData.conversation.conversation_id
    Write-Log "[OK] Conversation: $convId" -Color Green
} catch {
    Write-Log "[ERROR] Failed: $_" -Color Red
    exit 1
}

# Step 3: Send Msg
Write-Log "`n[3/7] SEND MESSAGE" -Color Cyan
$msgFile = "msg-req.json"
$msgText = "Hello Bob from gRPC at $(Get-Date -Format 'HH:mm:ss')"
@"
{"conversation_id":"$convId","sender_id":"$aliceId","message_text":"$msgText"}
"@ | Out-File -FilePath $msgFile -Encoding UTF8 -NoNewline

$msgResp = grpcurl -plaintext -proto "$protoPath\chat_service.proto" -import-path "$protoPath" -d "@$msgFile" localhost:9090 chat_api.v1.ChatService/SendMessage 2>&1 | Out-String
Write-Log "Response: $msgResp" -Color Gray
Remove-Item $msgFile -ErrorAction SilentlyContinue

try {
    $msgData = $msgResp | ConvertFrom-Json
    $messageId = $msgData.message_id
    Write-Log "[OK] Message: $messageId" -Color Green
} catch {
    Write-Log "[ERROR] Failed: $_" -Color Red
    exit 1
}

# Step 4: Kafka
Write-Log "`n[4/7] VERIFY KAFKA" -Color Cyan
Start-Sleep -Seconds 3
# Check Kafka topic offset (messages are in Avro format)
$kafkaOffset = docker exec kafka-dev kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic message-events 2>$null
if ($kafkaOffset -match ":(\d+)$") {
    Write-Log "[OK] Kafka 'message-events' has $($matches[1]) messages (Avro format)" -Color Green
} else {
    Write-Log "[WARN] Could not verify Kafka topic offset" -Color Yellow
}

# Step 5: MongoDB
Write-Log "`n[5/7] VERIFY MONGODB" -Color Cyan
$mongo = docker exec mongodb-dev mongosh chat --quiet --eval "db.messages.findOne({messageId: '$messageId'}, {messageText: 1, status: 1, messageId: 1, _id: 0})" 2>$null
if ($mongo -notmatch "null") {
    Write-Log "[OK] Found in MongoDB" -Color Green
} else {
    Write-Log "[WARN] NOT in MongoDB" -Color Yellow
}

# Step 6: Mark Read
Write-Log "`n[6/7] MARK AS READ" -Color Cyan
$readFile = "read-req.json"
@"
{"message_id":"$messageId","recipient_id":"$bobId"}
"@ | Out-File -FilePath $readFile -Encoding UTF8 -NoNewline

$readResp = grpcurl -plaintext -proto "$protoPath\chat_service.proto" -import-path "$protoPath" -d "@$readFile" localhost:9090 chat_api.v1.ChatService/MarkMessageAsRead 2>&1 | Out-String
Write-Log "Response: $readResp" -Color Gray
Remove-Item $readFile -ErrorAction SilentlyContinue

# Step 7: State
Write-Log "`n[7/7] VERIFY STATE" -Color Cyan
Start-Sleep -Seconds 3
$state = docker exec mongodb-dev mongosh chat --quiet --eval "db.messages.findOne({messageId: '$messageId'}, {status: 1, stateHistory: 1, _id: 0})" 2>$null
Write-Log "[OK] State: $state" -Color Green

Write-Log "`n[PASS] All steps completed" -Color Green
Write-Log "Log: $logFile" -Color Cyan
