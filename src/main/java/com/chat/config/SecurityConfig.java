package com.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security Configuration
 * 
 * Responsibility: Configures security for HTTP endpoints (Actuator, REST APIs, JWT authentication).
 * Does NOT: Secure gRPC endpoints (handled by AuthenticationInterceptor).
 * 
 * Distributed Systems Concept: JWT-based authentication enables stateless authorization across
 * distributed service instances. No session state stored in-memory - user_id extracted from JWT
 * token claims on every request.
 * 
 * POC Implementation: Permits authentication endpoint (/api/auth/login) and actuator endpoints.
 * All other endpoints require authentication in production.
 * 
 * See: specs/001-ubiquitous-messaging-platform/authentication-jwt-research.md
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures HTTP security filter chain.
     * 
     * POC Configuration:
     * - Permits /api/auth/login (public login endpoint)
     * - Permits /actuator/** (health checks, metrics)
     * - Permits all other requests (for backward compatibility during POC)
     * 
     * Production Configuration:
     * - Change .anyRequest().permitAll() to .anyRequest().authenticated()
     * - Add JWT validation filter for protected endpoints
     * 
     * @param http HttpSecurity builder
     * @return SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless JWT authentication
            .csrf(csrf -> csrf.disable())
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()     // Public: Login endpoint
                .requestMatchers("/api/auth/health").permitAll()    // Public: Auth service health
                .requestMatchers("/actuator/**").permitAll()        // Public: Actuator endpoints
                .anyRequest().permitAll()                           // POC: Permit all (change to authenticated() in production)
            );
        
        return http.build();
    }
}
