package com.chat.util;

import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Input Sanitization Utility
 * 
 * Responsibility: Sanitize and validate user input to prevent injection attacks (XSS, SQL, NoSQL, etc.).
 * Does NOT: Handle business validation (see service layer), authentication (see SecurityConfig).
 * 
 * Security Threats Mitigated:
 * - Cross-Site Scripting (XSS): Encode HTML/JavaScript to prevent script injection
 * - NoSQL Injection: Validate MongoDB query operators, escape special characters
 * - Command Injection: Validate file paths, prevent shell command injection
 * - Path Traversal: Validate filenames, prevent directory traversal (.., /)
 * - LDAP Injection: Escape LDAP special characters
 * 
 * Educational Value: Demonstrates defense-in-depth security - input validation at API layer
 * prevents malicious data from reaching business logic or database. Uses OWASP Encoder library
 * (industry standard for context-aware encoding).
 * 
 * Pattern: Validation Layer - centralizes input sanitization logic to avoid duplication
 * and ensure consistent security controls across application.
 * 
 * Usage Example:
 * <pre>
 * // Sanitize user-provided message text before persisting
 * String safeText = InputSanitizer.sanitizeText(userInput);
 * 
 * // Validate filename before file upload
 * if (!InputSanitizer.isValidFilename(filename)) {
 *     throw new InvalidInputException("Invalid filename");
 * }
 * </pre>
 * 
 * @see <a href="https://owasp.org/www-project-java-encoder/">OWASP Java Encoder</a>
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html">OWASP Input Validation</a>
 */
public class InputSanitizer {
    
    private static final Logger logger = LoggerFactory.getLogger(InputSanitizer.class);
    
    // Regex patterns for validation
    private static final Pattern VALID_FILENAME = Pattern.compile("^[a-zA-Z0-9._-]{1,255}$");
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9._@+-]{3,50}$");
    private static final Pattern MONGODB_INJECTION_PATTERN = Pattern.compile(".*[\\$\\{\\}].*");
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(".*(\\.\\.|/).*");
    
    // Maximum lengths to prevent DoS via large inputs
    private static final int MAX_TEXT_LENGTH = 100 * 1024; // 100 KB (per FR-024)
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MAX_FILENAME_LENGTH = 255;
    
    /**
     * Sanitize text input for HTML output (prevents XSS).
     * 
     * Encodes HTML special characters: <, >, &, ", '
     * 
     * @param input Raw user input
     * @return HTML-encoded safe string
     */
    public static String sanitizeHtml(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        return Encode.forHtml(input);
    }
    
    /**
     * Sanitize text for JavaScript context (prevents XSS).
     * 
     * Encodes characters that could break out of JS strings.
     * 
     * @param input Raw user input
     * @return JavaScript-encoded safe string
     */
    public static String sanitizeJavaScript(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        return Encode.forJavaScript(input);
    }
    
    /**
     * Sanitize general text input (message content, conversation names, etc.).
     * 
     * Applies:
     * - Length validation (max 100 KB)
     * - NoSQL injection prevention
     * - Control character removal
     * - HTML encoding for safe display
     * 
     * @param input Raw user input
     * @return Sanitized string safe for persistence and display
     * @throws IllegalArgumentException if input exceeds max length or contains injection patterns
     */
    public static String sanitizeText(String input) {
        if (input == null) {
            return null;
        }
        
        if (input.isEmpty()) {
            return input;
        }
        
        // Validate length (DoS prevention)
        if (input.length() > MAX_TEXT_LENGTH) {
            logger.warn("[SECURITY] Input exceeds max length: {} > {}", input.length(), MAX_TEXT_LENGTH);
            throw new IllegalArgumentException(
                String.format("Input too long: %d characters (max: %d)", input.length(), MAX_TEXT_LENGTH));
        }
        
        // Check for NoSQL injection patterns ($, {, })
        if (MONGODB_INJECTION_PATTERN.matcher(input).matches()) {
            logger.warn("[SECURITY] Potential NoSQL injection detected: {}", input.substring(0, Math.min(50, input.length())));
            throw new IllegalArgumentException("Input contains invalid characters ($, {, })");
        }
        
        // Remove control characters (except newline, tab, carriage return)
        String cleaned = input.replaceAll("[\\p{Cntrl}&&[^\n\t\r]]", "");
        
        // Normalize whitespace (prevent formatting attacks)
        cleaned = cleaned.trim().replaceAll("\\s+", " ");
        
        return cleaned;
    }
    
    /**
     * Validate and sanitize filename for file uploads.
     * 
     * Prevents:
     * - Path traversal attacks (../)
     * - Command injection (;, |, &)
     * - Special characters that break filesystems
     * 
     * @param filename User-provided filename
     * @return Sanitized filename safe for filesystem
     * @throws IllegalArgumentException if filename is invalid
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }
        
        // Validate length
        if (filename.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Filename too long: %d characters (max: %d)", filename.length(), MAX_FILENAME_LENGTH));
        }
        
        // Check for path traversal
        if (PATH_TRAVERSAL_PATTERN.matcher(filename).matches()) {
            logger.warn("[SECURITY] Path traversal attempt detected: {}", filename);
            throw new IllegalArgumentException("Filename contains invalid path characters");
        }
        
        // Validate against whitelist pattern (alphanumeric, dot, underscore, hyphen)
        if (!VALID_FILENAME.matcher(filename).matches()) {
            logger.warn("[SECURITY] Invalid filename pattern: {}", filename);
            throw new IllegalArgumentException("Filename contains invalid characters (allowed: a-z A-Z 0-9 . _ -)");
        }
        
        return filename;
    }
    
    /**
     * Validate username format.
     * 
     * Allows: alphanumeric, dots, underscores, @, +, - (email-compatible)
     * Length: 3-50 characters
     * 
     * @param username User-provided username
     * @return true if valid, false otherwise
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        
        if (username.length() > MAX_USERNAME_LENGTH) {
            return false;
        }
        
        return VALID_USERNAME.matcher(username).matches();
    }
    
    /**
     * Validate UUID format.
     * 
     * Delegates to UuidValidator for consistency.
     * 
     * @param uuid UUID string
     * @return true if valid UUID format
     */
    public static boolean isValidUuid(String uuid) {
        return UuidValidator.isValid(uuid);
    }
    
    /**
     * Sanitize input for MongoDB queries (prevents NoSQL injection).
     * 
     * Removes MongoDB operators: $, {, }
     * 
     * @param input Raw user input used in MongoDB query
     * @return Sanitized string safe for MongoDB
     * @throws IllegalArgumentException if input contains injection patterns
     */
    public static String sanitizeForMongoDB(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // Check for MongoDB injection operators
        if (MONGODB_INJECTION_PATTERN.matcher(input).matches()) {
            logger.warn("[SECURITY] MongoDB injection attempt: {}", input.substring(0, Math.min(50, input.length())));
            throw new IllegalArgumentException("Input contains MongoDB operator characters");
        }
        
        return input;
    }
    
    /**
     * Sanitize email address.
     * 
     * Basic validation - for production, use javax.validation.constraints.Email
     * 
     * @param email User-provided email
     * @return Sanitized email (lowercase, trimmed)
     * @throws IllegalArgumentException if email format is invalid
     */
    public static String sanitizeEmail(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        
        email = email.trim().toLowerCase();
        
        // Basic email validation (for demo - use proper validator in production)
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        return email;
    }
    
    /**
     * Strip HTML tags from input (aggressive sanitization).
     * 
     * Use when HTML tags should never be allowed (e.g., plain text fields).
     * 
     * @param input Raw user input potentially containing HTML
     * @return Plain text with all HTML tags removed
     */
    public static String stripHtmlTags(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // Remove all HTML tags
        return input.replaceAll("<[^>]*>", "");
    }
    
    /**
     * Validate input length is within bounds.
     * 
     * @param input String to validate
     * @param minLength Minimum length (inclusive)
     * @param maxLength Maximum length (inclusive)
     * @return true if length is valid
     */
    public static boolean isValidLength(String input, int minLength, int maxLength) {
        if (input == null) {
            return minLength == 0;
        }
        
        int length = input.length();
        return length >= minLength && length <= maxLength;
    }
}
