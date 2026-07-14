package com.example.backend.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionConfigurationGuard implements ApplicationRunner {

  private static final String PROD_PROFILE = "prod";

  private final Environment environment;
  private final FinancialsSecurityProperties securityProperties;

  public ProductionConfigurationGuard(
      Environment environment, FinancialsSecurityProperties securityProperties) {
    this.environment = environment;
    this.securityProperties = securityProperties;
  }

  @Override
  public void run(ApplicationArguments args) {
    validate(
        Arrays.asList(environment.getActiveProfiles()),
        securityProperties.username(),
        securityProperties.password(),
        securityProperties.allowedOrigins(),
        securityProperties.sessionCookieSecure());
  }

  static void validate(
      List<String> activeProfiles,
      String username,
      String password,
      List<String> allowedOrigins,
      boolean sessionCookieSecure) {
    if (!activeProfiles.contains(PROD_PROFILE)) {
      return;
    }

    if (FinancialsSecurityDefaults.LOCAL_USERNAME.equals(username)
        || FinancialsSecurityDefaults.LOCAL_PASSWORD.equals(password)) {
      throw new IllegalStateException(
          "FINANCIALS_API_USERNAME and FINANCIALS_API_PASSWORD must override local defaults when the prod profile is active");
    }

    if (allowedOrigins.stream().anyMatch("*"::equals)) {
      throw new IllegalStateException(
          "FINANCIALS_ALLOWED_ORIGINS cannot include '*' when the prod profile is active");
    }

    if (!sessionCookieSecure) {
      throw new IllegalStateException(
          "FINANCIALS_SESSION_COOKIE_SECURE must be true when the prod profile is active");
    }
  }
}
