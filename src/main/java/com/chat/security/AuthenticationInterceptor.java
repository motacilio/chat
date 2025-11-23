package com.chat.security;

import com.chat.service.JwtService;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * gRPC JWT Authentication Interceptor
 * 
 * Responsibility: Validates JWT tokens in gRPC metadata and extracts authenticated user context.
 * Does NOT: Handle HTTP security (see SecurityConfig), generate tokens (see JwtService), manage authorization.
 * 
 * Distributed Systems Concept: Stateless authentication via JWT enables horizontal scaling without
 * session affinity. Each service instance independently validates tokens, eliminating single point
 * of failure and enabling load balancing across instances.
 * 
 * JWT Flow:
 * 1. Client includes JWT in gRPC metadata: authorization: Bearer <token>
 * 2. Interceptor extracts token, validates signature and expiration (via JwtService)
 * 3. Extracts user_id from token claims
 * 4. Stores user_id in gRPC Context for service layer access
 * 
 * Public Methods (whitelist - no authentication required):
 * - Add methods here that should be accessible without authentication
 * 
 * See: specs/001-ubiquitous-messaging-platform/authentication-jwt-research.md Decision 3, 6, 7
 */
@Component
public class AuthenticationInterceptor implements ServerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationInterceptor.class);
    
    /**
     * Authorization metadata key for JWT token.
     * Format: "authorization: Bearer <token>"
     */
    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    
    /**
     * Context key for storing authenticated user ID.
     * Services can access via: USER_ID_CONTEXT_KEY.get()
     */
    public static final Context.Key<String> USER_ID_CONTEXT_KEY = Context.key("userId");
    
    /**
     * Context key for storing authenticated username.
     */
    public static final Context.Key<String> USERNAME_CONTEXT_KEY = Context.key("username");
    
    /**
     * Context key for storing user role.
     */
    public static final Context.Key<String> ROLE_CONTEXT_KEY = Context.key("role");
    
    /**
     * Public methods that don't require authentication.
     * 
     * POC: Empty set - all methods require authentication for testing.
     * Production: Add health check methods, public endpoints.
     * 
     * Example:
     * PUBLIC_METHODS.add("chat.ChatService/HealthCheck");
     */
    private static final Set<String> PUBLIC_METHODS = new HashSet<>();
    
    @Autowired
    private JwtService jwtService;
    
    /**
     * Intercepts gRPC call to validate JWT token and extract user context.
     * 
     * Authentication Flow:
     * 1. Check if method is public (whitelisted) - skip authentication if yes
     * 2. Extract Authorization header from metadata
     * 3. Parse JWT token (format: "Bearer <token>")
     * 4. Validate token signature and expiration
     * 5. Extract user_id, username, role from claims
     * 6. Store in gRPC Context for service layer access
     * 
     * Error Responses:
     * - UNAUTHENTICATED: Missing token, invalid signature, expired token
     * - PERMISSION_DENIED: Valid token but insufficient permissions (handled by service layer)
     * 
     * @param call    ServerCall
     * @param headers Request metadata
     * @param next    Next handler in chain
     * @return ServerCall.Listener
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        String methodName = call.getMethodDescriptor().getFullMethodName();
        logger.debug("Intercepting gRPC call: {}", methodName);
        
        // Check if method is public (no authentication required)
        if (PUBLIC_METHODS.contains(methodName)) {
            logger.debug("Public method - skipping authentication: {}", methodName);
            return next.startCall(call, headers);
        }
        
        // Extract Authorization header
        String authHeader = headers.get(AUTHORIZATION_KEY);
        
        if (authHeader == null || authHeader.isEmpty()) {
            logger.warn("Missing Authorization header for method: {}", methodName);
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Authorization header missing"),
                    new Metadata()
            );
            return new ServerCall.Listener<ReqT>() {};
        }
        
        // Validate "Bearer " prefix
        if (!authHeader.startsWith("Bearer ")) {
            logger.warn("Invalid Authorization header format (missing 'Bearer ' prefix): {}", methodName);
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Invalid Authorization header format. Expected: Bearer <token>"),
                    new Metadata()
            );
            return new ServerCall.Listener<ReqT>() {};
        }
        
        // Extract token
        String token = authHeader.substring(7);  // Remove "Bearer " prefix
        
        // Validate token
        if (!jwtService.validateToken(token)) {
            logger.warn("Invalid or expired JWT token for method: {}", methodName);
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Invalid or expired JWT token"),
                    new Metadata()
            );
            return new ServerCall.Listener<ReqT>() {};
        }
        
        // Extract claims
        String userId = jwtService.extractUserId(token);
        String username = jwtService.extractUsername(token);
        String role = jwtService.extractRole(token);
        
        if (userId == null || username == null || role == null) {
            logger.error("Failed to extract claims from valid token for method: {}", methodName);
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Invalid token claims"),
                    new Metadata()
            );
            return new ServerCall.Listener<ReqT>() {};
        }
        
        logger.info("Authenticated user: {} (userId: {}, role: {}) for method: {}", 
                username, userId, role, methodName);
        
        // Create context with user information
        Context context = Context.current()
                .withValue(USER_ID_CONTEXT_KEY, userId)
                .withValue(USERNAME_CONTEXT_KEY, username)
                .withValue(ROLE_CONTEXT_KEY, role);
        
        // Continue with authenticated context
        return Contexts.interceptCall(context, call, headers, next);
    }
}
