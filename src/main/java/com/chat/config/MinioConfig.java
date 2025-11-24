package com.chat.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO Object Storage Configuration (User Story 4 - P2: Upload and Download Files)
 * 
 * Responsibility: Configure MinIO client for file storage operations.
 * Does NOT: Handle file upload logic (see FileStorageService), manage file metadata (see FileMetadata entity).
 * 
 * Distributed Systems Concept: Object Storage for blob data separation.
 * MinIO provides S3-compatible API enabling cloud portability (can swap to AWS S3, Google Cloud Storage, etc.).
 * 
 * Architecture Pattern:
 * - Metadata (filename, size, user): MongoDB (queryable, relational)
 * - Blob content (actual file bytes): MinIO (cost-effective, scalable storage)
 * - Access control: Pre-signed URLs with time-limited access (1 hour per FR-023)
 * 
 * Performance Benefits:
 * - Offloads large file I/O from application servers
 * - Direct client → MinIO upload (multipart resumable protocol)
 * - CDN-friendly (can front MinIO with CloudFront, CloudFlare, etc.)
 * 
 * Configuration:
 * - Development: Docker container on localhost:9000
 * - Production: MinIO cluster or S3-compatible cloud service
 * - Bucket: "chat-files" (auto-created if not exists)
 */
@Slf4j
@Configuration
public class MinioConfig {
    
    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;
    
    @Value("${minio.access-key:minioadmin}")
    private String minioAccessKey;
    
    @Value("${minio.secret-key:minioadmin}")
    private String minioSecretKey;
    
    @Value("${minio.bucket-name:chat-files}")
    private String bucketName;
    
    /**
     * Creates MinIO client bean for file operations.
     * 
     * Security Note (POC):
     * - Using hardcoded credentials for development (minioadmin/minioadmin)
     * - PRODUCTION: Use environment variables, Kubernetes secrets, or AWS IAM roles
     * - Never commit credentials to git (add to .gitignore)
     * 
     * @return Configured MinIO client
     */
    @Bean
    public MinioClient minioClient() {
        log.info("Initializing MinIO client - Endpoint: {}, Bucket: {}", minioEndpoint, bucketName);
        
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(minioEndpoint)
                    .credentials(minioAccessKey, minioSecretKey)
                    .build();
            
            log.info("MinIO client initialized successfully");
            return client;
            
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client - Endpoint: {}, Error: {}", 
                    minioEndpoint, e.getMessage(), e);
            throw new RuntimeException("MinIO client initialization failed", e);
        }
    }
    
    /**
     * Bucket name accessor for FileStorageService.
     * 
     * @return Configured bucket name (default: "chat-files")
     */
    @Bean
    public String minioBucketName() {
        return bucketName;
    }
}
