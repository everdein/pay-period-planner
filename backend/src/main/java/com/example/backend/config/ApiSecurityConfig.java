package com.example.backend.config;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(FinancialsSecurityProperties.class)
public class ApiSecurityConfig {

  private static final String FINANCIALS_ROLE = "FINANCIALS";

  @Bean
  SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            (session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors((cors) -> {})
        .httpBasic(
            (basic) ->
                basic.authenticationEntryPoint(
                    (request, response, authenticationException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .authorizeHttpRequests(
            (requests) ->
                requests
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/api/v1/financials/**")
                    .hasRole(FINANCIALS_ROLE)
                    .anyRequest()
                    .permitAll())
        .build();
  }

  @Bean
  UserDetailsService financialsUserDetailsService(
      FinancialsSecurityProperties securityProperties, PasswordEncoder passwordEncoder) {
    UserDetails financialsUser =
        User.withUsername(securityProperties.username())
            .password(passwordEncoder.encode(securityProperties.password()))
            .roles(FINANCIALS_ROLE)
            .build();
    return new InMemoryUserDetailsManager(financialsUser);
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(FinancialsSecurityProperties securityProperties) {
    List<String> allowedOrigins = securityProperties.allowedOrigins();
    if (allowedOrigins.isEmpty()) {
      return (request) -> null;
    }

    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
    configuration.setExposedHeaders(List.of("Content-Disposition"));
    configuration.setMaxAge(3_600L);
    return (request) -> configuration;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}
