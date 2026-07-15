package com.example.backend.config;

import com.example.backend.service.AccountSessionService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(FinancialsSecurityProperties.class)
public class ApiSecurityConfig {

  private static final String FINANCIALS_ROLE = "FINANCIALS";

  @Bean
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      WorkspaceSessionAuthenticationFilter workspaceSessionFilter,
      CookieCsrfTokenRepository csrfTokenRepository)
      throws Exception {
    http.csrf(
            (csrf) ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .ignoringRequestMatchers("/api/v1/admin/**", "/actuator/**"))
        .sessionManagement(
            (session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors((cors) -> {})
        .httpBasic(
            (basic) ->
                basic.authenticationEntryPoint(
                    (request, response, authenticationException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .authorizeHttpRequests(
            (requests) -> {
              requests
                  .requestMatchers(HttpMethod.OPTIONS, "/**")
                  .permitAll()
                  .requestMatchers("/actuator/health", "/actuator/info")
                  .permitAll()
                  .requestMatchers(
                      "/api/v1/auth/csrf", "/api/v1/auth/signup", "/api/v1/auth/signin")
                  .permitAll()
                  .requestMatchers("/api/v1/auth/**")
                  .hasRole(WorkspaceSessionAuthenticationFilter.WORKSPACE_ROLE)
                  .requestMatchers("/actuator/metrics", "/actuator/metrics/**")
                  .hasRole(FINANCIALS_ROLE)
                  .requestMatchers("/actuator/**")
                  .denyAll()
                  .requestMatchers("/api/v1/admin/**")
                  .hasRole(FINANCIALS_ROLE)
                  .requestMatchers("/api/v1/financials/**")
                  .hasRole(WorkspaceSessionAuthenticationFilter.WORKSPACE_ROLE)
                  .anyRequest()
                  .permitAll();
            });

    http.addFilterBefore(workspaceSessionFilter, BasicAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  WorkspaceSessionAuthenticationFilter workspaceSessionAuthenticationFilter(
      AccountSessionService accountSessionService) {
    return new WorkspaceSessionAuthenticationFilter(accountSessionService);
  }

  @Bean
  FilterRegistrationBean<WorkspaceSessionAuthenticationFilter> workspaceSessionFilterRegistration(
      WorkspaceSessionAuthenticationFilter filter) {
    FilterRegistrationBean<WorkspaceSessionAuthenticationFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
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
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-XSRF-TOKEN",
            AuthenticatedRequestWorkspace.WORKSPACE_ID_HEADER,
            RequestObservabilityFilter.REQUEST_ID_HEADER));
    configuration.setAllowCredentials(true);
    configuration.setExposedHeaders(
        List.of("Content-Disposition", RequestObservabilityFilter.REQUEST_ID_HEADER));
    configuration.setMaxAge(3_600L);
    return (request) -> configuration;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  CookieCsrfTokenRepository csrfTokenRepository(FinancialsSecurityProperties securityProperties) {
    CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
    repository.setHeaderName("X-XSRF-TOKEN");
    repository.setCookieCustomizer(
        (cookie) ->
            cookie.httpOnly(true).secure(securityProperties.sessionCookieSecure()).path("/"));
    return repository;
  }
}
