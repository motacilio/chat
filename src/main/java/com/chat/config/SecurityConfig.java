package com.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security Configuration
 * 
 * Responsibility: Configures security for HTTP endpoints (Actuator, future REST APIs).
 * Does NOT: Secure gRPC endpoints (requires custom interceptor - see AuthenticationInterceptor when implemented).
 * 
 * Distributed Systems Concept: JWT-based authentication enables stateless authorization across
 * distributed service instances. No session state stored in-memory - user_id extracted from JWT
 * token claims on every request (FR-002).
 * 
 * MVP Note: Per spec A-002 ("Users are pre-authenticated"), authentication implementation is
 * DEFERRED to post-MVP. This configuration permits all requests for local development.
 * Production deployment requires OAuth2 JWT validation (commented below).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures HTTP security filter chain.
     * 
     * MVP Configuration: Permits all requests (authentication deferred per spec A-002).
     * 
     * Production Configuration (uncomment when auth implemented):
     * - Validate JWT tokens from Authorization header
     * - Extract user_id from token claims
     * - Reject requests with invalid/expired tokens
     * 
     * @param http HttpSecurity builder
     * @return SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // MVP: Disable CSRF for development (enable in production with proper token handling)
            .csrf(csrf -> csrf.disable())
            
            // MVP: Permit all requests (authentication deferred to post-MVP per spec A-002)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()  // Health checks, metrics
                .anyRequest().permitAll()                      // All other requests
            );
        
        /* PRODUCTION CONFIGURATION (uncomment when OAuth2 implemented):
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()  // Public endpoints
                .anyRequest().authenticated()                                         // Require authentication
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())  // Extract user_id from claims
                )
            );
        */
        
        return http.build();
    }
    
    /* PRODUCTION: JWT converter to extract user_id from token claims
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("user_id");  // Extract user_id from JWT claims
        return converter;
    }
    */
}
