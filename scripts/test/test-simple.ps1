# Simple Test Script - Text Message Flow
$ErrorActionPreference = "Continue"

Write-Host "`n=== AUTHENTICATION ===" -ForegroundColor Cyan
$authAlice = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"alice","password":"password123"}'
Write-Host "[OK] Alice: $($authAlice.user_id)" -ForegroundColor Green

$authBob = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"bob","password":"password123"}'
Write-Host "[OK] Bob: $($authBob.user_id)" -ForegroundColor Green

$aliceToken = $authAlice.token
$bobToken = $authBob.token
$bobId = $authBob.user_id

Write-Host "`n=== CREATE CONVERSATION ===" -ForegroundColor Cyan
$convBody = "{`"type`":`"PRIVATE`",`"participant_ids`":[`"$bobId`"]}"
$conv = Invoke-RestMethod -Uri "http://localhost:8081/api/conversations" -Method POST -ContentType "application/json" -Headers @{"Authorization"="Bearer $aliceToken"} -Body $convBody
Write-Host "[OK] Conversation: $($conv.conversation_id)" -ForegroundColor Green
$convId = $conv.conversation_id

Write-Host "`n=== SEND MESSAGE ===" -ForegroundColor Cyan
$msgBody = "{`"conversation_id`":`"$convId`",`"message_text`":`"Hello Bob! Kafka test at $(Get-Date -Format 'HH:mm:ss')`"}"
$msg = Invoke-RestMethod -Uri "http://localhost:8081/api/messages" -Method POST -ContentType "application/json" -Headers @{"Authorization"="Bearer $aliceToken"} -Body $msgBody
Write-Host "[OK] Message sent: $($msg.message_id)" -ForegroundColor Green
Write-Host "    Status: $($msg.status)" -ForegroundColor Gray
$messageId = $msg.message_id

Write-Host "`n=== KAFKA MESSAGE-EVENTS ===" -ForegroundColor Cyan
Write-Host "Waiting 3 seconds..." -ForegroundColor Yellow
Start-Sleep -Seconds 3
# Check Kafka topic offset (messages are in Avro format)
$kafkaOffset = docker exec kafka-dev kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic message-events 2>$null
if ($kafkaOffset -match ":(\d+)$") {
    Write-Host "[OK] Kafka 'message-events' has $($matches[1]) messages (Avro format)" -ForegroundColor Green
}

Write-Host "`n=== MONGODB PERSISTENCE ===" -ForegroundColor Cyan
$mongoQuery = "db.messages.findOne({messageId: '$messageId'}, {messageText: 1, status: 1, messageId: 1, _id: 0})"
docker exec mongodb-dev mongosh chat --quiet --eval $mongoQuery 2>$null | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }

Write-Host "`n=== BOB RETRIEVES MESSAGE ===" -ForegroundColor Cyan
$history = Invoke-RestMethod -Uri "http://localhost:8081/api/conversations/$convId/messages" -Method GET -Headers @{"Authorization"="Bearer $bobToken"}
$received = $history.messages | Where-Object { $_.message_id -eq $messageId }
if ($received) {
    Write-Host "[OK] Bob received: $($received.message_text)" -ForegroundColor Green
    Write-Host "    Status: $($received.status)" -ForegroundColor Gray
}

Write-Host "`n=== MARK AS READ ===" -ForegroundColor Cyan
$readBody = "{`"message_id`":`"$messageId`"}"
Invoke-RestMethod -Uri "http://localhost:8081/api/messages/$messageId/read" -Method POST -ContentType "application/json" -Headers @{"Authorization"="Bearer $bobToken"} -Body $readBody | Out-Null
Write-Host "[OK] Marked as READ" -ForegroundColor Green

Write-Host "`n=== VERIFY STATE TRANSITION ===" -ForegroundColor Cyan
Write-Host "Waiting 3 seconds..." -ForegroundColor Yellow
Start-Sleep -Seconds 3
$stateQuery = "db.messages.findOne({messageId: '$messageId'}, {stateHistory: 1, status: 1, _id: 0})"
Write-Host "State history from MongoDB:" -ForegroundColor Yellow
docker exec mongodb-dev mongosh chat --quiet --eval $stateQuery 2>$null | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }

Write-Host "`n=== ALICE CHECKS STATUS ===" -ForegroundColor Cyan
$status = Invoke-RestMethod -Uri "http://localhost:8081/api/messages/$messageId/status" -Method GET -Headers @{"Authorization"="Bearer $aliceToken"}
Write-Host "[OK] Current status: $($status.status)" -ForegroundColor Green
Write-Host "State transitions:" -ForegroundColor Yellow
$status.state_history | ForEach-Object {
    Write-Host "    - $($_.state) at $($_.timestamp)" -ForegroundColor Gray
}

Write-Host "`n=== TEST COMPLETE ===" -ForegroundColor Cyan
if ($status.status -eq 'READ') {
    Write-Host "[PASS] Message lifecycle complete: SENT -> DELIVERED -> READ" -ForegroundColor Green
} else {
    Write-Host "[WARN] Status is $($status.status)" -ForegroundColor Yellow
}
