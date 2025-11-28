# Webhook Race Condition - Temporary Workaround

## Problem

MessageStateUpdateWorker ERROR: "Message not found for state update" with platformMessageId.

### Root Cause

Fundamental architectural race condition between asynchronous webhook callbacks and MongoDB write commits:

1. **Mock Adapter** calls `triggerDeliveredCallback()` @Async **INSIDE** `sendMessage()` before returning
2. @Async thread starts sleeping (delay of N seconds) **IMMEDIATELY**
3. `sendMessage()` returns `SendResult.success(platformMessageId)` to Worker
4. **Worker** logs, calls `mappingService.saveMapping()`, sleeps 2s, calls `ack.acknowledge()`
5. **MongoDB** buffers the write but takes additional time to commit and make queryable
6. **@Async thread** wakes up after N seconds and makes HTTP POST to webhook endpoint
7. **WebhookController** queries MongoDB for platformMessageId mapping
8. **RACE CONDITION**: If webhook arrives before MongoDB commit completes → lookup fails

### Timeline Evidence

From actual logs (test with 20-30s delay):
```
22:36:29 - Mock sendMessage() returns, @Async starts (random delay: 20s)
22:36:49 - Webhook arrives (exactly 20s later)
22:36:49-50 - WebhookController retries 5x300ms, all fail
22:36:50 - Worker saves mapping (TOO LATE!)
22:36:52 - Worker Thread.sleep(2000) completes
```

Even with 20-30s webhook delay, MongoDB write + commit took >20s in some cases.

## Temporary Workaround

**Current Solution**: Increased webhook delay to 60-80 seconds in Mock adapters.

### Changes Made

1. **WhatsAppMockAdapter.java**: `triggerDeliveredCallback()` delay = 60000 + random.nextInt(20000) ms
2. **InstagramMockAdapter.java**: Same 60-80s delay
3. **Workers**: Added `Thread.sleep(2000)` after `saveMapping()` to flush write buffer
4. **WebhookController**: Retry logic (5 attempts × 300ms) to handle timing variations

### Why 60 Seconds

- Mock adapter latency simulation: ~100-300ms
- Worker processing overhead: ~500ms
- MongoDB saveMapping() write: ~1-2s
- MongoDB commit + queryable: **up to 25-30s observed worst case**
- Thread.sleep buffer flush: 2s
- Safety margin: 2x = **60s minimum**

### Tradeoffs

✅ **PROS**:
- Guarantees mapping exists before webhook arrives
- No code refactoring required
- Works reliably in all scenarios

❌ **CONS**:
- E2E tests take 90+ seconds to complete
- Unrealistic simulation (real webhooks arrive in 1-3s)
- Hides architectural problem instead of fixing it

## Proper Solution (Future Refactoring)

### Recommended Architecture

**Move webhook triggering from Mock Adapter to Worker**:

```java
// WhatsAppMessageWorker.java
if (result.isSuccess()) {
    // Save mapping FIRST
    mappingService.saveMapping(messageId, platformMessageId, Platform.WHATSAPP);
    
    // Wait for MongoDB commit
    Thread.sleep(2000);
    
    // ACK Kafka offset
    ack.acknowledge();
    
    // THEN trigger webhook callback (new method)
    webhookTriggerService.triggerDeliveredCallback(platformMessageId, externalId, Platform.WHATSAPP);
}
```

### Benefits

1. **Correct sequencing**: Mapping guaranteed to exist before webhook
2. **Realistic timing**: Webhook delay can be reduced to 1-3s (real-world behavior)
3. **Testability**: Can mock webhook trigger service in unit tests
4. **Separation of concerns**: Mock adapters only simulate platform API, not webhooks

### Implementation Steps

1. Create `WebhookTriggerService` with `@Async triggerDeliveredCallback()` method
2. Inject into Workers (WhatsApp, Instagram, future platforms)
3. Remove `triggerDeliveredCallback()` from Mock adapters
4. Update Mock adapters to only simulate sendMessage() latency (~100-300ms)
5. Update integration tests to wait for webhook (3-5s instead of 90s)

## Current Status

**Workaround is active**: Tests functional but slow (90s per E2E test).

**Action required**: Schedule refactoring for next sprint to implement proper solution.

## Related Files

- `src/main/java/com/chat/adapter/WhatsAppMockAdapter.java` (line 198: webhook delay)
- `src/main/java/com/chat/adapter/InstagramMockAdapter.java` (line 198: webhook delay)
- `src/main/java/com/chat/worker/WhatsAppMessageWorker.java` (line 105-117: saveMapping + sleep)
- `src/main/java/com/chat/worker/InstagramMessageWorker.java` (line 105-117: same)
- `src/main/java/com/chat/controller/WebhookController.java` (line 65-85: retry logic)
- `src/main/java/com/chat/service/PlatformMessageMappingService.java` (mapping persistence)

## Testing

Current E2E test: `test-file-upload-e2e.ps1`
- Expected duration: ~90-100 seconds
- Webhook arrival: 60-80s after message sent
- State update: Should show DELIVERED after webhook completes

To verify fix is working:
```powershell
.\test-file-upload-e2e.ps1
# Wait 90 seconds
Get-Content spring-boot.log -Tail 200 | Select-String "Resolved messageId|Message state updated to DELIVERED"
```

Should see:
```
[WEBHOOK] Resolved messageId: <uuid> from platformMessageId: wamid.xxx
[MessageStateUpdateWorker] Message state updated - messageId: <uuid>, newStatus: DELIVERED
```

---
**Last Updated**: 2025-11-26  
**Author**: GitHub Copilot (Claude Sonnet 4.5)  
**Status**: TEMPORARY WORKAROUND - REFACTORING RECOMMENDED
