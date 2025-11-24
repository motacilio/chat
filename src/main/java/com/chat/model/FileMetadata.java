package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * FileMetadata Entity (User Story 4 - P2: Upload and Download Files)
 * 
 * Responsibility: Stores metadata about uploaded files in Object Storage (MinIO).
 * Does NOT: Store file content (handled by MinIO), manage file permissions (see FileStorageService).
 * 
 * Distributed Systems Concept: Separation of metadata (MongoDB) from blob storage (MinIO).
 * This pattern enables independent scaling of structured metadata queries vs large file storage.
 * Pre-signed URLs provide time-limited access without exposing storage credentials.
 * 
 * Storage Strategy:
 * - Metadata (this entity): MongoDB for queryability and relationships
 * - File content: MinIO object storage for cost-effective blob handling
 * - Download access: Pre-signed URLs (1 hour validity per FR-023)
 * 
 * Performance:
 * - File queries by conversation: O(log n) via conversationId index
 * - Checksum validation: MD5 hash prevents corruption and enables deduplication
 * - Max file size: 2 GB (enforced at upload initiation per FR-024)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "file_metadata")
public class FileMetadata {
    
    /**
     * MongoDB internal ID
     */
    @Id
    private String id;
    
    /**
     * UUID - unique file identifier (server-generated on upload initiation)
     * Used for MinIO object key and download URL generation
     */
    @Indexed(unique = true)
    private String fileId;
    
    /**
     * Original filename provided by uploader
     * Examples: "project-presentation.pdf", "screenshot-2025.png"
     */
    private String filename;
    
    /**
     * File size in bytes (validated during initiation)
     * Constraint: Must be ≤ 2,147,483,648 bytes (2 GB per FR-024)
     */
    private Long sizeBytes;
    
    /**
     * MIME type for content type handling
     * Examples: "application/pdf", "image/png", "video/mp4"
     */
    private String mimeType;
    
    /**
     * MinIO storage URL (internal reference)
     * Format: "s3://chat-files/{fileId}"
     * NOT exposed to clients (use pre-signed download URL instead)
     */
    private String storageUrl;
    
    /**
     * MD5 checksum for integrity validation (hex string)
     * Calculated by client during upload, verified by server on completion
     * Prevents corruption and enables deduplication
     */
    private String checksumMd5;
    
    /**
     * Upload completion timestamp
     * Set when upload finalized (not when initiated)
     */
    private Instant uploadedAt;
    
    /**
     * Conversation this file belongs to (for authorization and context)
     * Indexed for querying files by conversation
     */
    @Indexed
    private String conversationId;
    
    /**
     * User who uploaded the file (user_id UUID)
     * Used for authorization checks and audit trails
     */
    @Indexed
    private String uploaderId;
    
    /**
     * Upload status tracking
     * INITIATED: Upload URL generated, waiting for chunks
     * UPLOADING: Chunks being received (resumable protocol)
     * COMPLETED: Upload finalized and verified
     * FAILED: Upload failed (checksum mismatch, timeout, etc.)
     */
    @Builder.Default
    private FileUploadStatus uploadStatus = FileUploadStatus.INITIATED;
    
    /**
     * Optional: Number of chunks uploaded (for resumable protocol tracking)
     * Enables progress calculation and resume-from-offset functionality
     */
    private Integer chunksUploaded;
    
    /**
     * Optional: Total chunks expected (calculated from file size / chunk size)
     * Chunk size: 5 MB (5,242,880 bytes) per research.md Decision 4
     */
    private Integer totalChunks;
    
    /**
     * File upload status enum
     */
    public enum FileUploadStatus {
        /**
         * Upload initiated, waiting for file content
         */
        INITIATED,
        
        /**
         * Chunks being uploaded (resumable protocol in progress)
         */
        UPLOADING,
        
        /**
         * Upload completed and verified successfully
         */
        COMPLETED,
        
        /**
         * Upload failed (checksum mismatch, timeout, etc.)
         */
        FAILED
    }
}
