package com.chat.controller;

import com.chat.dto.CompleteUploadRequest;
import com.chat.dto.CompleteUploadResponse;
import com.chat.dto.DownloadFileResponse;
import com.chat.dto.InitiateUploadRequest;
import com.chat.dto.InitiateUploadResponse;
import com.chat.dto.MessageEventDto;
import com.chat.model.FileMetadata;
import com.chat.model.Message;
import com.chat.model.FileMetadata.FileUploadStatus;
import com.chat.service.FileStorageService;
import com.chat.service.JwtService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
// quem ler isso é gay
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * REST Controller for file upload/download operations.
 * (User Story 4 - P2: File Upload/Download)
 * 
 * Endpoints:
 * - POST /api/files/initiate: Start resumable upload, receive pre-signed URL
 * - POST /api/files/complete: Finalize upload with checksum validation
 * - GET /api/files/{fileId}/download: Generate time-limited download URL
 * 
 * Security: All endpoints require JWT authentication (extracted from Authorization header).
 * 
 * Architecture:
 * - Controller handles HTTP/DTO layer
 * - FileStorageService handles MinIO interactions
 * - Pre-signed URLs enable direct client-to-MinIO transfer (no proxy overhead)
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private com.chat.service.MessageService messageService;
    
    @Autowired
    private KafkaTemplate<String, MessageEventDto> messageEventKafkaTemplate;

    @Value("${minio.download-url-expiration-seconds:3600}")
    private int downloadUrlExpirationSeconds;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * POST /api/files/initiate
     * 
     * Initiates resumable file upload.
     * 
     * Flow:
     * 1. Validates file size ≤ 2 GB
     * 2. Generates server-side UUID fileId
     * 3. Creates pre-signed PUT URL (1 hour validity)
     * 4. Persists FileMetadata with INITIATED status
     * 5. Returns upload URL to client
     * 
     * @param request Upload request (conversation_id, filename, size_bytes, mime_type)
     * @param authHeader JWT token (format: "Bearer <token>")
     * @return Upload response with pre-signed URL and fileId
     * @throws IllegalArgumentException if file exceeds 2 GB
     */
    @PostMapping("/initiate")
    public ResponseEntity<InitiateUploadResponse> initiateUpload(
            @Valid @RequestBody InitiateUploadRequest request,
            @RequestHeader("Authorization") String authHeader) {
        
        log.info("[FileController] POST /api/files/initiate - filename: {}, size: {} bytes, conversation: {}", 
                 request.getFilename(), request.getSizeBytes(), request.getConversationId());

        // Extract user ID from JWT
        String token = authHeader.replace("Bearer ", "");
        String uploaderId = jwtService.extractUserId(token);

        // Call service to initiate upload (generates pre-signed URL)
        FileMetadata fileMetadata = fileStorageService.initiateUpload(
                request.getFilename(),
                request.getSizeBytes(),
                request.getMimeType(),
                request.getConversationId(),
                uploaderId
        );

        // Build response DTO
        Instant expiresAt = Instant.now().plusSeconds(3600); // Pre-signed URL expires in 1 hour
        InitiateUploadResponse response = InitiateUploadResponse.builder()
                .fileId(fileMetadata.getFileId())
                .uploadUrl(fileMetadata.getStorageUrl()) // Pre-signed PUT URL (not persisted in DB)
                .uploadExpiresAt(ISO_FORMATTER.format(expiresAt.atOffset(ZoneOffset.UTC)))
                .chunkSizeBytes(fileMetadata.getTotalChunks() > 0 
                        ? request.getSizeBytes() / fileMetadata.getTotalChunks() 
                        : 5242880L) // Default 5 MB
                .totalChunks(fileMetadata.getTotalChunks())
                .resumable(true)
                .build();

        log.info("[FileController] Upload initiated - fileId: {}, expires: {}", 
                 fileMetadata.getFileId(), response.getUploadExpiresAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/files/complete
     * 
     * Completes file upload and creates file message.
     * 
     * Flow:
     * 1. Validates file exists in MinIO
     * 2. Compares MD5 checksum (integrity check)
     * 3. Updates FileMetadata status to COMPLETED
     * 4. Sets uploadedAt timestamp
     * 5. Creates Message entity with fileMetadata (XOR: no text)
     * 6. Publishes message to Kafka for async delivery
     * 7. Returns message details to client
     * 
     * Clean Architecture:
     * - Controller orchestrates the flow (HTTP → Service → Kafka)
     * - FileStorageService handles MinIO validation
     * - MessageService handles Message creation and business rules
     * - KafkaTemplate handles async publishing
     * 
     * @param request Completion request (file_id, checksum_md5, sender_id, recipient_ids)
     * @param authHeader JWT token (authorization check)
     * @return File message details (messageId, state, downloadUrl)
     * @throws RuntimeException if file not found or checksum mismatch
     */
    @PostMapping("/complete")
    public ResponseEntity<CompleteUploadResponse> completeUpload(
            @Valid @RequestBody CompleteUploadRequest request,
            @RequestHeader("Authorization") String authHeader) {
        
        log.info("[FileController] POST /api/files/complete - fileId: {}, checksum: {}, sender: {}, recipients: {}", 
                 request.getFileId(), request.getChecksumMd5(), request.getSenderId(), request.getRecipientIds());

        // Extract user ID from JWT (verify matches request.senderId)
        String token = authHeader.replace("Bearer ", "");
        String authenticatedUserId = jwtService.extractUserId(token);
        
        if (!authenticatedUserId.equals(request.getSenderId())) {
            log.warn("[FileController] Sender ID mismatch - JWT: {}, Request: {}", 
                     authenticatedUserId, request.getSenderId());
            throw new SecurityException("Sender ID does not match authenticated user");
        }

        // Step 1-4: Complete upload in MinIO (validates checksum, updates status)
        FileMetadata fileMetadata = fileStorageService.completeUpload(
                request.getFileId(),
                request.getChecksumMd5()
        );

        // Step 5: Create file message (XOR: fileMetadata instead of messageText)
        com.chat.model.Message fileMessage = messageService.createFileMessage(
                fileMetadata.getConversationId(),
                request.getSenderId(),
                request.getRecipientIds(),
                fileMetadata
        );

        // Step 6: Publish to Kafka for async delivery (convert to MessageEventDto like text messages)
        MessageEventDto messageEvent = MessageEventDto.builder()
                .messageId(fileMessage.getMessageId())
                .conversationId(fileMessage.getConversationId())
                .senderId(fileMessage.getSenderId())
                .messageText(null)  // File messages have null messageText (XOR with fileMetadata)
                .sequenceNumber(fileMessage.getSequenceNumber())
                .timestamp(Instant.now().toString())
                .build();
        
        messageEventKafkaTemplate.send("message-events", fileMetadata.getConversationId(), messageEvent);
        log.info("[FileController] File message published to Kafka - messageId: {}, fileId: {}", 
                 fileMessage.getMessageId(), fileMetadata.getFileId());

        // Step 7: Generate download URL for immediate access
        String downloadUrl = fileStorageService.generateDownloadUrl(request.getFileId());
        Instant downloadExpiresAt = Instant.now().plusSeconds(downloadUrlExpirationSeconds);

        // Build response DTO (matches spec.md contract)
        CompleteUploadResponse response = CompleteUploadResponse.builder()
                .messageId(fileMessage.getMessageId())
                .fileId(fileMetadata.getFileId())
                .state(com.chat.model.MessageStatus.SENT.name())
                .uploadStatus(fileMetadata.getUploadStatus().name())
                .filename(fileMetadata.getFilename())
                .sizeBytes(fileMetadata.getSizeBytes())
                .mimeType(fileMetadata.getMimeType())
                .uploadedAt(fileMetadata.getUploadedAt() != null 
                        ? ISO_FORMATTER.format(fileMetadata.getUploadedAt().atOffset(ZoneOffset.UTC)) 
                        : null)
                .conversationId(fileMetadata.getConversationId())
                .downloadUrl(downloadUrl)
                .downloadExpiresAt(ISO_FORMATTER.format(downloadExpiresAt.atOffset(ZoneOffset.UTC)))
                .build();

        log.info("[FileController] Upload completed - messageId: {}, fileId: {}, state: {}", 
                 fileMessage.getMessageId(), fileMetadata.getFileId(), fileMessage.getStateHistory().get(0).getState());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/files/{fileId}/download
     * 
     * Generates time-limited download URL (pre-signed GET URL).
     * 
     * Flow:
     * 1. Validates file exists and status is COMPLETED
     * 2. Generates pre-signed GET URL (1 hour validity per FR-023)
     * 3. Returns download URL to client
     * 
     * Client downloads directly from MinIO (no proxy through server).
     * 
     * @param fileId File UUID
     * @param authHeader JWT token (authorization check)
     * @return Download URL and file metadata
     * @throws RuntimeException if file not found or not COMPLETED
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<DownloadFileResponse> generateDownloadUrl(
            @PathVariable String fileId,
            @RequestHeader("Authorization") String authHeader) {
        
        log.info("[FileController] GET /api/files/{}/download", fileId);

        // Call service to generate pre-signed download URL
        String downloadUrl = fileStorageService.generateDownloadUrl(fileId);
        FileMetadata fileMetadata = fileStorageService.getFileMetadata(fileId);

        // Build response DTO
        Instant expiresAt = Instant.now().plusSeconds(downloadUrlExpirationSeconds);
        DownloadFileResponse response = DownloadFileResponse.builder()
                .fileId(fileMetadata.getFileId())
                .downloadUrl(downloadUrl)
                .downloadExpiresAt(ISO_FORMATTER.format(expiresAt.atOffset(ZoneOffset.UTC)))
                .filename(fileMetadata.getFilename())
                .sizeBytes(fileMetadata.getSizeBytes())
                .mimeType(fileMetadata.getMimeType())
                .build();

        log.info("[FileController] Download URL generated - fileId: {}, expires: {}", 
                 fileId, response.getDownloadExpiresAt());

        return ResponseEntity.ok(response);
    }
}
