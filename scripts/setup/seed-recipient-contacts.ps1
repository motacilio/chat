# seed-recipient-contacts.ps1
# Populates RecipientContact collection with test data for platform routing

Write-Host "Seeding RecipientContact data for platform routing..." -ForegroundColor Cyan

# MongoDB connection
$mongoHost = "localhost"
$mongoPort = "27017"
$database = "chat_db"
$collection = "recipient_contacts"

# Test data: Map internal users to external platform IDs
$contacts = @(
    @{
        userId = "a1a1a1a1-1111-1111-1111-111111111111"  # Alice
        platform = "WHATSAPP"
        externalId = "+5511987654321"
        verified = $true
        createdAt = "2025-11-24T10:00:00Z"
    },
    @{
        userId = "a1a1a1a1-1111-1111-1111-111111111111"  # Alice
        platform = "INSTAGRAM"
        externalId = "@alice_wonderland"
        verified = $true
        createdAt = "2025-11-24T10:00:00Z"
    },
    @{
        userId = "b2b2b2b2-2222-2222-2222-222222222222"  # Bob
        platform = "WHATSAPP"
        externalId = "+5511912345678"
        verified = $true
        createdAt = "2025-11-24T10:00:00Z"
    },
    @{
        userId = "b2b2b2b2-2222-2222-2222-222222222222"  # Bob
        platform = "INSTAGRAM"
        externalId = "@bob_builder"
        verified = $true
        createdAt = "2025-11-24T10:00:00Z"
    },
    @{
        userId = "c3c3c3c3-3333-3333-3333-333333333333"  # Charlie
        platform = "WHATSAPP"
        externalId = "+14155552671"  # US number
        verified = $true
        createdAt = "2025-11-24T10:00:00Z"
    }
)

Write-Host "Inserting $($contacts.Count) recipient contacts..." -ForegroundColor Yellow

foreach ($contact in $contacts) {
    $jsonDoc = $contact | ConvertTo-Json -Compress
    
    # Use mongosh (MongoDB Shell) to insert document
    $command = @"
use $database;
db.$collection.updateOne(
    { userId: "$($contact.userId)", platform: "$($contact.platform)" },
    { `$set: $(ConvertTo-Json $contact -Compress) },
    { upsert: true }
);
"@
    
    $command | mongosh --quiet --host $mongoHost --port $mongoPort
    
    Write-Host "  OK $($contact.platform): $($contact.externalId) -> User $($contact.userId.Substring(0, 8))..." -ForegroundColor Green
}

Write-Host ""
Write-Host "Seed completed! RecipientContacts ready for platform routing." -ForegroundColor Green
Write-Host ""
Write-Host "Verify data:" -ForegroundColor Cyan
Write-Host "  mongosh chat_db --eval ""db.recipient_contacts.find().pretty()""" -ForegroundColor Gray
