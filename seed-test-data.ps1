# MongoDB Seed Script for File Message Integration Test
# Creates test users and conversation in MongoDB

$ErrorActionPreference = "Stop"

Write-Host "=== Seeding MongoDB for File Message Test ===" -ForegroundColor Cyan

# MongoDB connection details
$mongoHost = "localhost"
$mongoPort = "27017"
$database = "chat"

# User IDs (matching hardcoded users in UserService)
$aliceId = "a1a1a1a1-1111-1111-1111-111111111111"
$bobId = "b2b2b2b2-2222-2222-2222-222222222222"

# Conversation ID
$conversationId = "c1c1c1c1-1111-1111-1111-111111111111"

# MongoDB commands
$commands = @"
use chat

// Insert test conversation (alice + bob)
db.conversations.deleteOne({ conversation_id: "$conversationId" })
db.conversations.insertOne({
    conversation_id: "$conversationId",
    type: "PRIVATE",
    participants: ["$aliceId", "$bobId"],
    created_at: new Date(),
    last_message_at: new Date(),
    last_message_preview: "Test conversation for file uploads"
})

// Verify
var conv = db.conversations.findOne({ conversation_id: "$conversationId" })
if (conv) {
    print("✓ Conversation created: " + conv.conversation_id)
    print("  Participants: " + conv.participants.join(", "))
} else {
    print("✗ Failed to create conversation")
}
"@

# Save commands to temp file
$tempFile = "C:\temp\mongo-seed.js"
if (-not (Test-Path "C:\temp")) {
    New-Item -ItemType Directory -Path "C:\temp" | Out-Null
}
$commands | Out-File -FilePath $tempFile -Encoding UTF8

# Execute MongoDB commands via docker exec
Write-Host "Seeding data into MongoDB..." -ForegroundColor Yellow
Get-Content $tempFile | docker exec -i mongodb mongosh --quiet

# Cleanup
Remove-Item $tempFile -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "=== MongoDB Seeded Successfully ===" -ForegroundColor Green
Write-Host "Test conversation: $conversationId" -ForegroundColor Gray
Write-Host "Participants: alice ($aliceId), bob ($bobId)" -ForegroundColor Gray
Write-Host ""
Write-Host "Now you can run: .\test-file-message-simple.ps1" -ForegroundColor Yellow
