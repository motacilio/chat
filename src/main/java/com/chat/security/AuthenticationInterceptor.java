package com.chat.security;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC Authentication Interceptor
 * 
 * Responsibility: Extracts user_id from JWT token in gRPC metadata and validates authentication.
 * Does NOT: Handle HTTP endpoint security (see SecurityConfig), manage authorization (see service layer).
 * 
 * Distributed Systems Concept: Stateless authentication via JWT enables horizontal scaling without
 * session affinity. Each service instance independently validates tokens, eliminating single point
 * of failure and enabling load balancing across instances (FR-002).
 * 
 * JWT Flow:
 * 1. Client includes JWT in gRPC metadata: Authorization: Bearer <token>
 * 2. Interceptor extracts token, validates signature (future: verify with OAuth2 server)
 * 3. Extracts user_id from token claims
 * 4. Stores user_id in gRPC Context for service layer access
 * 
 * MVP Note: Per spec A-002 ("Users are pre-authenticated"), authentication is DEFERRED.
 * This interceptor is a placeholder for production OAuth2 JWT validation.
 */
@Component
public class AuthenticationInterceptor implements ServerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationInterceptor.class);
    
    /* PRODUCTION: Authorization metadata key
    private static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY =
        Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    */
    
    /**
     * Intercept gRPC call to extract and validate JWT token.
     * 
     * MVP Implementation: Allows all requests (authentication deferred per spec A-002).
     * 
     * Production Implementation (commented below):
     * - Extract Authorization header from metadata
     * - Parse JWT token (Bearer <token>)
     * - Validate token signature and expiration
     * - Extract user_id from claims
     * - Store user_id in Context for service layer access
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
        
        // MVP: Skip authentication (deferred per spec A-002)
        logger.debug("Authentication interceptor called for method: {}", call.getMethodDescriptor().getFullMethodName());
        
        /* PRODUCTION IMPLEMENTATION (uncomment when OAuth2 configured):
        
        // Extract Authorization header
        String authHeader = headers.get(AUTHORIZATION_METADATA_KEY);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid Authorization header"), headers);
            return new ServerCall.Listener<ReqT>() {};
        }
        
        try {
            // Extract token
            String token = authHeader.substring(7);  // Remove "Bearer " prefix
            
            // Validate JWT token and extract user_id
            String userId = validateTokenAndExtractUserId(token);
            
            // Store user_id in gRPC Context for service layer access
            Context context = Context.current().withValue(USER_ID_CONTEXT_KEY, userId);
            
            // Continue with context
            return Contexts.interceptCall(context, call, headers, next);
            
        } catch (Exception e) {
            logger.warn("JWT validation failed", e);
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid JWT token"), headers);
            return new ServerCall.Listener<ReqT>() {};
        }
        */
        
        // MVP: Pass through without authentication
        return next.startCall(call, headers);
    }
    
    /* PRODUCTION: Context key for storing user_id
    public static final Context.Key<String> USER_ID_CONTEXT_KEY = Context.key("userId");
    */
    
    /* PRODUCTION: JWT validation method
    private String validateTokenAndExtractUserId(String token) {
        // Use Spring Security OAuth2 JWT decoder
        // Jwt jwt = jwtDecoder.decode(token);
        // return jwt.getClaim("user_id");
        throw new UnsupportedOperationException("JWT validation not implemented - auth deferred per spec A-002");
    }
    */
}
