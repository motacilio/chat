package com.chat.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Global gRPC Exception Handler
 * 
 * Responsibility: Maps domain exceptions to gRPC Status codes with error details.
 * Does NOT: Handle business logic (see service layer), modify request/response data.
 * 
 * Distributed Systems Concept: gRPC uses HTTP/2 status codes plus application-specific error details.
 * Proper exception mapping enables clients to distinguish between transient failures (retry-able)
 * and permanent errors (non-retry-able), critical for distributed system reliability.
 * 
 * Standard gRPC Status Codes:
 * - INVALID_ARGUMENT: Client sent malformed request (400-like)
 * - NOT_FOUND: Resource does not exist (404-like)
 * - ALREADY_EXISTS: Duplicate identifier (409-like)
 * - PERMISSION_DENIED: Authorization failure (403-like)
 * - UNAUTHENTICATED: Authentication required (401-like)
 * - UNAVAILABLE: Service temporarily unavailable (503-like, retry-able)
 * - INTERNAL: Server error (500-like, non-retry-able)
 */
@Component
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Convert IllegalArgumentException to INVALID_ARGUMENT status.
     * Used for validation failures (invalid UUID, message size exceeded, etc.).
     * 
     * @param ex IllegalArgumentException
     * @return StatusRuntimeException with INVALID_ARGUMENT code
     */
    public StatusRuntimeException handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("Invalid argument: {}", ex.getMessage());
        return Status.INVALID_ARGUMENT
            .withDescription(ex.getMessage())
            .withCause(ex)
            .asRuntimeException();
    }
    
    /**
     * Convert IllegalStateException to FAILED_PRECONDITION status.
     * Used for state machine violations (e.g., cannot mark SENT message as READ directly).
     * 
     * @param ex IllegalStateException
     * @return StatusRuntimeException with FAILED_PRECONDITION code
     */
    public StatusRuntimeException handleIllegalState(IllegalStateException ex) {
        logger.warn("Invalid state: {}", ex.getMessage());
        return Status.FAILED_PRECONDITION
            .withDescription(ex.getMessage())
            .withCause(ex)
            .asRuntimeException();
    }
    
    /**
     * Convert generic RuntimeException to INTERNAL status.
     * Used for unexpected server errors.
     * 
     * @param ex RuntimeException
     * @return StatusRuntimeException with INTERNAL code
     */
    public StatusRuntimeException handleGenericException(RuntimeException ex) {
        logger.error("Internal server error", ex);
        return Status.INTERNAL
            .withDescription("An internal error occurred. Please try again later.")
            .withCause(ex)
            .asRuntimeException();
    }
    
    /**
     * Create NOT_FOUND status for missing resources.
     * 
     * @param resourceType Resource type (e.g., "Conversation", "Message")
     * @param resourceId   Resource identifier
     * @return StatusRuntimeException with NOT_FOUND code
     */
    public StatusRuntimeException notFound(String resourceType, String resourceId) {
        String message = String.format("%s with ID %s not found", resourceType, resourceId);
        logger.warn(message);
        return Status.NOT_FOUND
            .withDescription(message)
            .asRuntimeException();
    }
    
    /**
     * Create ALREADY_EXISTS status for duplicate identifiers.
     * Used for idempotency violations (FR-006: duplicate message_id).
     * 
     * @param resourceType Resource type (e.g., "Message")
     * @param resourceId   Duplicate identifier
     * @return StatusRuntimeException with ALREADY_EXISTS code
     */
    public StatusRuntimeException alreadyExists(String resourceType, String resourceId) {
        String message = String.format("%s with ID %s already exists", resourceType, resourceId);
        logger.warn(message);
        return Status.ALREADY_EXISTS
            .withDescription(message)
            .asRuntimeException();
    }
    
    /**
     * Create PERMISSION_DENIED status for authorization failures.
     * Used when user is not participant in conversation (FR-013).
     * 
     * @param message Description of permission violation
     * @return StatusRuntimeException with PERMISSION_DENIED code
     */
    public StatusRuntimeException permissionDenied(String message) {
        logger.warn("Permission denied: {}", message);
        return Status.PERMISSION_DENIED
            .withDescription(message)
            .asRuntimeException();
    }
}

