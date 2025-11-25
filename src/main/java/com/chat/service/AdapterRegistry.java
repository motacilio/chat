package com.chat.service;

import com.chat.adapter.PlatformAdapter;
import com.chat.model.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * Responsibility: Registry pattern for platform adapter lookup and management.
 * Does NOT: Handle message routing (see PlatformRoutingService), validate platforms, manage credentials.
 * 
 * Distributed Systems Concept: Service Registry - centralized location to discover and access
 * external service integrations. Enables loose coupling between routing logic and adapter implementations.
 * Adding new platforms requires ZERO changes to routing code.
 * 
 * Educational Value: Demonstrates THREE critical Spring Boot + Design Patterns concepts:
 * 
 * 1. **Registry Pattern**: Centralized lookup table mapping Platform enum → adapter instance.
 *    Alternative to if/else chains or switch statements scattered throughout code.
 * 
 * 2. **Dependency Injection with @Qualifier**: Spring autowires multiple beans implementing
 *    same interface (PlatformAdapter) using @Qualifier annotations to distinguish them.
 *    This is how Spring knows which adapter to inject for each platform.
 * 
 * 3. **Constructor Injection**: All dependencies injected via constructor (best practice).
 *    Enables immutability, easier testing, and explicit dependency visibility.
 * 
 * Spring Boot Magic Explained:
 * - @Service: Marks this class as a Spring-managed bean (singleton by default)
 * - @Qualifier: Matches bean name to inject (e.g., "whatsappAdapter" matches @Component("whatsappAdapter"))
 * - Constructor params: Spring searches for beans matching parameter types + qualifiers
 * - EnumMap: Efficient lookup O(1) for Platform → PlatformAdapter mapping
 * 
 * Usage Example:
 * <pre>
 * // Spring injects AdapterRegistry into PlatformRoutingService
 * public class PlatformRoutingService {
 *     private final AdapterRegistry adapterRegistry;
 *     
 *     public void routeMessage(Platform platform, String externalId, String text) {
 *         PlatformAdapter adapter = adapterRegistry.getAdapter(platform);
 *         adapter.connect(credentials);
 *         SendResult result = adapter.sendMessage(externalId, text);
 *     }
 * }
 * </pre>
 * 
 * Why EnumMap Instead of HashMap?
 * - Type-safe: Compiler ensures only Platform enum values as keys
 * - Performance: Faster than HashMap for enum keys (uses array internally)
 * - Memory: More compact representation than HashMap
 * - Educational: Teaches specialized collections for specific use cases
 */
@Service
public class AdapterRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterRegistry.class);
    
    /**
     * Internal registry mapping Platform enum → PlatformAdapter instance.
     * 
     * Educational Note: EnumMap is specialized Map implementation optimized for enum keys.
     * Uses ordinal values as array indices internally, making lookups O(1) and very fast.
     * 
     * Why not just if/else or switch?
     * - Scattered logic: Every place needing adapter would duplicate selection logic
     * - Hard to test: Can't easily mock adapter selection
     * - Hard to extend: Adding new platform requires finding/updating all selection points
     * - Registry centralizes: Single source of truth for adapter lookup
     */
    private final Map<Platform, PlatformAdapter> adapterMap;
    
    /**
     * Constructor-based Dependency Injection with @Qualifier annotations.
     * 
     * Educational Deep Dive - How Spring Autowiring Works:
     * 
     * 1. Spring scans classpath for @Component/@Service classes
     * 2. Finds 3 classes implementing PlatformAdapter:
     *    - WhatsAppMockAdapter with @Component("whatsappAdapter")
     *    - InstagramMockAdapter with @Component("instagramAdapter")
     *    - TelegramBotAdapter with @Component("telegramAdapter") [future]
     * 
     * 3. Creates singleton instances of each adapter
     * 
     * 4. When constructing AdapterRegistry, Spring sees 3 PlatformAdapter parameters:
     *    - @Qualifier("whatsappAdapter") → injects WhatsAppMockAdapter instance
     *    - @Qualifier("instagramAdapter") → injects InstagramMockAdapter instance
     *    - @Qualifier("telegramAdapter") → injects TelegramBotAdapter instance
     * 
     * 5. Constructor populates adapterMap with Platform enum → adapter mappings
     * 
     * Why @Qualifier?
     * - Without it: Spring sees 3 beans of type PlatformAdapter and throws ambiguity error
     *   "expected single matching bean but found 3: whatsappAdapter, instagramAdapter, telegramAdapter"
     * - With it: Spring knows EXACTLY which bean to inject for each parameter
     * 
     * Alternative Approach (NOT recommended):
     * - Field injection with @Autowired: Harder to test, hidden dependencies
     * - Setter injection: Mutable state, can forget to set dependencies
     * - Constructor injection (BEST PRACTICE): Immutable, explicit, testable
     * 
     * Testing Benefit:
     * In tests, you can create AdapterRegistry manually without Spring:
     * <code>
     * PlatformAdapter mockWhatsApp = mock(PlatformAdapter.class);
     * PlatformAdapter mockInstagram = mock(PlatformAdapter.class);
     * AdapterRegistry registry = new AdapterRegistry(mockWhatsApp, mockInstagram, null);
     * </code>
     * 
     * @param whatsappAdapter WhatsApp adapter instance (injected by Spring via @Qualifier)
     * @param instagramAdapter Instagram adapter instance (injected by Spring via @Qualifier)
     * @param telegramAdapter Telegram adapter instance (injected by Spring via @Qualifier)
     */
    public AdapterRegistry(
            @Qualifier("whatsappAdapter") PlatformAdapter whatsappAdapter,
            @Qualifier("instagramAdapter") PlatformAdapter instagramAdapter,
            @Qualifier("telegramAdapter") PlatformAdapter telegramAdapter) {
        
        // Initialize EnumMap with Platform enum class (tells EnumMap what enum type to use)
        this.adapterMap = new EnumMap<>(Platform.class);
        
        // Populate registry: Platform enum value → adapter instance
        // Educational Note: This mapping is done ONCE at startup, not on every lookup
        adapterMap.put(Platform.WHATSAPP, whatsappAdapter);
        adapterMap.put(Platform.INSTAGRAM, instagramAdapter);
        adapterMap.put(Platform.TELEGRAM, telegramAdapter);
        
        logger.info("AdapterRegistry initialized with {} platform adapters", adapterMap.size());
        logger.debug("Registered platforms: {}", adapterMap.keySet());
        
        // Educational logging: Show which adapter class is registered for each platform
        adapterMap.forEach((platform, adapter) -> 
            logger.debug("  {} → {} ({}% success rate expected)", 
                        platform, 
                        adapter.getClass().getSimpleName(),
                        platform == Platform.WHATSAPP ? "95" : 
                        platform == Platform.INSTAGRAM ? "90" : "TBD")
        );
    }
    
    /**
     * Retrieves platform adapter for given platform.
     * 
     * Distributed Systems Concept: Service Discovery - lookup service implementation
     * by identifier (Platform enum). In microservices, similar pattern uses service
     * registry (Consul, Eureka) to discover service instances by name.
     * 
     * Educational Note - Why This Method Exists:
     * Instead of exposing the Map directly (bad encapsulation), we provide controlled
     * access via method. This allows us to:
     * - Add validation (null checks, unsupported platforms)
     * - Add logging (track adapter usage)
     * - Add caching or lazy loading in future
     * - Change internal implementation (e.g., switch from EnumMap to database lookup)
     *   without breaking calling code
     * 
     * Time Complexity: O(1) - constant time lookup in EnumMap
     * Space Complexity: O(n) where n = number of Platform enum values (currently 3)
     * 
     * @param platform Platform enum value (WHATSAPP, INSTAGRAM, or TELEGRAM)
     * @return PlatformAdapter instance for the platform
     * @throws IllegalArgumentException if platform is null or adapter not registered
     * 
     * Educational Warning: In production code, you might want to handle missing adapters
     * gracefully (return Optional<PlatformAdapter> or default no-op adapter) instead of
     * throwing exception. For educational clarity, we fail fast to catch configuration errors.
     */
    public PlatformAdapter getAdapter(Platform platform) {
        // Validation: Fail fast if platform is null
        if (platform == null) {
            logger.error("Cannot get adapter for null platform");
            throw new IllegalArgumentException("Platform cannot be null");
        }
        
        // Lookup adapter in registry
        PlatformAdapter adapter = adapterMap.get(platform);
        
        // Validation: Fail fast if adapter not registered (configuration error)
        if (adapter == null) {
            logger.error("No adapter registered for platform: {}. Available platforms: {}", 
                        platform, adapterMap.keySet());
            throw new IllegalArgumentException(
                String.format("No adapter registered for platform: %s. " +
                             "Did you forget to implement/register the adapter?", platform));
        }
        
        logger.debug("Retrieved adapter for platform: {} → {}", 
                    platform, adapter.getClass().getSimpleName());
        
        return adapter;
    }
    
    /**
     * Checks if adapter is registered for given platform.
     * 
     * Educational Note: This is a "query method" - reads state without modifying it.
     * Useful for validation before attempting to use adapter (fail fast principle).
     * 
     * Usage Example:
     * <pre>
     * if (adapterRegistry.hasAdapter(Platform.TELEGRAM)) {
     *     // Safe to route message to Telegram
     * } else {
     *     // Log error or use fallback (send internally only)
     * }
     * </pre>
     * 
     * @param platform Platform enum value to check
     * @return true if adapter registered for platform, false otherwise
     */
    public boolean hasAdapter(Platform platform) {
        return platform != null && adapterMap.containsKey(platform);
    }
    
    /**
     * Returns count of registered adapters.
     * 
     * Educational Note: Useful for monitoring/health checks. In production, you might
     * expose this via Spring Boot Actuator health endpoint to verify all expected
     * adapters are registered at startup.
     * 
     * @return Number of registered platform adapters (expected: 3)
     */
    public int getAdapterCount() {
        return adapterMap.size();
    }
}
