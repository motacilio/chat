package com.chat.service;

import com.chat.adapter.PlatformAdapter;
import com.chat.model.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for AdapterRegistry
 * 
 * Validates Spring DI configuration and adapter lookup functionality.
 * 
 * These are INTEGRATION tests (not unit tests) because they require:
 * - Spring ApplicationContext to load
 * - Bean creation and autowiring
 * - @Qualifier resolution
 * 
 * Educational Focus:
 * - How to test Spring-managed components
 * - Difference between unit tests (isolated) and integration tests (with Spring)
 * - Verifying dependency injection configuration
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AdapterRegistry Integration Tests")
class AdapterRegistryTest {

    @Autowired
    private AdapterRegistry adapterRegistry;

    /**
     * Test 1: Spring Context Loads
     * 
     * Validates that Spring successfully creates all required beans:
     * - WhatsAppMockAdapter (@Component("whatsappAdapter"))
     * - InstagramMockAdapter (@Component("instagramAdapter"))
     * - AdapterRegistry (@Service with @Qualifier constructor params)
     */
    @Test
    @DisplayName("Should autowire AdapterRegistry from Spring context")
    void testSpringAutowiring() {
        assertNotNull(adapterRegistry, 
            "AdapterRegistry should be autowired by Spring");
    }

    /**
     * Test 2: Adapter Count
     * 
     * Validates that registry contains exactly 2 adapters (one per platform).
     */
    @Test
    @DisplayName("Should have exactly 2 adapters registered")
    void testAdapterCount() {
        assertEquals(2, adapterRegistry.getAdapterCount(),
            "Registry should contain WhatsApp and Instagram adapters");
    }

    /**
     * Test 3: WhatsApp Adapter Lookup
     * 
     * Validates that getAdapter(WHATSAPP) returns correct adapter instance.
     */
    @Test
    @DisplayName("Should return WhatsApp adapter for WHATSAPP platform")
    void testGetWhatsAppAdapter() {
        PlatformAdapter adapter = adapterRegistry.getAdapter(Platform.WHATSAPP);
        
        assertNotNull(adapter, "WhatsApp adapter should be registered");
        assertEquals(Platform.WHATSAPP, adapter.getPlatform(),
            "Adapter should identify as WHATSAPP platform");
        assertEquals("WhatsAppMockAdapter", adapter.getClass().getSimpleName(),
            "Should return WhatsAppMockAdapter instance");
    }

    /**
     * Test 4: Instagram Adapter Lookup
     */
    @Test
    @DisplayName("Should return Instagram adapter for INSTAGRAM platform")
    void testGetInstagramAdapter() {
        PlatformAdapter adapter = adapterRegistry.getAdapter(Platform.INSTAGRAM);
        
        assertNotNull(adapter, "Instagram adapter should be registered");
        assertEquals(Platform.INSTAGRAM, adapter.getPlatform(),
            "Adapter should identify as INSTAGRAM platform");
        assertEquals("InstagramMockAdapter", adapter.getClass().getSimpleName(),
            "Should return InstagramMockAdapter instance");
    }

    /**
     * Test 5: HasAdapter Check - Existing Platform
     */
    @Test
    @DisplayName("Should return true for registered platforms")
    void testHasAdapterExisting() {
        assertTrue(adapterRegistry.hasAdapter(Platform.WHATSAPP),
            "Should have WhatsApp adapter");
        assertTrue(adapterRegistry.hasAdapter(Platform.INSTAGRAM),
            "Should have Instagram adapter");
    }

    /**
     * Test 6: Null Platform Handling
     * 
     * Edge case: What happens if we try to get adapter for null platform?
     */
    @Test
    @DisplayName("Should throw exception for null platform")
    void testGetAdapterNullPlatform() {
        assertThrows(IllegalArgumentException.class, 
            () -> adapterRegistry.getAdapter(null),
            "Should throw IllegalArgumentException for null platform");
    }

    /**
     * Test 8: Adapter Instance Uniqueness
     * 
     * Validates that multiple calls to getAdapter return the SAME instance
     * (Spring beans are singletons by default).
     */
    @Test
    @DisplayName("Should return same adapter instance on multiple calls")
    void testAdapterSingleton() {
        PlatformAdapter whatsapp1 = adapterRegistry.getAdapter(Platform.WHATSAPP);
        PlatformAdapter whatsapp2 = adapterRegistry.getAdapter(Platform.WHATSAPP);
        
        assertSame(whatsapp1, whatsapp2,
            "Should return same singleton instance (not create new adapter each time)");
    }

    /**
     * Test 9: Adapter Independence
     * 
     * Validates that different platform adapters are DIFFERENT instances.
     */
    @Test
    @DisplayName("Should return different instances for different platforms")
    void testAdapterIndependence() {
        PlatformAdapter whatsapp = adapterRegistry.getAdapter(Platform.WHATSAPP);
        PlatformAdapter instagram = adapterRegistry.getAdapter(Platform.INSTAGRAM);
        PlatformAdapter telegram = adapterRegistry.getAdapter(Platform.TELEGRAM);
        
        assertNotSame(whatsapp, instagram,
            "WhatsApp and Instagram should be different instances");
        assertNotSame(whatsapp, telegram,
            "WhatsApp and Telegram should be different instances");
        assertNotSame(instagram, telegram,
            "Instagram and Telegram should be different instances");
    }

    /**
     * Test 10: Registry Immutability
     * 
     * Validates that registry state doesn't change after construction.
     * Adapter count should always be 2.
     */
    @Test
    @DisplayName("Should maintain consistent state across calls")
    void testRegistryImmutability() {
        int count1 = adapterRegistry.getAdapterCount();
        
        // Perform some operations
        adapterRegistry.getAdapter(Platform.WHATSAPP);
        adapterRegistry.getAdapter(Platform.INSTAGRAM);
        adapterRegistry.hasAdapter(Platform.WHATSAPP);
        
        int count2 = adapterRegistry.getAdapterCount();
        
        assertEquals(count1, count2,
            "Adapter count should remain constant (registry is immutable)");
    }

    /**
     * Test 11: Registered Platforms Covered
     * 
     * Validates that WhatsApp and Instagram platforms have adapters.
     */
    @Test
    @DisplayName("Should have adapter for WhatsApp and Instagram platforms")
    void testRegisteredPlatformsCovered() {
        Platform[] registeredPlatforms = {Platform.WHATSAPP, Platform.INSTAGRAM};
        
        for (Platform platform : registeredPlatforms) {
            assertTrue(adapterRegistry.hasAdapter(platform),
                "Should have adapter for platform: " + platform);
            
            PlatformAdapter adapter = adapterRegistry.getAdapter(platform);
            assertNotNull(adapter,
                "Adapter should not be null for platform: " + platform);
            assertEquals(platform, adapter.getPlatform(),
                "Adapter platform should match requested platform");
        }
    }

    /**
     * Test 12: Spring @Qualifier Resolution
     * 
     * Validates that Spring correctly matched @Qualifier annotations
     * to bean names during autowiring.
     * 
     * Educational: This test demonstrates how Spring DI works:
     * 1. AdapterRegistry constructor has 2 @Qualifier parameters
     * 2. Spring finds beans named "whatsappAdapter" and "instagramAdapter"
     * 3. Spring injects correct beans into EnumMap
     * 4. This test verifies the wiring worked correctly
     */
    @Test
    @DisplayName("Should correctly wire adapters via @Qualifier annotations")
    void testQualifierResolution() {
        // If this test passes, it means Spring successfully:
        // 1. Found @Component("whatsappAdapter") bean
        // 2. Matched it to @Qualifier("whatsappAdapter") parameter
        // 3. Injected it into AdapterRegistry constructor
        // 4. Stored it in EnumMap with Platform.WHATSAPP key
        
        PlatformAdapter whatsapp = adapterRegistry.getAdapter(Platform.WHATSAPP);
        assertEquals("WhatsAppMockAdapter", whatsapp.getClass().getSimpleName(),
            "@Qualifier(\"whatsappAdapter\") should resolve to WhatsAppMockAdapter bean");
        
        PlatformAdapter instagram = adapterRegistry.getAdapter(Platform.INSTAGRAM);
        assertEquals("InstagramMockAdapter", instagram.getClass().getSimpleName(),
            "@Qualifier(\"instagramAdapter\") should resolve to InstagramMockAdapter bean");
    }

    /**
     * Test 13: Concurrent Access
     * 
     * Validates that registry is thread-safe (immutable state).
     */
    @Test
    @DisplayName("Should handle concurrent adapter lookups")
    void testConcurrentAccess() throws InterruptedException {
        final int threadCount = 10;
        final int lookupsPerThread = 100;
        
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < lookupsPerThread; j++) {
                    // Cycle through registered platforms only (WhatsApp and Instagram)
                    Platform[] platforms = {Platform.WHATSAPP, Platform.INSTAGRAM};
                    Platform platform = platforms[j % 2];
                    PlatformAdapter adapter = adapterRegistry.getAdapter(platform);
                    assertNotNull(adapter);
                    assertEquals(platform, adapter.getPlatform());
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // If we got here without exceptions, thread safety is OK
        assertEquals(2, adapterRegistry.getAdapterCount(),
            "Registry should maintain consistent state after concurrent access");
    }
}
