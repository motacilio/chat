package com.chat.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Security Headers Filter
 * 
 * Responsibility: Add HTTP security headers to all responses to protect against common web vulnerabilities.
 * Does NOT: Handle authentication/authorization (see SecurityConfig), validate business logic.
 * 
 * Security Headers Added:
 * - X-Content-Type-Options: nosniff (prevents MIME type sniffing)
 * - X-Frame-Options: DENY (prevents clickjacking attacks)
 * - X-XSS-Protection: 1; mode=block (enables XSS filter in older browsers)
 * - Strict-Transport-Security: max-age=31536000 (enforces HTTPS)
 * - Content-Security-Policy: default-src 'self' (prevents XSS/injection attacks)
 * - Referrer-Policy: strict-origin-when-cross-origin (controls referrer information)
 * - Permissions-Policy: controls browser features (camera, microphone, etc.)
 * 
 * Educational Value: Demonstrates defense-in-depth security strategy - multiple layers
 * of protection against OWASP Top 10 vulnerabilities. Each header addresses specific
 * attack vectors (XSS, clickjacking, MIME sniffing, etc.).
 * 
 * Pattern: Filter Chain Pattern - intercepts all HTTP requests/responses to apply
 * cross-cutting security concerns.
 * 
 * @see <a href="https://owasp.org/www-project-secure-headers/">OWASP Secure Headers Project</a>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityHeadersFilter.class);
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // X-Content-Type-Options: Prevents MIME type sniffing
        // Attack prevented: Attacker uploads .txt file with JavaScript, browser interprets as script
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        
        // X-Frame-Options: Prevents clickjacking
        // Attack prevented: Attacker embeds our app in iframe, tricks users into clicking hidden buttons
        httpResponse.setHeader("X-Frame-Options", "DENY");
        
        // X-XSS-Protection: Enables XSS filter (legacy browsers)
        // Note: Modern browsers use CSP instead, but this provides defense-in-depth
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        
        // Strict-Transport-Security (HSTS): Enforces HTTPS for 1 year
        // Attack prevented: Man-in-the-middle attacks via HTTP downgrade
        // Note: Only effective if served over HTTPS (ignored on HTTP)
        httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        
        // Content-Security-Policy: Restricts resource loading to prevent XSS
        // default-src 'self': Only load resources from same origin
        // script-src 'self' 'unsafe-inline': Allow inline scripts (needed for some frameworks)
        // Note: 'unsafe-inline' weakens protection - remove in production if possible
        httpResponse.setHeader("Content-Security-Policy", 
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: https:; " +
            "font-src 'self' data:; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none'");
        
        // Referrer-Policy: Controls how much referrer information is sent
        // strict-origin-when-cross-origin: Send origin only when protocol security level stays the same
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // Permissions-Policy (formerly Feature-Policy): Restrict browser features
        // Disable camera, microphone, geolocation, etc. unless needed
        httpResponse.setHeader("Permissions-Policy", 
            "camera=(), " +
            "microphone=(), " +
            "geolocation=(), " +
            "payment=(), " +
            "usb=(), " +
            "magnetometer=(), " +
            "gyroscope=()");
        
        // Cache-Control for sensitive endpoints
        String requestURI = httpRequest.getRequestURI();
        if (requestURI.contains("/actuator") || requestURI.contains("/api/")) {
            // Prevent caching of API responses and actuator endpoints
            httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
            httpResponse.setHeader("Pragma", "no-cache");
            httpResponse.setHeader("Expires", "0");
        }
        
        // Log security headers applied (debug level to avoid noise)
        logger.debug("[SECURITY] Security headers applied to {}", requestURI);
        
        // Continue filter chain
        chain.doFilter(request, response);
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("[SECURITY] SecurityHeadersFilter initialized - adding security headers to all responses");
    }
    
    @Override
    public void destroy() {
        logger.info("[SECURITY] SecurityHeadersFilter destroyed");
    }
}
