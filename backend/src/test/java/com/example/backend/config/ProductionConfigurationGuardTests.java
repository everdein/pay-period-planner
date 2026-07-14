package com.example.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionConfigurationGuardTests {

  @Test
  void ignoresNonProductionProfiles() {
    assertThatCode(
            () ->
                ProductionConfigurationGuard.validate(
                    List.of(),
                    FinancialsSecurityDefaults.LOCAL_USERNAME,
                    FinancialsSecurityDefaults.LOCAL_PASSWORD,
                    List.of("*"),
                    false))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsLocalCredentialsWhenProductionProfileIsActive() {
    assertThatThrownBy(
            () ->
                ProductionConfigurationGuard.validate(
                    List.of("prod"),
                    FinancialsSecurityDefaults.LOCAL_USERNAME,
                    FinancialsSecurityDefaults.LOCAL_PASSWORD,
                    List.of(),
                    true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must override local defaults");
  }

  @Test
  void rejectsWildcardCorsWhenProductionProfileIsActive() {
    assertThatThrownBy(
            () ->
                ProductionConfigurationGuard.validate(
                    List.of("prod"), "financial_owner", "not-local", List.of("*"), true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot include '*'");
  }

  @Test
  void allowsHardenedProductionConfiguration() {
    assertThatCode(
            () ->
                ProductionConfigurationGuard.validate(
                    List.of("prod"),
                    "financial_owner",
                    "not-local",
                    List.of("https://finance.example.com"),
                    true))
        .doesNotThrowAnyException();
  }

  @Test
  void requiresSecureSessionCookiesWhenProductionProfileIsActive() {
    assertThatThrownBy(
            () ->
                ProductionConfigurationGuard.validate(
                    List.of("prod"),
                    "financial_owner",
                    "not-local",
                    List.of("https://finance.example.com"),
                    false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SESSION_COOKIE_SECURE must be true");
  }
}
