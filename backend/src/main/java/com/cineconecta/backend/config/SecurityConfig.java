package com.cineconecta.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.cineconecta.backend.security.JsonSecurityHandlers;
import com.cineconecta.backend.security.JwtAuthFilter;
import com.cineconecta.backend.service.JwtService;

/**
 * Two stateless JWT chains (RNF3, RNF4):
 *   1. /api/admin/** : administrators from Admin_user (MongoDB), role ADMIN.
 *   2. /api/**       : customer accounts from ACCOUNTS (Oracle), role CLIENT.
 * The server checks the role on every request, whatever the UI shows: an admin token
 * gets 403 on customer routes and vice versa. Everything else is denied except Swagger.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String[] API_DOCS = { "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html" };

    // Cost 10, the same as the BCrypt hashes in the Oracle and MongoDB seeds (RNF1).
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminChain(HttpSecurity http, JwtService jwtService) throws Exception {
        return stateless(http.securityMatcher("/api/admin/**"), jwtService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain clientChain(HttpSecurity http, JwtService jwtService) throws Exception {
        return stateless(http.securityMatcher("/api/**"), jwtService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/plans", "/api/plans/**").permitAll()
                        .anyRequest().hasRole("CLIENT"))
                .build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain defaultChain(HttpSecurity http, JwtService jwtService) throws Exception {
        return stateless(http, jwtService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(API_DOCS).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    private static HttpSecurity stateless(HttpSecurity http, JwtService jwtService) throws Exception {
        JsonSecurityHandlers handlers = new JsonSecurityHandlers();
        return http
                .csrf(AbstractHttpConfigurer::disable)   // no cookies: the JWT travels in a header
                .cors(Customizer.withDefaults())         // uses CorsConfig#corsConfigurationSource
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
    }
}
