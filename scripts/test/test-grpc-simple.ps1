# gRPC Test with Proto Files
$ErrorActionPreference = "Continue"
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$logFile = "grpc-test-$timestamp.log"
# Detectar caminho do projeto (2 níveis acima do script)
$scriptDir = Split-Path -Parent $PSCommandPath
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
$protoPath = Join-Path $projectRoot "src\main\proto"

function Write-Log {
    param([string]$Message, [string]$Color = "White")
    $time = Get-Date -Format "HH:mm:ss"
    Write-Host "[$time] $Message" -ForegroundColor $Color
    "[$time] $Message" | Out-File -FilePath $logFile -Append
}

Write-Log "=== AUTHENTICATION ===" -Color Cyan
try {
    $authAlice = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"alice","password":"password123"}'
    Write-Log "[OK] Alice: $($authAlice.user_id)" -Color Green
    $aliceId = $authAlice.user_id
    
    $authBob = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"bob","password":"password123"}'
    Write-Log "[OK] Bob: $($authBob.user_id)" -Color Green
    $bobId = $authBob.user_id
} catch {
    Write-Log "[ERROR] Authentication failed: $_" -Color Red
    exit 1
}

Write-Log "`n=== CREATE CONVERSATION ===" -Color Cyan
$convJson = @"
{
  "type": "PRIVATE",
  "participant_ids": ["$bobId"]
}
"@
Write-Log "Request: $convJson" -Color Gray

try {
    $convResponse = grpcurl -plaintext -proto "$protoPath\conversation_service.proto" -import-path "$protoPath" -d $convJson localhost:9090 chat_api.v1.ConversationService/CreateConversation 2>&1
    Write-Log "Response: $convResponse" -Color Green
    $convResponse | Out-File -FilePath $logFile -Append
    
    $convData = $convResponse | ConvertFrom-Json -ErrorAction SilentlyContinue
    $convId = $convData.conversation.conversation_id
    Write-Log "Conversation ID: $convId" -Color Green
} catch {
    Write-Log "[ERROR] CreateConversation failed: $_" -Color Red
    Write-Log "Response was: $convResponse" -Color Yellow
}

if ([string]::IsNullOrEmpty($convId)) {
    Write-Log "[ERROR] No conversation ID returned, cannot continue" -Color Red
    exit 1
}

Write-Log "`n=== SEND MESSAGE ===" -Color Cyan
$msgJson = @"
{
  "conversation_id": "$convId",
  "sender_id": "$aliceId",
  "message_text": "Test message at $(Get-Date -Format 'HH:mm:ss')"
}
"@
Write-Log "Request: $msgJson" -Color Gray

try {
    $msgResponse = grpcurl -plaintext -proto "$protoPath\chat_service.proto" -import-path "$protoPath" -d $msgJson localhost:9090 chat_api.v1.ChatService/SendMessage 2>&1
    Write-Log "Response: $msgResponse" -Color Green
    $msgResponse | Out-File -FilePath $logFile -Append
    
    $msgData = $msgResponse | ConvertFrom-Json -ErrorAction SilentlyContinue
    $messageId = $msgData.message_id
    Write-Log "Message ID: $messageId" -Color Green
} catch {
    Write-Log "[ERROR] SendMessage failed: $_" -Color Red
}

if ([string]::IsNullOrEmpty($messageId)) {
    Write-Log "[ERROR] No message ID returned" -Color Red
} else {
    Write-Log "`n=== KAFKA VERIFICATION ===" -Color Cyan
    Write-Log "Waiting 3 seconds..." -Color Yellow
    Start-Sleep -Seconds 3
    
    # Check Kafka topic offset (messages are in Avro format)
    $kafkaOffset = docker exec kafka-dev kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic message-events 2>$null
    if ($kafkaOffset -match ":(\d+)$") {
        Write-Log "[OK] Kafka 'message-events' has $($matches[1]) messages (Avro format)" -Color Green
    } else {
        Write-Log "[WARN] Could not verify Kafka topic offset" -Color Yellow
    }
    
    Write-Log "`n=== MONGODB VERIFICATION ===" -Color Cyan
    $mongoQuery = "db.messages.findOne({messageId: '$messageId'}, {messageText: 1, status: 1, messageId: 1, _id: 0})"
    $mongoDoc = docker exec mongodb-dev mongosh chat --quiet --eval $mongoQuery 2>$null
    if ($mongoDoc -match "null") {
        Write-Log "[WARN] Not found in MongoDB" -Color Yellow
    } else {
        Write-Log "[OK] Found in MongoDB" -Color Green
        $mongoDoc | Out-File -FilePath $logFile -Append
    }
}

Write-Log "`nLog: $logFile" -Color Cyan
